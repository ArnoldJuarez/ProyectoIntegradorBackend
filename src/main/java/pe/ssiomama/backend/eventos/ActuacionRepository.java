package pe.ssiomama.backend.eventos;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a {@code "EventoArtista"}: quién toca en cada noche. */
public interface ActuacionRepository extends JpaRepository<Actuacion, ActuacionId> {

    List<Actuacion> findByEventoIdOrderByCabezaDeCartelDescArtistaNombreAsc(Long eventoId);

    /**
     * Vacía el cartel de una noche.
     *
     * <p>Al editar, el cartel se reemplaza entero en vez de calcular altas y
     * bajas: es más simple, y no hay nada colgando de una actuación que se pueda
     * perder — la hora del set y si es cabeza de cartel se vuelven a escribir.
     *
     * <p>{@code @Modifying} porque es un borrado masivo en una sola sentencia.
     * Sin él, Spring Data intentaría tratarla como una consulta de lectura.
     */
    @Modifying
    @Query("delete from Actuacion a where a.evento.id = :eventoId")
    void borrarElCartelDe(@Param("eventoId") Long eventoId);

    /**
     * El cartel de una noche como entidades. Para borrarlo junto con la noche
     * hay que pasar por aquí y no por el borrado masivo: ver
     * {@link GestionDeEventos#borrar}.
     */
    @Query("select a from Actuacion a where a.evento.id = :eventoId")
    java.util.List<Actuacion> buscarDeLaNoche(@Param("eventoId") Long eventoId);
}
