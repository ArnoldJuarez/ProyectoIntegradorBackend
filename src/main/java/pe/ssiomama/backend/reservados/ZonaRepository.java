package pe.ssiomama.backend.reservados;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a {@code "Zona"}. */
public interface ZonaRepository extends JpaRepository<Zona, String> {

    /** Las de una sala, en el orden en que se pintan en el plano. */
    List<Zona> findBySalaIdOrderByOrdenAsc(String salaId);
}
