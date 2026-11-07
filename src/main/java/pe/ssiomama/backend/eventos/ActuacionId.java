package pe.ssiomama.backend.eventos;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Clave de una actuación: la noche y el artista.
 *
 * <p>La tabla {@code "EventArtist"} no tiene id propio; su clave primaria es la
 * pareja {@code (eventId, artistId)}. Eso ya dice algo del modelo: un artista no
 * puede aparecer dos veces en el mismo cartel, y lo impide la base, no el código.
 *
 * <p>JPA exige que una clave compuesta sea {@code Serializable} y tenga
 * {@code equals} y {@code hashCode}: los usa para saber si dos filas cargadas
 * son la misma entidad.
 */
@Embeddable
public class ActuacionId implements Serializable {

    @Column(name = "eventoId", nullable = false)
    private Long eventoId;

    @Column(name = "artistaId", nullable = false)
    private Long artistaId;

    protected ActuacionId() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /** La clave de una actuación concreta. */
    static ActuacionId de(Long eventoId, Long artistaId) {
        ActuacionId id = new ActuacionId();
        id.eventoId = eventoId;
        id.artistaId = artistaId;
        return id;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public Long getArtistaId() {
        return artistaId;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof ActuacionId id)) {
            return false;
        }
        return Objects.equals(eventoId, id.eventoId) && Objects.equals(artistaId, id.artistaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventoId, artistaId);
    }
}
