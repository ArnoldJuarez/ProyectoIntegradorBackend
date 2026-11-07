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
 * este sirve las <b>existencias</b> —cuántos quedan de cada uno—.
 *
 * <h2>El stock dejó de ser por sala</h2>
 *
 * <p>En el esquema anterior el saldo vivía en una tabla aparte,
 * {@code StockItem}, con una fila por producto <b>y sala</b>. Ahora es una sola
 * columna en {@code Producto}, así que hay una única cifra por producto.
 *
 * <p>Los movimientos sí siguen guardando de qué sala salieron, de modo que la
 * información no se perdió: lo que se perdió es el saldo desglosado. Mientras
 * haya un solo local da igual; con dos habría que volver a separarlo.
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

    private final ProductoRepository productos;
    private final MovimientoRepository movimientos;
    private final ProveedorRepository proveedores;

    public InventarioController(
            ProductoRepository productos,
            MovimientoRepository movimientos,
            ProveedorRepository proveedores) {
        this.productos = productos;
        this.movimientos = movimientos;
        this.proveedores = proveedores;
    }

    // ── Lo que sale por la API ────────────────────────────────────

    /** Una línea de stock: cuántas unidades quedan de un producto. */
    public record LineaDeStock(
            Long productoId,
            String sku,
            String producto,
            String categoria,
            UnidadDeProducto unidad,
            int cantidad,
            int alertaDeStock,
            boolean necesitaReposicion,
            int faltanParaElUmbral,
            long valorACosteCentimos,
            /*
             * El coste y el precio salen aquí desde que el panel edita la ficha
             * del producto: el formulario los rellena con lo que ya hay, y sin
             * ellos cada edición los pisaría. De paso, tenerlos en la tabla deja
             * ver el margen sin abrir nada.
             */
            int costeCentimos,
            int precioCentimos) {

        static LineaDeStock de(Producto p) {
            return new LineaDeStock(
                    p.getId(),
                    p.getSku(),
                    p.getNombre(),
                    p.getCategoria().getNombre(),
                    p.getUnidad(),
                    p.getStock(),
                    p.getAlertaDeStock(),
                    p.necesitaReposicion(),
                    p.faltanParaElUmbral(),
                    p.valorACosteCentimos(),
                    p.getCosteCentimos(),
                    p.getPrecioCentimos());
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
            Long id,
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
            Long id, String nombre, String personaDeContacto, String email, String telefono) {

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

    /** El stock con su resumen. Lo que hay que reponer sale primero. */
    @GetMapping("/stock")
    @Transactional(readOnly = true)
    public StockConResumen stock() {
        List<Producto> todos = productos.buscarConStock();

        ResumenDeAlmacen resumen = new ResumenDeAlmacen(
                todos.size(),
                (int) todos.stream().filter(Producto::necesitaReposicion).count(),
                (int) todos.stream().filter(p -> p.getStock() == 0).count(),
                todos.stream().mapToLong(Producto::valorACosteCentimos).sum());

        return new StockConResumen(resumen, todos.stream().map(LineaDeStock::de).toList());
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
            @RequestParam(required = false) Long producto,
            @RequestParam(defaultValue = "" + MOVIMIENTOS_POR_DEFECTO) int limite) {

        var pagina = PageRequest.of(0, Math.clamp(limite, 1, MOVIMIENTOS_MAXIMO));

        List<MovimientoDeInventario> encontrados = producto == null
                ? movimientos.buscarRecientes(pagina)
                : movimientos.buscarPorProducto(producto, pagina);

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
