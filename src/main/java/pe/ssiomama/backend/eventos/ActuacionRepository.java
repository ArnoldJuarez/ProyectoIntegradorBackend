package pe.ssiomama.backend.eventos;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a {@code "EventoArtista"}: quién toca en cada noche. */
public interface ActuacionRepository extends JpaRepository<Actuacion, ActuacionId> {

    List<Actuacion> findByEventoIdOrderByCabezaDeCartelDescArtistaNombreAsc(String eventoId);

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
    void borrarElCartelDe(@Param("eventoId") String eventoId);
}
