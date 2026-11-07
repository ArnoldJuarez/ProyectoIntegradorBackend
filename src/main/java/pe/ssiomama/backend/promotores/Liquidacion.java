package pe.ssiomama.backend.promotores;

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
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.caja.MetodoDePago;

/**
 * Un pago hecho a un promotor — tabla {@code "LiquidacionPromotor"}.
 *
 * <h2>Las cifras se copian y no se recalculan</h2>
 *
 * <p>Igual que un arqueo de caja: es la foto de lo que se pagó. Si mañana se le
 * sube el porcentaje al promotor, lo que cobró el mes pasado no cambia, y esta
 * fila tiene que seguir diciendo con qué porcentaje se le pagó entonces.
 *
 * <h2>Lo que cubre</h2>
 *
 * <p>Los pedidos y las listas que entraron en ella quedan marcados con su id
 * ({@code liquidacionId}). Eso es lo que impide pagar dos veces lo mismo: lo
 * marcado ya no sale como pendiente.
 */
@Entity
@Table(name = "LiquidacionPromotor")
public class Liquidacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotorId", nullable = false)
    private Usuario promotor;

    /** Cubre las noches que empezaron hasta este instante. */
    @Column(name = "hasta", nullable = false)
    private Instant hasta;

    @Column(name = "pedidos", nullable = false)
    private int pedidos;

    @Column(name = "entradas", nullable = false)
    private int entradas;

    /** El precio de las entradas vendidas con su código, sin gastos de gestión. */
    @Column(name = "ventasCentimos", nullable = false)
    private int ventasCentimos;

    @Column(name = "comisionPct", nullable = false)
    private int comisionPorcentaje;

    @Column(name = "comisionCentimos", nullable = false)
    private int comisionCentimos;

    @Column(name = "invitadosEntraron", nullable = false)
    private int invitadosEntraron;

    @Column(name = "bonoInvitadoCentimos", nullable = false)
    private int bonoPorInvitadoCentimos;

    @Column(name = "bonoCentimos", nullable = false)
    private int bonoCentimos;

    /** Lo que se suma o se resta a mano: un adelanto, una penalización. */
    @Column(name = "ajusteCentimos", nullable = false)
    private int ajusteCentimos;

    @Column(name = "totalCentimos", nullable = false)
    private int totalCentimos;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "metodoPago", nullable = false)
    private MetodoDePago metodoDePago;

    /** El número de operación del Yape o de la transferencia, si lo hay. */
    @Column(name = "referencia")
    private String referencia;

    @Column(name = "nota")
    private String nota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registradaPorId")
    private Usuario registradaPor;

    @Column(name = "pagadaEn", nullable = false)
    private Instant pagadaEn;

    protected Liquidacion() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /**
     * Abre la liquidación vacía. Las cifras se ponen después con
     * {@link #fijarCifras}, cuando ya se sabe qué pedidos y listas se marcaron.
     */
    static Liquidacion abrir(
            Usuario promotor, Instant hasta, MetodoDePago metodo, String referencia, String nota,
            int ajusteCentimos, Usuario quien, Instant ahora) {
        Liquidacion l = new Liquidacion();
        l.promotor = promotor;
        l.hasta = hasta;
        l.metodoDePago = metodo;
        l.referencia = vacioANulo(referencia);
        l.nota = vacioANulo(nota);
        l.ajusteCentimos = ajusteCentimos;
        l.registradaPor = quien;
        l.pagadaEn = ahora;
        l.comisionPorcentaje = promotor.getComisionPorcentaje() == null ? 0 : promotor.getComisionPorcentaje();
        l.bonoPorInvitadoCentimos = promotor.getBonoPorInvitadoCentimos();
        return l;
    }

    /**
     * Pone las cifras a partir de lo marcado.
     *
     * <p>La comisión se redondea una vez, sobre el total vendido, y no pedido a
     * pedido: redondear cada pedido y sumar puede desviarse varios céntimos.
     */
    void fijarCifras(int pedidos, int entradas, long ventasCentimos, int invitadosEntraron) {
        this.pedidos = pedidos;
        this.entradas = entradas;
        this.ventasCentimos = Math.toIntExact(ventasCentimos);
        this.comisionCentimos = Math.toIntExact(Math.round(ventasCentimos * comisionPorcentaje / 100.0));
        this.invitadosEntraron = invitadosEntraron;
        this.bonoCentimos = Math.multiplyExact(invitadosEntraron, bonoPorInvitadoCentimos);
        this.totalCentimos = comisionCentimos + bonoCentimos + ajusteCentimos;
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public Long getId() {
        return id;
    }

    public Usuario getPromotor() {
        return promotor;
    }

    public Instant getHasta() {
        return hasta;
    }

    public int getPedidos() {
        return pedidos;
    }

    public int getEntradas() {
        return entradas;
    }

    public int getVentasCentimos() {
        return ventasCentimos;
    }

    public int getComisionPorcentaje() {
        return comisionPorcentaje;
    }

    public int getComisionCentimos() {
        return comisionCentimos;
    }

    public int getInvitadosEntraron() {
        return invitadosEntraron;
    }

    public int getBonoPorInvitadoCentimos() {
        return bonoPorInvitadoCentimos;
    }

    public int getBonoCentimos() {
        return bonoCentimos;
    }

    public int getAjusteCentimos() {
        return ajusteCentimos;
    }

    public int getTotalCentimos() {
        return totalCentimos;
    }

    public MetodoDePago getMetodoDePago() {
        return metodoDePago;
    }

    public String getReferencia() {
        return referencia;
    }

    public String getNota() {
        return nota;
    }

    public Usuario getRegistradaPor() {
        return registradaPor;
    }

    public Instant getPagadaEn() {
        return pagadaEn;
    }
}
