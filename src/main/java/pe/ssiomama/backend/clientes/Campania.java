package pe.ssiomama.backend.clientes;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Un envío dirigido a un segmento — tabla {@code "Campania"}.
 *
 * <h2>Nada sale de aquí</h2>
 *
 * <p>Esta entidad guarda el texto, el canal y a quién va dirigido, pero
 * <b>el sistema no manda nada</b>: no hay proveedor de correo ni de SMS
 * conectado. Una campaña se queda en {@link EstadoDeCampania#BORRADOR} hasta que
 * alguien la exporta o la manda por su cuenta.
 *
 * <p>Es lo mismo que hacía el sistema anterior, y conviene que siga siendo
 * evidente: el día que se conecte un proveedor, lo que cambia es quién pone el
 * estado en {@code ENVIADO}, no el modelo.
 *
 * <h2>El destinatario es el segmento, no una lista</h2>
 *
 * <p>Se apunta al segmento y no a los clientes que había dentro al escribirla.
 * Entre que se redacta y se manda pueden pasar días, y en ese tiempo alguien
 * dormido vuelve a comprar: con la regla, esa persona ya no recibe el mensaje.
 */
@Entity
@Table(name = "Campania")
public class Campania {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "canal", nullable = false)
    private CanalDeCampania canal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "segmentoId", nullable = false)
    private Segmento segmento;

    /** El asunto. Solo lo llevan los canales que tienen uno: el correo. */
    @Column(name = "asunto")
    private String asunto;

    @Column(name = "cuerpo", nullable = false)
    private String cuerpo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "estado", nullable = false)
    private EstadoDeCampania estado;

    @Column(name = "programadaEn")
    private Instant programadaEn;

    @Column(name = "enviadaEn")
    private Instant enviadaEn;

    /**
     * Los tres contadores de resultado.
     *
     * <p>Se quedan a cero mientras no haya proveedor: quien los llenaría es
     * quien manda el mensaje y recibe sus avisos de apertura. Están mapeados
     * para que la pantalla pueda enseñarlos el día que signifiquen algo.
     */
    @Column(name = "enviados", nullable = false)
    private int enviados;

    @Column(name = "aperturas", nullable = false)
    private int aperturas;

    @Column(name = "clics", nullable = false)
    private int clics;

    @Column(name = "creadoEn", nullable = false)
    private Instant creadoEn;

    protected Campania() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /**
     * Escribe una campaña nueva, siempre en borrador.
     *
     * <p>No hay forma de crear una ya enviada, y es deliberado: el estado
     * {@code ENVIADO} debería significar que un mensaje salió de verdad.
     */
    static Campania redactar(
            String nombre,
            CanalDeCampania canal,
            Segmento segmento,
            String asunto,
            String cuerpo) {

        Campania c = new Campania();
        c.nombre = nombre;
        c.canal = canal;
        c.segmento = segmento;
        c.asunto = asunto == null || asunto.isBlank() ? null : asunto.trim();
        c.cuerpo = cuerpo;
        c.estado = EstadoDeCampania.BORRADOR;
        c.enviados = 0;
        c.aperturas = 0;
        c.clics = 0;
        c.creadoEn = Instant.now();
        return c;
    }

    /** Cambia el texto de una campaña que todavía no ha salido. */
    void reescribir(String nombre, CanalDeCampania canal, String asunto, String cuerpo) {
        this.nombre = nombre;
        this.canal = canal;
        this.asunto = asunto == null || asunto.isBlank() ? null : asunto.trim();
        this.cuerpo = cuerpo;
    }

    /** La descarta. No se borra: queda el rastro de que se pensó y no salió. */
    void cancelar() {
        this.estado = EstadoDeCampania.CANCELADO;
    }

    /** ¿Se puede todavía editar o cancelar? */
    public boolean sePuedeTocar() {
        return estado.sePuedeTocar();
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public CanalDeCampania getCanal() {
        return canal;
    }

    public Segmento getSegmento() {
        return segmento;
    }

    public String getAsunto() {
        return asunto;
    }

    public String getCuerpo() {
        return cuerpo;
    }

    public EstadoDeCampania getEstado() {
        return estado;
    }

    public Instant getProgramadaEn() {
        return programadaEn;
    }

    public Instant getEnviadaEn() {
        return enviadaEn;
    }

    public int getEnviados() {
        return enviados;
    }

    public int getAperturas() {
        return aperturas;
    }

    public int getClics() {
        return clics;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
