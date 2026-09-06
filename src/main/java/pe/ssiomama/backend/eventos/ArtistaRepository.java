package pe.ssiomama.backend.eventos;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a {@code "Artista"}. */
public interface ArtistaRepository extends JpaRepository<Artista, String> {

    /** Todos, por nombre. Es la lista que se elige al montar un cartel. */
    List<Artista> findAllByOrderByNombreAsc();
}
