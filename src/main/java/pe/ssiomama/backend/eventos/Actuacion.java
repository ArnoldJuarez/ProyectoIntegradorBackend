package pe.ssiomama.backend.eventos;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Un artista dentro del cartel de una noche — tabla {@code "EventoArtista"}.
 *
 * <p>No es una simple tabla de unión: lleva a qué hora toca y si es cabeza de
 * cartel, que es información de <b>esa actuación concreta</b> y no del artista
 * ni de la noche por separado. Por eso es una entidad y no un {@code @ManyToMany}.
 */
@Entity
@Table(name = "EventoArtista")
public class Actuacion {

    @EmbeddedId
    private ActuacionId id;

    /**
     * {@code @MapsId} enlaza esta relación con su mitad de la clave compuesta:
     * la columna {@code eventId} es a la vez clave ajena y parte de la primaria,
     * y sin esto Hibernate intentaría mapearla dos veces.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("eventoId")
    @JoinColumn(name = "eventoId", nullable = false)
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("artistaId")
    @JoinColumn(name = "artistaId", nullable = false)
    private Artista artista;

    @Column(name = "inicioSet")
    private Instant empiezaA;

    @Column(name = "finSet")
    private Instant terminaA;

    /** El nombre grande del cartel. Puede haber más de uno. */
    @Column(name = "cabezaCartel", nullable = false)
    private boolean cabezaDeCartel;

    protected Actuacion() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /**
     * Pone a un artista en el cartel de una noche.
     *
     * <p>La clave se construye a mano con los dos ids porque es compuesta:
     * {@code @MapsId} la rellena al persistir, pero solo si el objeto ya existe.
     *
     * @param cabezaDeCartel el nombre grande. Puede haber más de uno.
     */
    static Actuacion enElCartel(Evento evento, Artista artista, boolean cabezaDeCartel) {
        Actuacion a = new Actuacion();
        a.id = ActuacionId.de(evento.getId(), artista.getId());
        a.evento = evento;
        a.artista = artista;
        a.cabezaDeCartel = cabezaDeCartel;
        return a;
    }

    public ActuacionId getId() {
        return id;
    }

    public Evento getEvento() {
        return evento;
    }

    public Artista getArtista() {
        return artista;
    }

    public Instant getEmpiezaA() {
        return empiezaA;
    }

    public Instant getTerminaA() {
        return terminaA;
    }

    public boolean isCabezaDeCartel() {
        return cabezaDeCartel;
    }
}
