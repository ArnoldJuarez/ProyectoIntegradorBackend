package pe.ssiomama.backend.inventario;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a {@code "Product"}. Es el equivalente de las consultas Prisma del panel. */
public interface ProductoRepository extends JpaRepository<Producto, String> {

    /** Los productos que están a la venta, en el orden en que se listan en el panel. */
    List<Producto> findByActivoTrueOrderByNombreAsc();
}
