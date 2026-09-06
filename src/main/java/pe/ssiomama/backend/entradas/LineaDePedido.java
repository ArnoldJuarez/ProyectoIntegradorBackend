package pe.ssiomama.backend.entradas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pe.ssiomama.backend.eventos.TipoDeEntrada;

/**
 * Cuántas entradas de un tipo lleva un pedido — tabla {@code "OrderItem"}.
 *
 * <p>Guarda el precio de aquel momento, no el actual del tipo de entrada: un
 * pedido de hace tres meses tiene que seguir diciendo lo que costó, aunque la
 * Early Bird haya subido después.
 */
@Entity
@Table(name = "LineaPedido")
public class LineaDePedido {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedidoId", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipoEntradaId", nullable = false)
    private TipoDeEntrada tipoDeEntrada;

    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    /** El precio al que se compró, en céntimos. */
    @Column(name = "precioUnitarioCentimos", nullable = false)
    private int precioUnitarioCentimos;

    protected LineaDePedido() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /** Lo que sumó esta línea al subtotal. */
    public long importeCentimos() {
        return (long) cantidad * precioUnitarioCentimos;
    }

    public String getId() {
        return id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public TipoDeEntrada getTipoDeEntrada() {
        return tipoDeEntrada;
    }

    public int getCantidad() {
        return cantidad;
    }

    public int getPrecioUnitarioCentimos() {
        return precioUnitarioCentimos;
    }
}
