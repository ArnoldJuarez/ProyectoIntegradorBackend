package pe.ssiomama.backend.reservados;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Las reservas de una noche, con su mesa y su cliente cargados.
     *
     * <p>Las confirmadas primero: son las que ocupan sitio de verdad. Las
     * solicitudes pendientes van después porque todavía no apartan nada —
     * alguien tiene que llamar para confirmarlas.
     */
    @Query("""
            select r from Reserva r
            join fetch r.mesa
            join fetch r.cliente
            where r.evento.id = :eventoId
            order by r.estado asc, r.creadaEn asc
            """)
    List<Reserva> buscarDeLaNoche(@Param("eventoId") String eventoId);

    /** Las reservas de las noches que empiezan después de un instante, la más cercana primero. */
    @Query("""
            select r from Reserva r
            join fetch r.evento e
            join fetch r.mesa m
            left join fetch m.zona
            join fetch r.cliente
            where e.empiezaA >= :desde
            order by e.empiezaA asc, r.estado asc, r.creadaEn asc
            """)
    List<Reserva> buscarDesde(@Param("desde") Instant desde);

    /** Las de las noches entre dos instantes, la más reciente primero: el histórico. */
    @Query("""
            select r from Reserva r
            join fetch r.evento e
            join fetch r.mesa m
            left join fetch m.zona
            join fetch r.cliente
            where e.empiezaA >= :desde and e.empiezaA < :hasta
            order by e.empiezaA desc, r.estado asc
            """)
    List<Reserva> buscarEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** Una reserva con todo lo que se enseña de ella. */
    @Query("""
            select r from Reserva r
            join fetch r.evento
            join fetch r.mesa m
            left join fetch m.zona
            join fetch r.cliente
            where r.id = :id
            """)
    Optional<Reserva> buscarCompleta(@Param("id") String id);

    /**
     * Quita la solicitud cancelada de un box para una noche.
     *
     * <p>La base tiene {@code unique (eventoId, boxId)} contando también las
     * canceladas: sin esto, un box cancelado quedaba bloqueado para esa noche
     * aunque la web lo enseñara libre, y quien lo pedía después recibía «acaban
     * de pedirlo». La cancelada no se pierde del todo: la solicitud sigue
     * apuntada en las interacciones del cliente.
     */
    @Modifying
    @Query("delete from Reserva r where r.evento.id = :eventoId and r.mesa.id = :mesaId and r.estado = :cancelada")
    int borrarCancelada(
            @Param("eventoId") String eventoId,
            @Param("mesaId") String mesaId,
            @Param("cancelada") EstadoDeReserva cancelada);
}
