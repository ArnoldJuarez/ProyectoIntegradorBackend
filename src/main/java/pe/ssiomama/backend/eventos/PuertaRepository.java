package pe.ssiomama.backend.eventos;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a {@code "Puerta"}. */
public interface PuertaRepository extends JpaRepository<Puerta, Long> {

    /**
     * La puerta por la que se entra a una sala.
     *
     * <p>Descarta las de emergencia y las cerradas, y ordena por nombre para que
     * dos escaneos seguidos no acaben en puertas distintas por capricho del
     * orden que devuelva la base. Con una sola entrada da igual; con dos, que el
     * criterio sea estable es lo que hace comparables los recuentos.
     *
     * <p>Devuelve {@link Optional} porque una sala puede no tener ninguna
     * registrada: en ese caso el escaneo sigue valiendo y el acceso se guarda
     * sin puerta. Perder el detalle de por dónde entró alguien es mucho menos
     * grave que dejarlo en la calle.
     */
    @Query("""
            select p from Puerta p
            where p.sala.id = :salaId
              and p.activo = true
              and p.esSalidaEmergencia = false
            order by p.nombre asc
            limit 1
            """)
    Optional<Puerta> buscarEntradaDe(@Param("salaId") Long salaId);

    /** Todas las de una sala, emergencias incluidas: el plano las pinta todas. */
    List<Puerta> findBySalaIdOrderByNombreAsc(Long salaId);
}
