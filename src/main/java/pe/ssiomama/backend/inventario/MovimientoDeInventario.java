package pe.ssiomama.backend.inventario;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.ssiomama.backend.eventos.Sala;

/**
 * Cada entrada o salida de mercancía — tabla {@code "StockMovement"}.
 *
 * <p>Es el histórico que explica por qué el saldo de {@link Existencia} es el
 * que es. Sin él, un inventario que no cuadra no se puede investigar: se ve que
 * faltan doce botellas pero no cuándo ni por qué.
 *
 * <p>Nada borra ni edita un movimiento. Una equivocación se corrige con otro
 * movimiento de tipo {@code ADJUSTMENT}, igual que en contabilidad. Así el
 * historial sigue explicando lo que pasó de verdad, incluidos los errores.
 *
 * <p><b>Las cantidades llevan signo</b>: negativas cuando sale mercancía. Ver
 * {@link TipoDeMovimiento}.
 */
@Entity
@Table(name = "StockMovement")
public class MovimientoDeInventario {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productId", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venueId", nullable = false)
    private Sala sala;

    /** Recuerda pasarlo como parámetro en las consultas, nunca como literal. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false)
    private TipoDeMovimiento tipo;

    /**
     * Lo que sumó o restó al saldo, <b>con su signo</b>.
     *
     * <p>Positiva cuando entra mercancía y negativa cuando sale: una venta de
     * tres cervezas se guarda como {@code -3}. El tipo dice el motivo, no el
     * sentido. Ver la nota en {@link TipoDeMovimiento}.
     */
    @Column(name = "quantity", nullable = false)
    private int cantidad;

    /**
     * Lo que costó cada unidad en ESTE movimiento, en céntimos.
     *
     * <p>No es el coste del producto: el mismo whisky puede llegar a distinto
     * precio en dos compras, y para calcular márgenes de verdad hace falta saber
     * a cuánto entró cada partida, no una media.
     */
    @Column(name = "unitCostCents", nullable = false)
    private int costeUnitarioCentimos;

    /** Número de factura, de albarán o nota de quien lo registró. */
    @Column(name = "reference")
    private String referencia;

    @Column(name = "createdAt", nullable = false)
    private Instant creadoEn;

    protected MovimientoDeInventario() {
        // Hibernate necesita un constructor sin argumentos.
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

    public String getId() {
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

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
