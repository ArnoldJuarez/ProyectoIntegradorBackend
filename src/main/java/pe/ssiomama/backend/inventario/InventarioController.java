package pe.ssiomama.backend.inventario;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El almacén: qué hay, qué falta reponer, qué se ha movido y a quién se compra.
 *
 * <p>Va aparte de {@link ProductoController} porque responde otra pregunta. Ese
 * sirve el <b>catálogo</b> —qué productos existen y a qué precio se venden— y
 * este sirve las <b>existencias</b> —cuántos quedan de cada uno—. Son cosas
 * distintas: un producto puede existir en el catálogo con cero unidades.
 */
@RestController
@RequestMapping("/api/inventario")
public class InventarioController {

    /** Cuántos movimientos devuelve el listado si no se pide otra cosa. */
    private static final int MOVIMIENTOS_POR_DEFECTO = 50;

    /**
     * Tope duro. La tabla de movimientos crece con cada venta de barra, así que
     * sin límite una petición podría intentar traer una noche entera.
     */
    private static final int MOVIMIENTOS_MAXIMO = 200;

    private final ExistenciaRepository existencias;
    private final MovimientoRepository movimientos;
    private final ProveedorRepository proveedores;

    public InventarioController(
            ExistenciaRepository existencias,
            MovimientoRepository movimientos,
            ProveedorRepository proveedores) {
        this.existencias = existencias;
        this.movimientos = movimientos;
        this.proveedores = proveedores;
    }

    // ── Lo que sale por la API ────────────────────────────────────

    /** Una línea de stock: un producto en una sala. */
    public record LineaDeStock(
            String id,
            String productoId,
            String sku,
            String producto,
            String categoria,
            String sala,
            UnidadDeProducto unidad,
            int cantidad,
            int alertaDeStock,
            boolean necesitaReposicion,
            int faltanParaElUmbral,
            long valorACosteCentimos,
            Instant actualizadoEn) {

        static LineaDeStock de(Existencia e) {
            Producto p = e.getProducto();
            return new LineaDeStock(
                    e.getId(),
                    p.getId(),
                    p.getSku(),
                    p.getNombre(),
                    p.getCategoria().getNombre(),
                    e.getSala().getNombre(),
                    p.getUnidad(),
                    e.getCantidad(),
                    p.getAlertaDeStock(),
                    e.necesitaReposicion(),
                    e.faltanParaElUmbral(),
                    e.valorACosteCentimos(),
                    e.getActualizadoEn());
        }
    }

    /**
     * El resumen que va arriba de la pantalla.
     *
     * <p>Se calcula en el servidor y no en el navegador porque el día que el
     * listado vaya paginado, sumar en el cliente daría el total de la página en
     * vez del total de verdad.
     */
    public record ResumenDeAlmacen(
            int referencias, int porDebajoDelUmbral, int sinExistencias, long valorTotalCentimos) {}

    public record StockConResumen(ResumenDeAlmacen resumen, List<LineaDeStock> lineas) {}

    /** Una entrada del histórico. */
    public record LineaDeMovimiento(
            String id,
            String producto,
            String sku,
            String sala,
            TipoDeMovimiento tipo,
            String tipoEtiqueta,
            /** Con su signo: negativa cuando salió mercancía. */
            int cantidad,
            /** Sin signo, para enseñar «3 unidades» en vez de «−3». */
            int unidades,
            boolean esEntrada,
            int costeUnitarioCentimos,
            long importeCentimos,
            String referencia,
            Instant cuando) {

        static LineaDeMovimiento de(MovimientoDeInventario m) {
            return new LineaDeMovimiento(
                    m.getId(),
                    m.getProducto().getNombre(),
                    m.getProducto().getSku(),
                    m.getSala().getNombre(),
                    m.getTipo(),
                    m.getTipo().etiqueta(),
                    m.getCantidad(),
                    m.unidadesMovidas(),
                    m.esEntrada(),
                    m.getCosteUnitarioCentimos(),
                    m.importeCentimos(),
                    m.getReferencia(),
                    m.getCreadoEn());
        }
    }

    public record ProveedorEnLista(
            String id, String nombre, String personaDeContacto, String email, String telefono) {

        static ProveedorEnLista de(Proveedor p) {
            return new ProveedorEnLista(
                    p.getId(),
                    p.getNombre(),
                    p.getPersonaDeContacto(),
                    p.getEmail(),
                    p.getTelefono());
        }
    }

    // ── Endpoints ─────────────────────────────────────────────────

    /**
     * El stock con su resumen. Lo que hay que reponer sale primero.
     */
    @GetMapping("/stock")
    @Transactional(readOnly = true)
    public StockConResumen stock() {
        List<Existencia> todas = existencias.buscarTodasConDetalle();

        ResumenDeAlmacen resumen = new ResumenDeAlmacen(
                todas.size(),
                (int) todas.stream().filter(Existencia::necesitaReposicion).count(),
                (int) todas.stream().filter(e -> e.getCantidad() == 0).count(),
                todas.stream().mapToLong(Existencia::valorACosteCentimos).sum());

        return new StockConResumen(resumen, todas.stream().map(LineaDeStock::de).toList());
    }

    /**
     * El histórico de movimientos.
     *
     * @param producto si se indica, solo los de ese producto — para investigar
     *     un descuadre concreto
     */
    @GetMapping("/movimientos")
    @Transactional(readOnly = true)
    public List<LineaDeMovimiento> movimientos(
            @RequestParam(required = false) String producto,
            @RequestParam(defaultValue = "" + MOVIMIENTOS_POR_DEFECTO) int limite) {

        var pagina = PageRequest.of(0, Math.clamp(limite, 1, MOVIMIENTOS_MAXIMO));

        List<MovimientoDeInventario> encontrados = (producto == null || producto.isBlank())
                ? movimientos.buscarRecientes(pagina)
                : movimientos.buscarPorProducto(producto.trim(), pagina);

        return encontrados.stream().map(LineaDeMovimiento::de).toList();
    }

    @GetMapping("/proveedores")
    @Transactional(readOnly = true)
    public List<ProveedorEnLista> proveedores() {
        return proveedores.findAllByOrderByNombreAsc().stream()
                .map(ProveedorEnLista::de)
                .toList();
    }
}
