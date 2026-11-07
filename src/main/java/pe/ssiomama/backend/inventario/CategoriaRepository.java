package pe.ssiomama.backend.inventario;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a {@code "CategoriaProducto"}. */
public interface CategoriaRepository extends JpaRepository<CategoriaDeProducto, Long> {

    /** Las categorías por nombre: es el orden fijo de las pestañas de la barra. */
    List<CategoriaDeProducto> findAllByOrderByNombreAsc();
}
