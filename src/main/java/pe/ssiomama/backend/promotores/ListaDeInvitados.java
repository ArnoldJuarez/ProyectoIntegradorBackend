package pe.ssiomama.backend.promotores;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.eventos.Evento;

/**
 * La lista de un promotor para una noche — tabla {@code "ListaInvitados"}.
 *
 * <p>Un promotor puede abrir varias para la misma noche («Cumpleaños Ana»,
 * «Grupo de la uni»), y por eso la unicidad en la base es el trío evento,
 * promotor y nombre.
 */
@Entity
@Table(name = "ListaInvitados")
public class ListaDeInvitados {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eventoId", nullable = false)
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotorId", nullable = false)
    private Usuario promotor;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Cuántas personas admite como mucho. Cuenta personas, no filas. */
    @Column(name = "maxInvitados", nullable = false)
    private int maxInvitados;

    @OneToMany(mappedBy = "lista", fetch = FetchType.LAZY)
    private List<Invitado> invitados = new ArrayList<>();

    protected ListaDeInvitados() {
        // Hibernate necesita un constructor sin argumentos.
    }

    static ListaDeInvitados abrir(Evento evento, Usuario promotor, String nombre, int maxInvitados) {
        ListaDeInvitados l = new ListaDeInvitados();
        l.evento = evento;
        l.promotor = promotor;
        l.nombre = nombre.trim();
        l.maxInvitados = maxInvitados;
        return l;
    }

    void cambiar(String nombre, int maxInvitados) {
        this.nombre = nombre.trim();
        this.maxInvitados = maxInvitados;
    }

    /** Personas apuntadas: una fila «Ana +3» son cuatro. */
    public int apuntados() {
        return invitados.stream().mapToInt(Invitado::getPersonas).sum();
    }

    /** Personas que pasaron por la puerta. */
    public int entraron() {
        return invitados.stream().filter(Invitado::isEntro).mapToInt(Invitado::getPersonas).sum();
    }

    public Long getId() {
        return id;
    }

    public Evento getEvento() {
        return evento;
    }

    public Usuario getPromotor() {
        return promotor;
    }

    public String getNombre() {
        return nombre;
    }

    public int getMaxInvitados() {
        return maxInvitados;
    }

    public List<Invitado> getInvitados() {
        return invitados;
    }
}
