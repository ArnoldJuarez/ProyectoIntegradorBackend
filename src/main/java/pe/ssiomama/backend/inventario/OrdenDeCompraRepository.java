package pe.ssiomama.backend.inventario;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso a {@code "OrdenCompra"}. */
public interface OrdenDeCompraRepository extends JpaRepository<OrdenDeCompra, Long> {

    /** Todos los pedidos con su proveedor, los más recientes primero. */
    @Query("select o from OrdenDeCompra o join fetch o.proveedor order by o.creadoEn desc")
    List<OrdenDeCompra> buscarTodos();

    /** Un pedido con proveedor, líneas y productos, para su ficha. */
    @Query("""
            select distinct o from OrdenDeCompra o
            join fetch o.proveedor
            left join fetch o.lineas l
            left join fetch l.producto
            where o.id = :id
            """)
    Optional<OrdenDeCompra> buscarConLineas(Long id);
}
