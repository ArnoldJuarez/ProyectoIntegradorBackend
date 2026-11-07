package pe.ssiomama.backend.inventario;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    /** Todos, por orden alfabético: es una lista corta que se lee buscando un nombre. */
    List<Proveedor> findAllByOrderByNombreAsc();
}
