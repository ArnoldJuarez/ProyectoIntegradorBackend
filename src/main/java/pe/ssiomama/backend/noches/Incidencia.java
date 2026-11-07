package pe.ssiomama.backend.noches;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.eventos.Evento;

/**
 * Algo que pasó en la noche y no es una venta — tabla {@code "IncidenciaNoche"}.
 *
 * <p>Una pelea, un corte de luz, una queja. Son las cosas que explican unos
 * números raros cuando se miran un mes después («esa noche la barra facturó la
 * mitad»: se cortó la luz a la una), y por eso van en el expediente de la noche
 * y en lo que lee la IA.
 */
@Entity
@Table(name = "IncidenciaNoche")
public class Incidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eventoId", nullable = false)
    private Evento evento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoDeIncidencia tipo;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registradaPorId")
    private Usuario registradaPor;

    /** Cuándo pasó, que no es cuándo se apuntó: se apunta al cerrar, con calma. */
    @Column(name = "ocurrioEn", nullable = false)
    private Instant ocurrioEn;

    @Column(name = "creadaEn", nullable = false)
    private Instant creadaEn;

    protected Incidencia() {
        // Hibernate necesita un constructor sin argumentos.
    }

    static Incidencia apuntar(
            Evento evento, TipoDeIncidencia tipo, String descripcion, Instant ocurrioEn, Usuario quien) {
        Incidencia i = new Incidencia();
        i.evento = evento;
        i.tipo = tipo;
        i.descripcion = descripcion.trim();
        i.ocurrioEn = ocurrioEn;
        i.registradaPor = quien;
        i.creadaEn = Instant.now();
        return i;
    }

    public Long getId() {
        return id;
    }

    public Evento getEvento() {
        return evento;
    }

    public TipoDeIncidencia getTipo() {
        return tipo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Instant getOcurrioEn() {
        return ocurrioEn;
    }
}
