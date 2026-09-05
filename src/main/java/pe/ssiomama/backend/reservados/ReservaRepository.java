package pe.ssiomama.backend.reservados;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaRepository extends JpaRepository<Reserva, String> {

    /**
     * Los ids de las mesas ya pedidas para una noche.
     *
     * <p>Cuenta cualquier estado menos {@code CANCELLED}: una solicitud sin
     * confirmar también bloquea la mesa, porque alguien va a llamar para
     * cerrarla y no puede encontrarse con que la pidió otro entretanto.
     */
    @Query("""
            select r.mesa.id from Reserva r
            where r.evento.slug = :slug
              and r.estado <> :cancelada
            """)
    List<String> idsDeMesasPedidas(
            @Param("slug") String slug, @Param("cancelada") EstadoDeReserva cancelada);
}
