package pe.ssiomama.backend.caja;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.inventario.Producto;
import pe.ssiomama.backend.inventario.ProductoRepository;
import pe.ssiomama.backend.inventario.UnidadDeProducto;

/**
 * El punto de venta de la barra.
 *
 * <p>Va aparte del panel por lo mismo que la puerta: esto se usa de pie, con
 * prisa, en una tablet pegajosa y con gente esperando. El panel está pensado
 * para sentarse a mirar números, y de hecho el arqueo y el histórico se miran
 * allí, en {@code /api/caja}. Aquí solo se cobra.
 *
 * <p>Los roles que entran son {@code BARRA} más dirección y gerencia, que cubren
 * la barra cuando hace falta.
 */
@RestController
@RequestMapping("/api/barra")
public class BarraController {

    private final Barra barra;
    private final ProductoRepository productos;

    public BarraController(Barra barra, ProductoRepository productos) {
        this.barra = barra;
        this.productos = productos;
    }

    // ── Lo que entra ──────────────────────────────────────────────

    public record Apertura(
            @Min(value = 0, message = "El fondo no puede ser negativo")
            @Max(value = 10_000_000, message = "Ese fondo no parece real")
            int fondoCentimos) {}

    public record Cierre(
            @NotBlank(message = "Falta la caja") String arqueoId,
            @Min(value = 0, message = "Lo contado no puede ser negativo")
            @Max(value = 100_000_000, message = "Esa cantidad no parece real")
            int contadoCentimos) {}

    public record LineaACobrar(
            @NotBlank(message = "Falta el producto") String productoId,
            @Min(value = 1, message = "Las cantidades son de una unidad o más")
            @Max(value = 500, message = "Esa cantidad no parece real")
            int cantidad) {}

    public record Cobro(
            @NotBlank(message = "Falta la caja") String arqueoId,
            @NotNull(message = "Falta el método de pago") MetodoDePago metodoDePago,
            /** La noche que se celebra, si la hay. Puede venir vacío. */
            String eventoSlug,
            @NotEmpty(message = "La venta está vacía") @Valid List<LineaACobrar> lineas) {}

    // ── Lo que sale ───────────────────────────────────────────────

    /** Un producto tal y como se pinta en la rejilla de la barra. */
    public record BotonDeProducto(
            String id,
            String nombre,
            String sku,
            UnidadDeProducto unidad,
            int precioCentimos,
            /**
             * Puede ser negativo. No se oculta: es la señal de que hay que
             * arreglar el inventario, y esconderla no arregla nada.
             */
            int stock) {

        static BotonDeProducto de(Producto p) {
            return new BotonDeProducto(
                    p.getId(), p.getNombre(), p.getSku(), p.getUnidad(), p.getPrecioCentimos(),
                    p.getStock());
        }
    }

    /** El catálogo agrupado por categoría, en orden fijo. */
    public record GrupoDeCatalogo(String categoria, List<BotonDeProducto> productos) {}

    /**
     * El turno abierto.
     *
     * @param esperadoCentimos lo que debería haber en el cajón: el fondo más lo
     *     cobrado en efectivo. No incluye tarjeta ni Yape, que van al banco.
     */
    public record TurnoAbierto(
            String id,
            String sala,
            String responsable,
            int fondoCentimos,
            int esperadoCentimos,
            Instant abiertaEn) {

        static TurnoAbierto de(ArqueoDeCaja a) {
            return new TurnoAbierto(
                    a.getId(),
                    a.getSala().getNombre(),
                    a.getResponsable().getNombre(),
                    a.getAperturaCentimos(),
                    a.getEsperadoCentimos(),
                    a.getAbiertaEn());
        }
    }

    /**
     * La pantalla entera de la barra.
     *
     * @param turno nulo si esta persona no tiene caja abierta: la pantalla
     *     enseña entonces el formulario de apertura
     * @param noche la que se celebra ahora, para colgarle las ventas
     */
    public record PantallaDeBarra(
            TurnoAbierto turno,
            String noche,
            String nocheSlug,
            List<GrupoDeCatalogo> catalogo) {}

    /** El resultado de cerrar: lo que se firma. */
    public record TurnoCerrado(
            String id,
            int fondoCentimos,
            int esperadoCentimos,
            int contadoCentimos,
            int diferenciaCentimos,
            boolean cuadro,
            Instant cerradaEn) {

        static TurnoCerrado de(ArqueoDeCaja a) {
            return new TurnoCerrado(
                    a.getId(),
                    a.getAperturaCentimos(),
                    a.getEsperadoCentimos(),
                    a.getContadoCentimos(),
                    a.getDiferenciaCentimos(),
                    a.cuadro(),
                    a.getCerradaEn());
        }
    }

    // ── Endpoints ─────────────────────────────────────────────────

    /**
     * Lo que hace falta para empezar a cobrar: el turno, la noche y el catálogo.
     *
     * <p>Va todo en una respuesta y no en tres porque la barra se abre una vez
     * por noche y se usa durante horas: tres peticiones al arrancar es
     * exactamente el momento en que menos importa ahorrarlas, y una sola
     * respuesta hace imposible pintar media pantalla.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public PantallaDeBarra pantalla(@AuthenticationPrincipal Usuario quien) {
        var noche = barra.nocheEnCurso(Instant.now());

        return new PantallaDeBarra(
                barra.turnoAbiertoDe(quien).map(TurnoAbierto::de).orElse(null),
                noche == null ? null : noche.getTitulo(),
                noche == null ? null : noche.getSlug(),
                catalogo());
    }

    /**
     * Abre el turno, o devuelve el que ya estaba: volver a entrar es continuar.
     *
     * <h2>Por qué este método es transaccional si el servicio ya lo es</h2>
     *
     * <p>Porque el DTO se arma <b>aquí</b>, y armarlo toca la sala y la persona
     * responsable, que son relaciones perezosas. Con {@code open-in-view}
     * desactivado —y lo está a propósito— fuera de la transacción no hay sesión
     * de Hibernate y esas lecturas revientan.
     *
     * <p>Y revientan aunque la entidad se acabe de construir con objetos reales:
     * {@code save()} sobre una entidad con id asignado a mano no hace
     * {@code persist} sino {@code merge}, y merge <b>devuelve una copia
     * distinta</b> cuyas relaciones son proxies sin cargar. Costó un 500 en el
     * test averiguarlo.
     */
    @PostMapping("/turno")
    @Transactional
    public ResponseEntity<TurnoAbierto> abrir(
            @Valid @RequestBody Apertura apertura, @AuthenticationPrincipal Usuario quien) {

        var arqueo = barra.abrirTurno(quien, apertura.fondoCentimos());
        return ResponseEntity.status(HttpStatus.CREATED).body(TurnoAbierto.de(arqueo));
    }

    /** Cobra. Descuenta stock, deja rastro y suma al cajón si fue en efectivo. */
    @PostMapping("/ventas")
    public ResponseEntity<Barra.VentaHecha> cobrar(
            @Valid @RequestBody Cobro cobro, @AuthenticationPrincipal Usuario quien) {

        var hecha = barra.cobrar(
                quien,
                cobro.arqueoId(),
                cobro.metodoDePago(),
                cobro.eventoSlug(),
                cobro.lineas().stream()
                        .map(l -> new Barra.LineaPedida(l.productoId(), l.cantidad()))
                        .toList());

        return ResponseEntity.status(HttpStatus.CREATED).body(hecha);
    }

    /** Cierra el turno con lo contado en el cajón. */
    @PostMapping("/cierre")
    @Transactional
    public TurnoCerrado cerrar(
            @Valid @RequestBody Cierre cierre, @AuthenticationPrincipal Usuario quien) {
        return TurnoCerrado.de(
                barra.cerrarTurno(quien, cierre.arqueoId(), cierre.contadoCentimos()));
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    /**
     * El catálogo agrupado por categoría y ordenado alfabéticamente.
     *
     * <p>El orden fijo no es estética: si las pestañas se reordenaran solas —por
     * ventas, por ejemplo—, el de barra tocaría el botón equivocado a la tercera
     * cerveza de la noche, porque para entonces ya no mira, solo pulsa donde
     * estaba.
     */
    private List<GrupoDeCatalogo> catalogo() {
        Map<String, List<Producto>> porCategoria = productos.buscarConStock().stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCategoria().getNombre(),
                        java.util.TreeMap::new,
                        Collectors.toList()));

        return porCategoria.entrySet().stream()
                .map(e -> new GrupoDeCatalogo(
                        e.getKey(),
                        e.getValue().stream()
                                .sorted(java.util.Comparator.comparing(Producto::getNombre))
                                .map(BotonDeProducto::de)
                                .toList()))
                .toList();
    }
}
