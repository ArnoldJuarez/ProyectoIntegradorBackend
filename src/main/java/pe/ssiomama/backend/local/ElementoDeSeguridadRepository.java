package pe.ssiomama.backend.local;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a {@code "ElementoSeguridad"}. */
public interface ElementoDeSeguridadRepository extends JpaRepository<ElementoDeSeguridad, String> {

    List<ElementoDeSeguridad> findBySalaIdOrderByTipoAscNombreAsc(String salaId);
}
