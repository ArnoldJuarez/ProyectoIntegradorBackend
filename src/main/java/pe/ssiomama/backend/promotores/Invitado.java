package pe.ssiomama.backend.promotores;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Una fila de una lista de invitados — tabla {@code "Invitado"}.
 *
 * <p>{@link #personas} existe porque en la puerta se apunta «Ana +3», no cuatro
 * nombres: lo que se controla es cuánta gente entra a cuenta de la lista.
 */
@Entity
@Table(name = "Invitado")
public class Invitado {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listaInvitadosId", nullable = false)
    private ListaDeInvitados lista;

    @Column(name = "nombreCompleto", nullable = false)
    private String nombreCompleto;

    @Column(name = "personas", nullable = false)
    private int personas;

    @Column(name = "entro", nullable = false)
    private boolean entro;

    @Column(name = "entroEn")
    private Instant entroEn;

    protected Invitado() {
        // Hibernate necesita un constructor sin argumentos.
    }

    public String getId() {
        return id;
    }

    public ListaDeInvitados getLista() {
        return lista;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public int getPersonas() {
        return personas;
    }

    public boolean isEntro() {
        return entro;
    }

    public Instant getEntroEn() {
        return entroEn;
    }
}
