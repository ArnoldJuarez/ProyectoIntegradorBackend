package pe.ssiomama.backend.inventario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Un producto dentro de un pedido a proveedor — tabla {@code "LineaOrdenCompra"}. */
@Entity
@Table(name = "LineaOrdenCompra")
public class LineaDeOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordenCompraId", nullable = false)
    private OrdenDeCompra orden;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productoId", nullable = false)
    private Producto producto;

    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    /** Lo pactado con el proveedor por unidad. Al recibir pasa a ser el coste de la ficha. */
    @Column(name = "costeUnitarioCentimos", nullable = false)
    private int costeUnitarioCentimos;

    protected LineaDeOrden() {
        // Hibernate necesita un constructor sin argumentos.
    }

    static LineaDeOrden de(OrdenDeCompra orden, Producto producto, int cantidad, int costeUnitarioCentimos) {
        LineaDeOrden l = new LineaDeOrden();
        l.orden = orden;
        l.producto = producto;
        l.cantidad = cantidad;
        l.costeUnitarioCentimos = costeUnitarioCentimos;
        return l;
    }

    long importeCentimos() {
        return (long) cantidad * costeUnitarioCentimos;
    }

    public Long getId() {
        return id;
    }

    public Producto getProducto() {
        return producto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public int getCosteUnitarioCentimos() {
        return costeUnitarioCentimos;
    }
}
