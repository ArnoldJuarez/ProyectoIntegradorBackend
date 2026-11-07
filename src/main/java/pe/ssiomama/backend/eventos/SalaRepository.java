package pe.ssiomama.backend.eventos;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a {@code "Sala"}. */
public interface SalaRepository extends JpaRepository<Sala, Long> {

    /**
     * Las salas de mayor a menor aforo.
     *
     * <p>La barra usa la primera cuando no se le dice cuál. Con un solo local da
     * igual, pero el criterio tiene que ser estable: si el orden lo decidiera la
     * base, dos turnos consecutivos podrían abrir caja en salas distintas sin
     * que nadie eligiera nada.
     */
    List<Sala> findAllByOrderByAforoDesc();
}
