package pe.ssiomama.backend.inventario;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimientoRepository extends JpaRepository<MovimientoDeInventario, String> {

    /**
     * Los últimos movimientos, de lo más reciente a lo más antiguo.
     *
     * <p>Va paginado porque esta tabla no tiene tope natural: cada venta de
     * barra deja una fila, así que en una noche buena crece por centenares.
     */
    @Query("""
            select m from MovimientoDeInventario m
            join fetch m.producto
            join fetch m.sala
            order by m.creadoEn desc
            """)
    List<MovimientoDeInventario> buscarRecientes(Pageable pagina);

    /** El histórico de un producto concreto, para investigar un descuadre. */
    @Query("""
            select m from MovimientoDeInventario m
            join fetch m.producto
            join fetch m.sala
            where m.producto.id = :idProducto
            order by m.creadoEn desc
            """)
    List<MovimientoDeInventario> buscarPorProducto(
            @Param("idProducto") String idProducto, Pageable pagina);
}
