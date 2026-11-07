package pe.ssiomama.backend.clientes;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a {@code "Segmento"}. */
public interface SegmentoRepository extends JpaRepository<Segmento, Long> {

    /** Todos, por nombre: es el orden en que se listan en el panel. */
    List<Segmento> findAllByOrderByNombreAsc();
}
