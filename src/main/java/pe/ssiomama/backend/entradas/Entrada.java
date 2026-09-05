package pe.ssiomama.backend.entradas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.ssiomama.backend.eventos.Evento;
import pe.ssiomama.backend.eventos.TipoDeEntrada;

/**
 * Una entrada emitida — tabla {@code "Ticket"}.
 *
 * <p>Es lo que se escanea en la puerta. Una por persona: un pedido de tres
 * entradas genera tres filas, porque cada una entra por su lado y se marca como
 * usada por separado.
 *
 * <h2>El código ES lo que va dentro del QR</h2>
 *
 * <p>{@code code} no es un identificador corto y legible: guarda exactamente
 * {@code id.firma}, el mismo texto que viaja en el código de barras. Comprobado
 * contra los datos reales — una entrada del sistema anterior tiene
 * {@code a8acf845-…-b43bc766ffd6.pvMaiBGg0aowckcS0iI5iI4w}.
 *
 * <p>La firma HMAC es lo que impide que alguien con un generador de QR se
 * fabrique una entrada; ver {@link FirmaDeEntrada}. Por eso este campo <b>no se
 * enseña entero en pantalla</b>: son sesenta caracteres que no dicen nada a una
 * persona, y quien lo lea por teléfono se equivocará.
 *
 * <h2>Por qué el titular es opcional</h2>
 *
 * <p>Se compran entradas para amigos, y pedir el nombre de los tres en el
 * momento de pagar es la forma más rápida de perder la venta. Se rellena
 * después, si hace falta.
 */
@Entity
@Table(name = "Ticket")
public class Entrada {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    /** El identificador legible que ve el cliente. */
    @Column(name = "code", nullable = false, unique = true)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orderId", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticketTypeId", nullable = false)
    private TipoDeEntrada tipoDeEntrada;

    /**
     * La noche.
     *
     * <p>Está aquí además de en el pedido a propósito: el escáner de la puerta
     * pregunta «¿esta entrada es de hoy?» miles de veces en una noche, y hacerlo
     * sin pasar por el pedido es una tabla menos en cada consulta.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eventId", nullable = false)
    private Evento evento;

    @Column(name = "holderName")
    private String titular;

    @Column(name = "holderDni")
    private String dniDelTitular;

    /** Recuerda pasarlo como parámetro en las consultas, nunca como literal. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private EstadoDeEntrada estado;

    @Column(name = "issuedAt", nullable = false)
    private Instant emitidaEn;

    /**
     * El escaneo, si ya pasó. Nulo mientras no haya entrado.
     *
     * <p>Uno a uno: la tabla {@code "CheckIn"} tiene {@code ticketId} único, así
     * que la base impide que una misma entrada se registre dos veces aunque el
     * código lo intentara.
     */
    @OneToOne(mappedBy = "entrada", fetch = FetchType.LAZY)
    private Escaneo escaneo;

    protected Entrada() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /** ¿Deja pasar ahora mismo? */
    public boolean esValida() {
        return estado.dejaPasar();
    }

    /** ¿Ya entró alguien con ella? */
    public boolean yaSeUso() {
        return escaneo != null;
    }

    public String getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public TipoDeEntrada getTipoDeEntrada() {
        return tipoDeEntrada;
    }

    public Evento getEvento() {
        return evento;
    }

    public String getTitular() {
        return titular;
    }

    public String getDniDelTitular() {
        return dniDelTitular;
    }

    public EstadoDeEntrada getEstado() {
        return estado;
    }

    public Instant getEmitidaEn() {
        return emitidaEn;
    }

    public Escaneo getEscaneo() {
        return escaneo;
    }
}
