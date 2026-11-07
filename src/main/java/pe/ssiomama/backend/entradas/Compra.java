package pe.ssiomama.backend.entradas;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.auth.UsuarioRepository;
import pe.ssiomama.backend.caja.MetodoDePago;
import pe.ssiomama.backend.clientes.CanalDeInteraccion;
import pe.ssiomama.backend.clientes.Cliente;
import pe.ssiomama.backend.clientes.ClienteRepository;
import pe.ssiomama.backend.clientes.FidelidadRepository;
import pe.ssiomama.backend.clientes.Interaccion;
import pe.ssiomama.backend.clientes.InteraccionRepository;
import pe.ssiomama.backend.clientes.MovimientoDeFidelidad;
import pe.ssiomama.backend.clientes.TipoDeInteraccion;
import pe.ssiomama.backend.eventos.EstadoDeEvento;
import pe.ssiomama.backend.eventos.Evento;
import pe.ssiomama.backend.eventos.EventoRepository;
import pe.ssiomama.backend.eventos.TipoDeEntrada;
import pe.ssiomama.backend.eventos.TipoDeEntradaRepository;

/**
 * Comprar entradas: abrir el pedido y confirmar el pago.
 *
 * <h2>Las dos operaciones bloquean una fila</h2>
 *
 * <p>Abrir un pedido lee cuántas entradas quedan y luego las descuenta. Sin
 * protección, dos compras simultáneas leen las dos que queda una, las dos
 * deciden que se puede, y las dos escriben: se vende dos veces la última entrada
 * y nadie se entera hasta la puerta, un viernes, con dos personas y un solo
 * sitio. Confirmar el pago tiene el mismo problema con otra forma: dos
 * pulsaciones del botón emiten el juego de entradas dos veces.
 *
 * <p>Las dos se resuelven bloqueando la fila que se va a modificar —el tipo de
 * entrada en un caso, el pedido en el otro— con {@code select ... for update}.
 * Quien llega segundo espera unos milisegundos y luego lee el número ya
 * actualizado.
 *
 * <p>El primer intento fue con aislamiento {@code SERIALIZABLE}, que es la otra
 * forma de resolverlo: dejar correr a todos y abortar a quien se pise. Funciona,
 * pero al medirlo con veinte compras simultáneas sobre la misma fila, cinco
 * acababan viendo un error del servidor <b>habiendo entradas libres</b>: los
 * reintentos volvían a chocar entre ellos. El detalle está en
 * {@code TipoDeEntradaRepository}.
 *
 * <p>Tampoco basta una restricción de la base, como sí ocurría en la puerta:
 * allí {@code entradaId} único bloqueaba el segundo escaneo, pero aquí no hay
 * ninguna regla que diga «vendidas nunca supera cantidad». Se podría añadir un
 * {@code CHECK}, y estaría bien tenerlo como red, pero daría un error de
 * restricción en vez de un «solo quedan 2», que es lo que hay que decirle a
 * quien está comprando.
 *
 * <h2>Lo que no llega desde el navegador</h2>
 *
 * <p>Ni el precio, ni el subtotal, ni la comisión, ni el total. Llega qué se
 * quiere y cuánto; todo lo demás se lee de la base y se calcula aquí. Es la
 * regla que evita que alguien compre una VIP por un sol editando la petición.
 */
@Service
public class Compra {

    /** El resultado de abrir un pedido: lo que se le enseña al comprador. */
    public record PedidoAbierto(String codigo, int subtotalCentimos, int comisionCentimos,
            int totalCentimos, int entradas) {}

    /**
     * El resultado de confirmar.
     *
     * @param yaEstabaPagado cierto si no había nada que hacer. Se distingue
     *     porque volver a la página de pago y darle otra vez no es un error, y
     *     tratar el segundo intento como fallo asusta a quien acaba de pagar.
     */
    public record PagoConfirmado(String codigo, int entradasEmitidas, boolean yaEstabaPagado) {}

    private final EventoRepository eventos;
    private final TipoDeEntradaRepository tipos;
    private final ClienteRepository clientes;
    private final PedidoRepository pedidos;
    private final EntradaRepository entradas;
    private final FidelidadRepository fidelidad;
    private final InteraccionRepository interacciones;
    private final FirmaDeEntrada firma;
    private final UsuarioRepository usuarios;

    public Compra(
            EventoRepository eventos,
            TipoDeEntradaRepository tipos,
            ClienteRepository clientes,
            PedidoRepository pedidos,
            EntradaRepository entradas,
            FidelidadRepository fidelidad,
            InteraccionRepository interacciones,
            FirmaDeEntrada firma,
            UsuarioRepository usuarios) {
        this.usuarios = usuarios;
        this.eventos = eventos;
        this.tipos = tipos;
        this.clientes = clientes;
        this.pedidos = pedidos;
        this.entradas = entradas;
        this.fidelidad = fidelidad;
        this.interacciones = interacciones;
        this.firma = firma;
    }

    // ── Lo que pide quien compra ──────────────────────────────────

    /** Una línea de la petición: qué tipo y cuántas. */
    public record Peticion(Long tipoDeEntradaId, int cantidad) {}

    /** Los datos de contacto de quien compra. */
    public record Comprador(String nombre, String apellido, String email, String telefono) {}

    // ── Abrir el pedido ───────────────────────────────────────────

    /**
     * Crea un pedido pendiente y aparta el cupo.
     *
     * <p>El cupo se reserva antes de cobrar. Si se esperara al pago, dos
     * personas podrían llegar a la pasarela con la última entrada y una pagaría
     * algo que no existe. El precio es que un pendiente que nadie paga ocupa
     * sitio hasta que se cancele.
     *
     * @throws CompraRechazada con un motivo que se le puede enseñar al comprador
     */
    @Transactional
    public PedidoAbierto abrir(
            String slugDelEvento, Comprador comprador, List<Peticion> peticiones, Instant ahora) {
        return abrir(slugDelEvento, comprador, peticiones, null, ahora);
    }

    /**
     * Igual, con el código del promotor que trajo a quien compra.
     *
     * <p>Un código que no existe, o de alguien dado de baja, <b>no bloquea la
     * compra</b>: se ignora y el pedido queda como venta directa. Quien compra
     * no tiene la culpa de que el enlace que le pasaron esté viejo, y perder
     * una venta por eso sería absurdo.
     *
     * @param codigoDePromotor el de {@code ?promo=} en el enlace, o nulo
     */
    @Transactional
    public PedidoAbierto abrir(
            String slugDelEvento, Comprador comprador, List<Peticion> peticiones, String codigoDePromotor,
            Instant ahora) {

        if (peticiones == null || peticiones.isEmpty()) {
            throw new CompraRechazada("Elige al menos una entrada.");
        }

        // Sin join fetch de los tipos: cargarlos aquí dejaría instancias viejas
        // en el contexto, y la consulta que los bloquea devolvería esas mismas.
        // Ver la nota en EventoRepository.findBySlug.
        Evento evento = eventos.findBySlug(slugDelEvento)
                .orElseThrow(() -> new CompraRechazada("Esa fecha no existe."));

        if (evento.getEstado() != EstadoDeEvento.PUBLICADO) {
            throw new CompraRechazada("Esta fecha no está a la venta.");
        }

        Map<Long, TipoDeEntrada> porId = cargarTiposDelEvento(evento, peticiones);

        // Primero se comprueba todo y luego se toca algo. Reservar sobre la
        // marcha dejaría cupo apartado de las líneas buenas cuando falla la
        // tercera, y aunque la transacción lo deshace, la lógica se lee peor.
        int subtotal = 0;
        for (Peticion p : peticiones) {
            TipoDeEntrada tipo = porId.get(p.tipoDeEntradaId());
            comprobar(tipo, p.cantidad(), ahora);
            subtotal += tipo.getPrecioCentimos() * p.cantidad();
        }

        Cliente cliente = clienteDe(comprador);
        int comision = Tarifas.comisionDeGestion(subtotal);

        Pedido pedido = Pedido.abrir(
                Tarifas.nuevoCodigoDePedido(), cliente, evento, promotorDe(codigoDePromotor), comision, ahora);

        for (Peticion p : peticiones) {
            TipoDeEntrada tipo = porId.get(p.tipoDeEntradaId());
            tipo.reservarCupo(p.cantidad());
            pedido.anadirLinea(LineaDePedido.de(pedido, tipo, p.cantidad()), comision);
        }

        pedidos.save(pedido);

        return new PedidoAbierto(
                pedido.getCodigo(),
                pedido.getSubtotalCentimos(),
                pedido.getComisionCentimos(),
                pedido.getTotalCentimos(),
                pedido.totalDeEntradas());
    }

    // ── Confirmar el pago ─────────────────────────────────────────

    /**
     * Da el pedido por pagado y emite sus entradas con su QR.
     *
     * <p><b>El cobro es simulado</b>: no hay pasarela conectada. Cuando la haya,
     * lo único que cambia es que antes de esta transacción se verifica el pago
     * de verdad y la referencia deja de empezar por {@code SIMULADO-}. El resto
     * —emitir, sumar a la ficha del cliente, apuntar los puntos— es igual.
     *
     * <p>Volver a llamar sobre un pedido ya pagado no hace nada y no es un
     * error: alguien que vuelve atrás en el navegador y le da otra vez al botón
     * no debería ver un fallo.
     */
    @Transactional
    public PagoConfirmado confirmarPago(String codigo, MetodoDePago metodo, Instant ahora) {
        // Bloqueado: dos pulsaciones del boton de pagar casi a la vez leerian
        // las dos que esta pendiente y emitirian el doble de entradas.
        Pedido pedido = pedidos.bloquearParaCobrar(codigo)
                .orElseThrow(() -> new CompraRechazada("No encontramos ese pedido."));

        if (pedido.estaPagado()) {
            return new PagoConfirmado(pedido.getCodigo(), pedido.getEntradas().size(), true);
        }
        if (!pedido.admitePago()) {
            throw new CompraRechazada("Este pedido ya no se puede pagar.");
        }

        String titular = pedido.getCliente().getNombreCompleto();
        int emitidas = 0;

        for (LineaDePedido linea : pedido.getLineas()) {
            for (int i = 0; i < linea.getCantidad(); i++) {
                /*
                 * El número se reserva antes de insertar porque el código del
                 * QR es `id.firma`: hay que conocerlo para firmarlo. Insertando
                 * primero, la entrada existiría un instante sin código.
                 */
                Long id = entradas.reservarId();
                entradas.save(Entrada.emitir(
                        pedido, linea.getTipoDeEntrada(), id, firma.codificar(id), titular, ahora));
                emitidas++;
            }
        }

        pedido.marcarPagado(metodo, "SIMULADO-" + pedido.getCodigo(), ahora);

        // La ficha del cliente y el saldo de puntos van en la misma transacción
        // que la emisión: si se hicieran después, un fallo entre medias dejaría
        // entradas emitidas que no cuentan como visita ni dan puntos.
        Cliente cliente = pedido.getCliente();
        cliente.registrarCompra(pedido.getTotalCentimos(), ahora);

        fidelidad.save(MovimientoDeFidelidad.porCompra(
                cliente, pedido.getId(), pedido.getCodigo(), pedido.getTotalCentimos()));

        interacciones.save(Interaccion.registrar(
                cliente,
                TipoDeInteraccion.COMPRA,
                CanalDeInteraccion.WEB,
                "Compró %d %s (%s)."
                        .formatted(
                                emitidas,
                                emitidas == 1 ? "entrada" : "entradas",
                                pedido.getCodigo())));

        return new PagoConfirmado(pedido.getCodigo(), emitidas, false);
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    /**
     * Carga los tipos pedidos comprobando que son de este evento.
     *
     * <p>Si vuelven menos de los que se pidieron, la petición nombra algo que no
     * existe o que es de otro evento. El mensaje es deliberadamente vago: decir
     * «ese tipo es de otro evento» le confirmaría a quien lo está intentando que
     * el id existe.
     */
    private Map<Long, TipoDeEntrada> cargarTiposDelEvento(
            Evento evento, List<Peticion> peticiones) {

        List<Long> ids = peticiones.stream().map(Peticion::tipoDeEntradaId).distinct().toList();
        if (ids.size() != peticiones.size()) {
            throw new CompraRechazada("Hay un tipo de entrada repetido en el pedido.");
        }

        List<TipoDeEntrada> encontrados = tipos.bloquearParaVender(evento.getId(), ids);
        if (encontrados.size() != ids.size()) {
            throw new CompraRechazada("Alguna de las entradas ya no está disponible.");
        }

        return encontrados.stream().collect(Collectors.toMap(TipoDeEntrada::getId, Function.identity()));
    }

    /** Las reglas de una línea, en el orden en que se le explican a una persona. */
    private void comprobar(TipoDeEntrada tipo, int cantidad, Instant ahora) {
        if (cantidad <= 0) {
            throw new CompraRechazada("Las cantidades tienen que ser de una entrada o más.");
        }
        if (!tipo.estaALaVenta(ahora)) {
            throw new CompraRechazada("Las entradas %s ya no están a la venta.".formatted(tipo.getNombre()));
        }
        if (cantidad > tipo.getMaximoPorPedido()) {
            throw new CompraRechazada(
                    "Máximo %d entradas %s por pedido."
                            .formatted(tipo.getMaximoPorPedido(), tipo.getNombre()));
        }
        // El aforo va el último porque es el que más cambia entre que alguien
        // abre el formulario y le da a comprar.
        if (cantidad > tipo.getDisponibles()) {
            throw new CompraRechazada(
                    tipo.getDisponibles() <= 0
                            ? "Se han agotado las entradas %s.".formatted(tipo.getNombre())
                            : "Solo quedan %d entradas %s."
                                    .formatted(tipo.getDisponibles(), tipo.getNombre()));
        }
    }

    /**
     * El cliente que compra: el que ya estaba con ese correo, o uno nuevo.
     *
     * <p>El correo es la identidad. Se actualiza el contacto de quien vuelve
     * porque quien acaba de rellenar el formulario está dando sus datos de hoy,
     * y un teléfono viejo es peor que ninguno cuando hay que avisar de que el
     * evento se cancela.
     */
    private Cliente clienteDe(Comprador comprador) {
        String email = comprador.email().trim().toLowerCase();

        return clientes.findByEmailIgnoreCase(email)
                .map(existente -> {
                    existente.actualizarContacto(
                            comprador.nombre().trim(),
                            comprador.apellido(),
                            comprador.telefono());
                    return existente;
                })
                .orElseGet(() -> clientes.save(Cliente.nuevoConContacto(
                        comprador.nombre().trim(),
                        comprador.apellido(),
                        email,
                        comprador.telefono())));
    }

    /** El promotor activo de ese código, o nulo. */
    private Usuario promotorDe(String codigo) {
        if (codigo == null || codigo.isBlank()) return null;
        return usuarios.findByCodigoDePromotor(codigo.trim().toUpperCase(Locale.ROOT))
                .filter(Usuario::isActivo)
                .orElse(null);
    }
}
