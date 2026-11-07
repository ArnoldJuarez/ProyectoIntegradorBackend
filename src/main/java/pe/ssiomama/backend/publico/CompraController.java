package pe.ssiomama.backend.publico;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.ssiomama.backend.caja.MetodoDePago;
import pe.ssiomama.backend.entradas.Compra;
import pe.ssiomama.backend.entradas.Entrada;
import pe.ssiomama.backend.entradas.EntradaRepository;
import pe.ssiomama.backend.entradas.EstadoDeEntrada;
import pe.ssiomama.backend.entradas.Pedido;
import pe.ssiomama.backend.entradas.PedidoRepository;
import pe.ssiomama.backend.entradas.Reintentos;

/**
 * Comprar entradas desde la web, sin cuenta.
 *
 * <h2>Por qué está en /api/publico y no en /api/pedidos</h2>
 *
 * <p>Porque {@code /api/pedidos} es del panel y solo lo ve dirección: enseña el
 * correo del comprador, su teléfono y el método de pago. Esto lo abre cualquiera
 * desde el móvil. Son los mismos pedidos vistos desde dos sitios con permisos
 * opuestos, y por eso llevan <b>DTO distintos</b>: si compartieran uno, añadir
 * un campo interno al panel lo publicaría en internet sin que nadie lo notara.
 *
 * <h2>El código del pedido es la llave</h2>
 *
 * <p>{@code GET /api/publico/pedidos/DSC-XXXXXX} enseña las entradas con su QR
 * sin pedir sesión, porque quien compró no tiene cuenta: lo único que tiene es
 * el enlace. Eso convierte el código en una credencial, aunque sea débil, y de
 * ahí dos decisiones:
 *
 * <ul>
 *   <li>Se genera con {@code SecureRandom} y sin caracteres ambiguos.
 *   <li>La respuesta trae <b>solo</b> lo que hace falta para entrar: ni correo,
 *       ni teléfono, ni cuánto pagó. Quien acierte un código ajeno no se lleva
 *       los datos personales de nadie.
 * </ul>
 */
@RestController
@RequestMapping("/api/publico/pedidos")
public class CompraController {

    private final Compra compra;
    private final Reintentos reintentos;
    private final PedidoRepository pedidos;
    private final EntradaRepository entradas;

    public CompraController(
            Compra compra,
            Reintentos reintentos,
            PedidoRepository pedidos,
            EntradaRepository entradas) {
        this.compra = compra;
        this.reintentos = reintentos;
        this.pedidos = pedidos;
        this.entradas = entradas;
    }

    // ── Lo que entra ──────────────────────────────────────────────

    public record LineaPedida(
            @NotNull(message = "Falta el tipo de entrada") Long tipoDeEntradaId,
            @Min(value = 1, message = "Las cantidades son de una entrada o más")
            @Max(value = 10, message = "Máximo 10 entradas del mismo tipo")
            int cantidad) {}

    /**
     * Lo que manda el navegador.
     *
     * <p>Fíjate en lo que <b>no</b> lleva: ni precios, ni subtotal, ni total.
     * Solo qué se quiere y cuánto. Los importes los pone el servidor leyendo la
     * base, porque un total que viene del cliente es un total que se puede
     * editar con las herramientas del navegador.
     */
    public record Solicitud(
            @NotBlank(message = "Falta la noche") String slug,
            @NotBlank(message = "Falta el nombre")
            @Size(max = 80, message = "Ese nombre es demasiado largo") String nombre,
            @Size(max = 80, message = "Ese apellido es demasiado largo") String apellido,
            @NotBlank(message = "Falta el correo")
            @Email(message = "El correo no es válido")
            @Size(max = 160) String email,
            @Size(max = 30, message = "Ese teléfono no parece correcto") String telefono,
            @NotEmpty(message = "Elige al menos una entrada")
            @Valid List<LineaPedida> lineas,
            /**
             * El código del promotor del enlace ({@code ?promo=}). Opcional, y
             * si no existe se ignora: ver {@code Compra.abrir}.
             */
            @Size(max = 20) String promo) {}

    // ── Lo que sale ───────────────────────────────────────────────

    /** El resguardo de un pedido recién abierto. */
    public record Resguardo(
            String codigo,
            int subtotalCentimos,
            int comisionCentimos,
            int totalCentimos,
            int entradas,
            String mensaje) {}

    public record PagoHecho(String codigo, int entradas, boolean yaEstabaPagado, String mensaje) {}

    /**
     * Una entrada tal y como la ve su dueño.
     *
     * <p>Aquí sí va el {@code codigo} completo, firma incluida: es el QR que se
     * enseña en la puerta. Es justo lo contrario que en el panel, donde solo
     * salen los primeros caracteres — una lista de códigos completos es una
     * lista de entradas válidas, y el panel la enseña a quien no es su dueño.
     */
    public record EntradaConQr(
            String codigo,
            String tipoDeEntrada,
            String titular,
            int consumicionesIncluidas,
            boolean yaSeUso) {

        static EntradaConQr de(Entrada e) {
            return new EntradaConQr(
                    e.getCodigo(),
                    e.getTipoDeEntrada().getNombre(),
                    e.getTitular(),
                    e.getTipoDeEntrada().getConsumicionesIncluidas(),
                    e.yaSeUso());
        }
    }

    /**
     * La página de «tus entradas».
     *
     * @param pagado si no, la página enseña el botón de pagar en vez de los QR
     */
    public record MisEntradas(
            String codigo,
            boolean pagado,
            String noche,
            String slug,
            String sala,
            Instant puertasA,
            Instant empiezaA,
            int totalCentimos,
            List<EntradaConQr> entradas) {}

    // ── Endpoints ─────────────────────────────────────────────────

    /**
     * Abre un pedido y aparta el cupo.
     *
     * <p>Responde 201 con el código: es lo que el navegador usa para llevar a la
     * persona a la página de pago. Si algo no cuadra —se agotaron mientras
     * rellenaba— sale un 409 con el motivo escrito para leerlo.
     */
    @PostMapping
    public ResponseEntity<Resguardo> comprar(@Valid @RequestBody Solicitud solicitud) {
        var resultado = reintentos.conReintento(() -> compra.abrir(
                solicitud.slug(),
                new Compra.Comprador(
                        solicitud.nombre(),
                        solicitud.apellido(),
                        solicitud.email(),
                        solicitud.telefono()),
                solicitud.lineas().stream()
                        .map(l -> new Compra.Peticion(l.tipoDeEntradaId(), l.cantidad()))
                        .toList(),
                solicitud.promo(),
                Instant.now()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new Resguardo(
                        resultado.codigo(),
                        resultado.subtotalCentimos(),
                        resultado.comisionCentimos(),
                        resultado.totalCentimos(),
                        resultado.entradas(),
                        "Pedido reservado. Te quedan unos minutos para pagarlo."));
    }

    /**
     * Confirma el pago y emite las entradas.
     *
     * <p>El cobro es <b>simulado</b>: no hay pasarela conectada, así que este
     * endpoint da por bueno el pago sin comprobar nada. Está señalado aquí y en
     * {@code Compra} porque es lo primero que hay que cambiar antes de que esto
     * cobre dinero de verdad.
     */
    @PostMapping("/{codigo}/pagar")
    public PagoHecho pagar(@PathVariable String codigo) {
        var hecho = reintentos.conReintento(
                () -> compra.confirmarPago(codigo.trim(), MetodoDePago.TARJETA, Instant.now()));

        return new PagoHecho(
                hecho.codigo(),
                hecho.entradasEmitidas(),
                hecho.yaEstabaPagado(),
                hecho.yaEstabaPagado()
                        ? "Este pedido ya estaba pagado."
                        : "Pago confirmado. Aquí tienes tus entradas.");
    }

    /** Las entradas de un pedido. Sin sesión: la llave es el código. */
    @GetMapping("/{codigo}")
    @Transactional(readOnly = true)
    public MisEntradas ver(@PathVariable String codigo) {
        Pedido pedido = pedidos.buscarPorCodigoConLineas(codigo.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No encontramos ese pedido."));

        // Las entradas solo existen si se pagó. Un pendiente devuelve la lista
        // vacía y la página enseña el botón de pagar.
        List<EntradaConQr> conQr = pedido.estaPagado()
                ? entradas.buscarPorCodigoDePedido(pedido.getCodigo()).stream()
                        .filter(e -> e.getEstado() != EstadoDeEntrada.ANULADA)
                        .map(EntradaConQr::de)
                        .toList()
                : List.of();

        var noche = pedido.getEvento();
        return new MisEntradas(
                pedido.getCodigo(),
                pedido.estaPagado(),
                noche.getTitulo(),
                noche.getSlug(),
                noche.getSala().getNombre(),
                noche.getPuertasA(),
                noche.getEmpiezaA(),
                pedido.getTotalCentimos(),
                conQr);
    }
}
