package pe.ssiomama.backend.reservados;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.ssiomama.backend.clientes.Cliente;
import pe.ssiomama.backend.eventos.Evento;

/**
 * Solicitud de un reservado para una noche — tabla {@code "TableReservation"}.
 *
 * <p>La tabla tiene {@code unique (eventId, tableId)}: la misma mesa no se puede
 * pedir dos veces para la misma noche, y quien lo impide es la base, no el
 * código. Eso importa porque entre comprobar «¿está libre?» e insertar pasa un
 * instante, y dos personas pidiendo a la vez pasan las dos la comprobación.
 */
@Entity
@Table(name = "TableReservation")
public class Reserva {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eventId", nullable = false)
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tableId", nullable = false)
    private MesaVip mesa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customerId", nullable = false)
    private Cliente cliente;

    @Column(name = "guests", nullable = false)
    private int personas;

    /**
     * Depósito, en céntimos. Nace en 0: se acuerda por teléfono al confirmar, no
     * lo fija la web.
     */
    @Column(name = "depositCents", nullable = false)
    private int depositoCentimos;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private EstadoDeReserva estado;

    @Column(name = "notes")
    private String notas;

    @Column(name = "createdAt", nullable = false)
    private Instant creadaEn;

    protected Reserva() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /** Una solicitud llegada por la web: pedida, sin depósito y sin confirmar. */
    public static Reserva solicitar(
            Evento evento, MesaVip mesa, Cliente cliente, int personas, String notas) {
        Reserva r = new Reserva();
        r.id = UUID.randomUUID().toString();
        r.evento = evento;
        r.mesa = mesa;
        r.cliente = cliente;
        r.personas = personas;
        r.depositoCentimos = 0;
        r.estado = EstadoDeReserva.REQUESTED;
        r.notas = notas == null || notas.isBlank() ? null : notas;
        r.creadaEn = Instant.now();
        return r;
    }

    public String getId() {
        return id;
    }

    public Evento getEvento() {
        return evento;
    }

    public MesaVip getMesa() {
        return mesa;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public int getPersonas() {
        return personas;
    }

    public int getDepositoCentimos() {
        return depositoCentimos;
    }

    public EstadoDeReserva getEstado() {
        return estado;
    }

    public String getNotas() {
        return notas;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }
}
