package pe.ssiomama.backend.inventario;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo de productos.
 *
 * <p>Equivale a lo que hoy hace {@code app/panel/inventario/page.tsx}
 * consultando Prisma directamente. Es el primer endpoint de la migración y
 * existe sobre todo para demostrar la rebanada completa: PostgreSQL, entidades
 * JPA contra el esquema de Prisma, y JSON saliendo por HTTP.
 */
@RestController
@RequestMapping("/api/inventario")
public class ProductoController {

    private final ProductoRepository productos;

    public ProductoController(ProductoRepository productos) {
        this.productos = productos;
    }

    /**
     * Lo que sale por la API.
     *
     * <p>Se devuelve un DTO y no la entidad por dos razones concretas: la
     * entidad tiene una relación perezosa que al serializarla dispararía
     * consultas —o reventaría, porque {@code open-in-view} está desactivado—, y
     * porque el JSON público no debería cambiar de forma solo porque alguien
     * añada una columna.
     *
     * <p>El coste ({@code costCents}) no viaja: es margen del negocio y no
     * pinta nada en una pantalla que puede acabar abierta en la barra.
     */
    public record ProductoResumen(
            Long id,
            String sku,
            String nombre,
            String categoria,
            UnidadDeProducto unidad,
            int precioCentimos,
            int alertaDeStock) {}

    /**
     * {@code @Transactional(readOnly = true)} mantiene la sesión abierta durante
     * el mapeo, que es lo que permite tocar {@code getCategoria()} sin que salte
     * {@code LazyInitializationException}.
     */
    @GetMapping("/productos")
    @Transactional(readOnly = true)
    public List<ProductoResumen> listar() {
        return productos.findByActivoTrueOrderByNombreAsc().stream()
                .map(p -> new ProductoResumen(
                        p.getId(),
                        p.getSku(),
                        p.getNombre(),
                        p.getCategoria().getNombre(),
                        p.getUnidad(),
                        p.getPrecioCentimos(),
                        p.getAlertaDeStock()))
                .toList();
    }
}
