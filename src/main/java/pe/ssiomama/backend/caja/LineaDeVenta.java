package pe.ssiomama.backend.caja;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pe.ssiomama.backend.inventario.Producto;

/**
 * Cada producto dentro de un cobro de barra — tabla {@code "BarSaleItem"}.
 *
 * <p>Guarda el precio al que se vendió, no el precio actual del producto. Es la
 * misma razón por la que la venta guarda su total: una línea de hace tres meses
 * tiene que seguir diciendo lo que se cobró entonces, aunque el producto haya
 * subido después.
 */
@Entity
@Table(name = "LineaVentaBarra")
public class LineaDeVenta {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ventaBarraId", nullable = false)
    private VentaDeBarra venta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productoId", nullable = false)
    private Producto producto;

    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    /** El precio de aquel momento, en céntimos. */
    @Column(name = "precioUnitarioCentimos", nullable = false)
    private int precioUnitarioCentimos;

    protected LineaDeVenta() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /** Lo que sumó esta línea al total del cobro. */
    public long importeCentimos() {
        return (long) cantidad * precioUnitarioCentimos;
    }

    public String getId() {
        return id;
    }

    public VentaDeBarra getVenta() {
        return venta;
    }

    public Producto getProducto() {
        return producto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public int getPrecioUnitarioCentimos() {
        return precioUnitarioCentimos;
    }
}
