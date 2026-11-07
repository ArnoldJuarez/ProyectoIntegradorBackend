package pe.ssiomama.backend.inventario;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso a {@code "Producto"}. Equivale a las consultas Prisma del panel. */
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /** Los productos que están a la venta, en el orden en que se listan en el panel. */
    List<Producto> findByActivoTrueOrderByNombreAsc();

    /**
     * Los productos activos con su categoría ya cargada, para la pantalla de
     * almacén.
     *
     * <p>El {@code join fetch} evita una consulta por fila solo para pintar la
     * categoría. Y con {@code open-in-view} desactivado no es que fuera lento:
     * fallaría al serializar.
     *
     * <p>Se ordena poniendo primero lo que hay que reponer —stock por debajo del
     * umbral—, porque esa es la razón por la que alguien abre esta pantalla un
     * jueves por la tarde.
     */
    @Query("""
            select p from Producto p
            join fetch p.categoria
            where p.activo = true
            order by case when p.stock <= p.alertaDeStock then 0 else 1 end,
                     p.nombre asc
            """)
    List<Producto> buscarConStock();

    /** ¿Está cogido ese SKU? Lo usa el generador para no repetir códigos. */
    boolean existsBySku(String sku);
}
