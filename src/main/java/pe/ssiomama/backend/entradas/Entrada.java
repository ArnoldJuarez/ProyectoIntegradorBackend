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
@Table(name = "Entrada")
public class Entrada {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    /** El identificador legible que ve el cliente. */
    @Column(name = "codigo", nullable = false, unique = true)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedidoId", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipoEntradaId", nullable = false)
    private TipoDeEntrada tipoDeEntrada;

    /**
     * La noche.
     *
     * <p>Está aquí además de en el pedido a propósito: el escáner de la puerta
     * pregunta «¿esta entrada es de hoy?» miles de veces en una noche, y hacerlo
     * sin pasar por el pedido es una tabla menos en cada consulta.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eventoId", nullable = false)
    private Evento evento;

    @Column(name = "nombreTitular")
    private String titular;

    @Column(name = "dniTitular")
    private String dniDelTitular;

    /** Recuerda pasarlo como parámetro en las consultas, nunca como literal. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "estado", nullable = false)
    private EstadoDeEntrada estado;

    @Column(name = "emitidaEn", nullable = false)
    private Instant emitidaEn;

    /**
     * El escaneo, si ya pasó. Nulo mientras no haya entrado.
     *
     * <p>Uno a uno: la tabla {@code "Acceso"} tiene {@code entradaId} único, así
     * que la base impide que una misma entrada se registre dos veces aunque el
     * código lo intentara.
     */
    @OneToOne(mappedBy = "entrada", fetch = FetchType.LAZY)
    private Escaneo escaneo;

    protected Entrada() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /**
     * Emite una entrada al confirmarse el pago.
     *
     * <p>El id se genera aquí y no lo asigna la base por un motivo que no es de
     * estilo: el código del QR es {@code id.firma}, así que hay que <b>conocer
     * el id antes de insertar</b>. Con un id autogenerado en el servidor de base
     * de datos habría que insertar primero y actualizar el código después, y una
     * entrada existiría un instante sin código válido.
     *
     * @param codigoFirmado lo que produce {@link FirmaDeEntrada#codificar}. Se
     *     recibe ya hecho en vez de firmar aquí para que la entidad no dependa
     *     del secreto ni de un componente de Spring.
     */
    static Entrada emitir(
            Pedido pedido,
            TipoDeEntrada tipo,
            String id,
            String codigoFirmado,
            String titular,
            Instant cuando) {
        Entrada e = new Entrada();
        e.id = id;
        e.codigo = codigoFirmado;
        e.pedido = pedido;
        e.tipoDeEntrada = tipo;
        e.evento = pedido.getEvento();
        e.titular = titular == null || titular.isBlank() ? null : titular;
        e.estado = EstadoDeEntrada.VALIDA;
        e.emitidaEn = cuando;
        return e;
    }

    /** ¿Deja pasar ahora mismo? */
    public boolean esValida() {
        return estado.dejaPasar();
    }

    /** ¿Ya entró alguien con ella? */
    public boolean yaSeUso() {
        return escaneo != null;
    }

    /**
     * La marca como usada al pasar por la puerta.
     *
     * <p>No comprueba nada: quien decide si puede pasar es el escáner, que
     * mira el estado, el escaneo previo y la fecha antes de llamar aquí. Si
     * esta comprobara por su cuenta, habría dos sitios donde cambiar la regla
     * y uno de los dos se quedaría atrás.
     */
    void marcarUsada() {
        this.estado = EstadoDeEntrada.USADA;
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
