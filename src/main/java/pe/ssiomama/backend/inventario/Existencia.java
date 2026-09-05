package pe.ssiomama.backend.inventario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import pe.ssiomama.backend.eventos.Sala;

/**
 * Cuántas unidades de un producto hay en una sala — tabla {@code "StockItem"}.
 *
 * <p>Es el <b>saldo actual</b>, no el histórico: los movimientos que lo
 * produjeron viven en {@link MovimientoDeInventario}. Se guarda por separado a
 * propósito, porque la pantalla de inventario lo consulta constantemente y
 * recalcularlo sumando todos los movimientos de un producto sería recorrer meses
 * de historial cada vez.
 *
 * <p>El precio de eso es que las dos cosas pueden desajustarse si alguien
 * escribe un movimiento sin actualizar el saldo. Por eso el saldo solo debería
 * cambiarlo quien registra el movimiento, nunca a mano.
 *
 * <p>La tabla tiene {@code unique (productId, venueId)}: un producto no puede
 * tener dos saldos en la misma sala.
 */
@Entity
@Table(name = "StockItem")
public class Existencia {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productId", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venueId", nullable = false)
    private Sala sala;

    @Column(name = "quantity", nullable = false)
    private int cantidad;

    @Column(name = "updatedAt", nullable = false)
    private Instant actualizadoEn;

    protected Existencia() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /**
     * ¿Hay que reponer?
     *
     * <p>Compara contra el umbral que lleva cada producto, no contra un número
     * fijo: no se repone igual una caja de cerveza que una botella de whisky.
     */
    public boolean necesitaReposicion() {
        return cantidad <= producto.getAlertaDeStock();
    }

    /** Cuánto falta para volver al umbral. 0 si no hace falta reponer. */
    public int faltanParaElUmbral() {
        return Math.max(producto.getAlertaDeStock() - cantidad, 0);
    }

    /**
     * Lo que vale lo que hay guardado, en céntimos.
     *
     * <p>Se valora a coste, no a precio de venta: es lo que se ha pagado por
     * ello, no lo que se espera cobrar.
     */
    public long valorACosteCentimos() {
        return (long) cantidad * producto.getCosteCentimos();
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

    public int getCantidad() {
        return cantidad;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
