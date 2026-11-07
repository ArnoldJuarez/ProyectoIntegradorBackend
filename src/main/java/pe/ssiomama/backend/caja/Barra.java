package pe.ssiomama.backend.caja;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.auth.UsuarioRepository;
import pe.ssiomama.backend.eventos.EstadoDeEvento;
import pe.ssiomama.backend.eventos.Evento;
import pe.ssiomama.backend.eventos.EventoRepository;
import pe.ssiomama.backend.eventos.Sala;
import pe.ssiomama.backend.eventos.SalaRepository;
import pe.ssiomama.backend.inventario.MovimientoDeInventario;
import pe.ssiomama.backend.inventario.MovimientoRepository;
import pe.ssiomama.backend.inventario.Producto;
import pe.ssiomama.backend.inventario.ProductoRepository;
import pe.ssiomama.backend.personal.Puesto;
import pe.ssiomama.backend.personal.PuestoRepository;

/**
 * La caja de la barra: abrir turno, cobrar y cerrar contando.
 *
 * <h2>Un turno de caja es de una persona</h2>
 *
 * <p>Un arqueo es el turno de alguien concreto en una barra concreta: se abre
 * con el fondo que hay en el cajón, se le van colgando las ventas y se cierra
 * contando el efectivo. <b>Sin turno abierto no se cobra</b>, y eso es a
 * propósito: una venta que no cuelga de nadie no se le puede reclamar a nadie
 * cuando el cajón no cuadra.
 *
 * <h2>Una venta toca cuatro cosas a la vez</h2>
 *
 * <p>La venta con sus líneas, el stock de cada producto, el movimiento que deja
 * rastro de por qué bajó, y el efectivo esperado en el cajón. Va todo en una
 * transacción porque si algo de eso falla a medias el resultado es un inventario
 * que miente — y un inventario que miente es peor que no tenerlo, porque se pide
 * mercancía contra él.
 */
@Service
public class Barra {

    /** El resultado de cobrar. */
    public record VentaHecha(Long id, int totalCentimos, int unidades, int esperadoEnCajon) {}

    /** Lo que se pide cobrar: qué producto y cuántos. */
    public record LineaPedida(Long productoId, int cantidad) {}

    private final ArqueoRepository arqueos;
    private final VentaDeBarraRepository ventas;
    private final ProductoRepository productos;
    private final MovimientoRepository movimientos;
    private final SalaRepository salas;
    private final EventoRepository eventos;
    private final PuestoRepository puestos;
    private final UsuarioRepository usuarios;

    public Barra(
            ArqueoRepository arqueos,
            VentaDeBarraRepository ventas,
            ProductoRepository productos,
            MovimientoRepository movimientos,
            SalaRepository salas,
            EventoRepository eventos,
            PuestoRepository puestos,
            UsuarioRepository usuarios) {
        this.puestos = puestos;
        this.usuarios = usuarios;
        this.arqueos = arqueos;
        this.ventas = ventas;
        this.productos = productos;
        this.movimientos = movimientos;
        this.salas = salas;
        this.eventos = eventos;
    }

    // ── El turno ──────────────────────────────────────────────────

    /**
     * Abre el turno, o devuelve el que ya estaba abierto.
     *
     * <p>Volver a entrar es <b>continuar</b>, no empezar de nuevo. Sin esta
     * regla, cerrar la pestaña y volver abría una segunda caja, las ventas se
     * repartían entre las dos y al cuadrar no salía nada.
     *
     * @param fondoCentimos lo que hay en el cajón al empezar
     */
    @Transactional
    public ArqueoDeCaja abrirTurno(Usuario responsable, int fondoCentimos) {
        return abrirTurno(responsable, fondoCentimos, null);
    }

    /**
     * Abre el turno en una barra concreta.
     *
     * @param barraId la barra elegida en la pantalla. Nula: la del puesto de
     *     quien abre, o la única que haya. Si hay varias y no se sabe cuál, se
     *     pregunta en vez de adivinar: un turno en la barra equivocada reparte
     *     mal las ventas de toda la noche.
     */
    @Transactional
    public ArqueoDeCaja abrirTurno(Usuario responsable, int fondoCentimos, Long barraId) {
        if (fondoCentimos < 0) {
            throw new CajaRechazada("El fondo de caja no puede ser negativo.");
        }

        return arqueos.buscarAbiertoDe(responsable.getId())
                .orElseGet(() -> {
                    Sala sala = salas.findAllByOrderByAforoDesc().stream()
                            .findFirst()
                            .orElseThrow(() -> new CajaRechazada(
                                    "No hay ninguna sala dada de alta para abrir caja."));
                    return arqueos.save(ArqueoDeCaja.abrir(sala, barraPara(responsable, barraId), responsable, fondoCentimos));
                });
    }

    /** Las barras donde se puede abrir turno hoy. */
    @Transactional(readOnly = true)
    public List<Puesto> barrasActivas() {
        return puestos.findByEsDeBarraTrueOrderByActivoDescNombreAsc().stream().filter(Puesto::isActivo).toList();
    }

    private Puesto barraPara(Usuario responsable, Long barraId) {
        if (barraId != null) {
            return puestos.findById(barraId)
                    .filter(Puesto::isEsDeBarra)
                    .filter(Puesto::isActivo)
                    .orElseThrow(() -> new CajaRechazada("Esa barra no existe o ya no se monta."));
        }
        // Se relee: quien llega de la sesión viene sin su puesto cargado, y
        // pedírselo fuera de su transacción revienta.
        Puesto suyo = usuarios.findById(responsable.getId()).map(Usuario::getPuesto).orElse(null);
        if (suyo != null && suyo.isEsDeBarra() && suyo.isActivo()) {
            return suyo;
        }
        List<Puesto> activas = barrasActivas();
        if (activas.size() > 1) {
            throw new CajaRechazada("Elige en qué barra vas a cobrar.");
        }
        // Sin ninguna barra dada de alta el turno se abre igual: sin barra, sus
        // ventas salen como «Sin barra asignada», pero se puede cobrar.
        return activas.isEmpty() ? null : activas.getFirst();
    }

    /**
     * Cierra el turno con lo contado en el cajón.
     *
     * <p>Se bloquea la fila: una venta que llegue justo ahora espera y se
     * encuentra la caja cerrada, en vez de colarse después del recuento y dejar
     * un descuadre inventado.
     */
    @Transactional
    public ArqueoDeCaja cerrarTurno(Usuario quien, Long arqueoId, int contadoCentimos) {
        if (contadoCentimos < 0) {
            throw new CajaRechazada("Lo contado no puede ser negativo.");
        }

        ArqueoDeCaja arqueo = arqueos.bloquear(arqueoId)
                .orElseThrow(() -> new CajaRechazada("Esa caja no existe."));

        if (!arqueo.estaAbierta()) {
            throw new CajaRechazada("Esa caja ya estaba cerrada.");
        }
        // Cada uno cierra la suya. Que gerencia pueda cerrar la de otro es una
        // decisión que habría que tomar a propósito, no heredarla de un olvido.
        if (!arqueo.getResponsable().getId().equals(quien.getId())) {
            throw new CajaRechazada("Esa caja es de otra persona.");
        }

        arqueo.cerrar(contadoCentimos, Instant.now());
        return arqueo;
    }

    // ── Cobrar ────────────────────────────────────────────────────

    /**
     * Registra una venta y descuenta el stock.
     *
     * @param eventoSlug la noche que se está celebrando, o {@code null}. Sirve
     *     para saber cuánto factura la barra en cada evento.
     */
    @Transactional
    public VentaHecha cobrar(
            Usuario quien,
            Long arqueoId,
            MetodoDePago metodo,
            String eventoSlug,
            List<LineaPedida> pedidas) {

        if (pedidas == null || pedidas.isEmpty()) {
            throw new CajaRechazada("La venta está vacía.");
        }

        // Bloqueado porque cobrar suma al efectivo esperado, y sumar es leer y
        // escribir: dos cobros a la vez se pisarían el incremento.
        ArqueoDeCaja arqueo = arqueos.bloquear(arqueoId)
                .orElseThrow(() -> new CajaRechazada("Esa caja no existe."));

        if (!arqueo.estaAbierta()) {
            throw new CajaRechazada("La caja está cerrada. Ábrela otra vez.");
        }
        if (!arqueo.getResponsable().getId().equals(quien.getId())) {
            throw new CajaRechazada("Esa caja es de otra persona.");
        }

        Map<Long, Integer> porProducto = juntarRepetidas(pedidas);
        Map<Long, Producto> catalogo = cargar(porProducto.keySet().stream().toList());

        Evento noche = eventoSlug == null || eventoSlug.isBlank()
                ? null
                : eventos.findBySlug(eventoSlug).orElse(null);

        VentaDeBarra venta = VentaDeBarra.registrar(arqueo, noche, metodo);

        int unidades = 0;
        for (var entrada : porProducto.entrySet()) {
            Producto producto = catalogo.get(entrada.getKey());
            int cantidad = entrada.getValue();

            venta.anadirLinea(LineaDeVenta.de(venta, producto, cantidad));
            unidades += cantidad;
        }

        ventas.save(venta);

        // Los movimientos se crean después de guardar la venta porque su
        // referencia lleva el número de la venta, y ese número lo pone la base
        // al insertarla.
        for (var entrada : porProducto.entrySet()) {
            Producto producto = catalogo.get(entrada.getKey());
            int cantidad = entrada.getValue();

            producto.descontarStock(cantidad);
            movimientos.save(MovimientoDeInventario.porVentaDeBarra(
                    producto, arqueo.getSala(), cantidad, "Venta " + venta.getId(), quien));
        }

        // Al cajón solo entra el efectivo. Lo demás va al banco.
        if (venta.esEfectivo()) {
            arqueo.sumarEfectivo(venta.getTotalCentimos());
        }

        return new VentaHecha(
                venta.getId(), venta.getTotalCentimos(), unidades, arqueo.getEsperadoCentimos());
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    /**
     * Junta las líneas repetidas del mismo producto.
     *
     * <p>En una barra se toca el mismo botón cinco veces seguidas. Si llegaran
     * cinco líneas del mismo producto se harían cinco descuentos de stock
     * separados; sumadas, es un único movimiento por producto — el inventario
     * queda legible y el recuento sale más barato.
     *
     * <p>{@link LinkedHashMap} para conservar el orden en que se pulsaron: el
     * ticket se lee mejor si las líneas salen como se cantaron.
     */
    private Map<Long, Integer> juntarRepetidas(List<LineaPedida> pedidas) {
        Map<Long, Integer> juntas = new LinkedHashMap<>();

        for (LineaPedida l : pedidas) {
            if (l.cantidad() <= 0) {
                throw new CajaRechazada("Las cantidades tienen que ser de una unidad o más.");
            }
            juntas.merge(l.productoId(), l.cantidad(), Integer::sum);
        }
        return juntas;
    }

    /** Los productos pedidos. Si falta alguno, la petición nombra algo que no existe. */
    private Map<Long, Producto> cargar(List<Long> ids) {
        List<Producto> encontrados = productos.findAllById(ids);

        if (encontrados.size() != ids.size()) {
            throw new CajaRechazada("Algún producto ya no está en el catálogo.");
        }
        return encontrados.stream().collect(Collectors.toMap(Producto::getId, Function.identity()));
    }

    /** El turno que esta persona tiene abierto, si tiene alguno. */
    @Transactional(readOnly = true)
    public java.util.Optional<ArqueoDeCaja> turnoAbiertoDe(Usuario quien) {
        return arqueos.buscarAbiertoDe(quien.getId());
    }

    /** La noche que se está celebrando ahora, para colgarle las ventas. */
    @Transactional(readOnly = true)
    public Evento nocheEnCurso(Instant ahora) {
        return eventos.buscarNocheAbierta(
                        EstadoDeEvento.PUBLICADO, ahora.plusSeconds(6 * 3600), ahora)
                .orElse(null);
    }
}
