package pe.ssiomama.backend.inventario;

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
import pe.ssiomama.backend.eventos.Sala;

/**
 * Cada entrada o salida de mercancía — tabla {@code "MovimientoStock"}.
 *
 * <p>Es el histórico que explica por qué {@code Producto.stock} vale lo que
 * vale. Sin él, un inventario que no cuadra no se puede investigar: se ve que
 * faltan doce botellas pero no cuándo ni por qué.
 *
 * <p>Nada borra ni edita un movimiento. Una equivocación se corrige con otro
 * movimiento de tipo {@code AJUSTE}, igual que en contabilidad. Así el historial
 * sigue explicando lo que pasó de verdad, incluidos los errores.
 *
 * <p><b>Las cantidades llevan signo</b>: negativas cuando sale mercancía. Ver
 * {@link TipoDeMovimiento}.
 */
@Entity
@Table(name = "MovimientoStock")
public class MovimientoDeInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productoId", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salaId", nullable = false)
    private Sala sala;

    /** Recuerda pasarlo como parámetro en las consultas, nunca como literal. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo", nullable = false)
    private TipoDeMovimiento tipo;

    /**
     * Lo que sumó o restó al saldo, <b>con su signo</b>.
     *
     * <p>Positiva cuando entra mercancía y negativa cuando sale: una venta de
     * tres cervezas se guarda como {@code -3}. El tipo dice el motivo, no el
     * sentido. Ver la nota en {@link TipoDeMovimiento}.
     */
    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    /**
     * Lo que costó cada unidad en ESTE movimiento, en céntimos.
     *
     * <p>No es el coste del producto: el mismo whisky puede llegar a distinto
     * precio en dos compras, y para calcular márgenes de verdad hace falta saber
     * a cuánto entró cada partida, no una media.
     */
    @Column(name = "costeUnitarioCentimos", nullable = false)
    private int costeUnitarioCentimos;

    /** Número de factura, de albarán o nota de quien lo registró. */
    @Column(name = "referencia")
    private String referencia;

    /**
     * Quién lo movió. Puede faltar: un movimiento del seed o de una importación
     * no tiene a nadie detrás.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuarioId")
    private Usuario responsable;

    @Column(name = "creadoEn", nullable = false)
    private Instant creadoEn;

    protected MovimientoDeInventario() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /**
     * El movimiento que deja una venta de barra.
     *
     * <p>La cantidad va <b>negativa</b>. El signo vive en el número y no solo en
     * el tipo, que es como estaba en la base original: así una suma de
     * movimientos da directamente el saldo, sin tener que consultar el tipo de
     * cada fila para saber si sumar o restar.
     *
     * <p>El coste unitario se copia del producto en este momento. Es lo que
     * permite calcular el margen de aquella noche aunque el proveedor haya
     * subido el precio después.
     *
     * @param referencia a qué venta corresponde, para poder rastrearlo
     */
    public static MovimientoDeInventario porVentaDeBarra(
            Producto producto, Sala sala, int unidades, String referencia, Usuario quien) {
        MovimientoDeInventario m = new MovimientoDeInventario();
        m.producto = producto;
        m.sala = sala;
        m.tipo = TipoDeMovimiento.VENTA;
        m.cantidad = -Math.abs(unidades);
        m.costeUnitarioCentimos = producto.getCosteCentimos();
        m.referencia = referencia;
        m.responsable = quien;
        m.creadoEn = Instant.now();
        return m;
    }

    /**
     * Un movimiento registrado a mano desde el panel: compra, merma o ajuste.
     *
     * <p>{@code VENTA} no se puede crear por aquí, y la comprobación está en el
     * servicio, no en esta fábrica: una venta la escribe el punto de venta y
     * nadie más. Si se pudiera registrar a mano habría ventas sin dinero detrás,
     * y el arqueo dejaría de cuadrar con el inventario.
     *
     * @param unidadesConSigno ya con su sentido resuelto: positivo si entra
     *     mercancía, negativo si sale
     * @param costeUnitarioCentimos lo pagado por unidad. Solo significa algo en
     *     una compra; en merma y ajuste va a cero porque no se pagó nada.
     * @param nota lo que explica el movimiento: un número de albarán, «se rompió
     *     una caja», «recuento del lunes»
     */
    public static MovimientoDeInventario aMano(
            Producto producto,
            Sala sala,
            TipoDeMovimiento tipo,
            int unidadesConSigno,
            int costeUnitarioCentimos,
            String nota,
            Usuario quien) {

        MovimientoDeInventario m = new MovimientoDeInventario();
        m.producto = producto;
        m.sala = sala;
        m.tipo = tipo;
        m.cantidad = unidadesConSigno;
        m.costeUnitarioCentimos = costeUnitarioCentimos;
        m.referencia = nota == null || nota.isBlank() ? null : nota.trim();
        m.responsable = quien;
        m.creadoEn = Instant.now();
        return m;
    }

    /** ¿Entró mercancía o salió? */
    public boolean esEntrada() {
        return cantidad > 0;
    }

    /**
     * Cuánto se movió, sin signo. Para enseñar «3 unidades» en vez de «−3».
     */
    public int unidadesMovidas() {
        return Math.abs(cantidad);
    }

    /**
     * Lo que movió en dinero, a coste y sin signo, en céntimos.
     *
     * <p>Se usa el valor absoluto para que sea una magnitud: si no, una venta
     * daría un importe negativo y sumar la columna no significaría nada.
     */
    public long importeCentimos() {
        return (long) unidadesMovidas() * costeUnitarioCentimos;
    }

    public Long getId() {
        return id;
    }

    public Producto getProducto() {
        return producto;
    }

    public Sala getSala() {
        return sala;
    }

    public TipoDeMovimiento getTipo() {
        return tipo;
    }

    public int getCantidad() {
        return cantidad;
    }

    public int getCosteUnitarioCentimos() {
        return costeUnitarioCentimos;
    }

    public String getReferencia() {
        return referencia;
    }

    public Usuario getResponsable() {
        return responsable;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
