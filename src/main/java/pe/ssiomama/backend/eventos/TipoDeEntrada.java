package pe.ssiomama.backend.eventos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Un tipo de entrada de una noche: Early Bird, General, VIP — tabla
 * {@code "TicketType"}.
 */
@Entity
@Table(name = "TipoEntrada")
public class TipoDeEntrada {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eventoId", nullable = false)
    private Evento evento;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    /** En céntimos enteros, nunca en coma flotante. */
    @Column(name = "precioCentimos", nullable = false)
    private int precioCentimos;

    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    /**
     * Vendidas.
     *
     * <p>Es un contador cacheado, no un {@code count(*)} sobre las entradas: se
     * incrementa al crear el pedido, dentro de la misma transacción
     * serializable, para que dos compras simultáneas no vendan el mismo aforo.
     */
    @Column(name = "vendidas", nullable = false)
    private int vendidas;

    @Column(name = "ventaDesde")
    private Instant ventaDesde;

    @Column(name = "ventaHasta")
    private Instant ventaHasta;

    @Column(name = "maxPorPedido", nullable = false)
    private int maximoPorPedido;

    @Column(name = "incluyeConsumo", nullable = false)
    private int consumicionesIncluidas;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected TipoDeEntrada() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /** Cuántas quedan. Nunca negativo, aunque el contador se desajustara. */
    public int getDisponibles() {
        return Math.max(cantidad - vendidas, 0);
    }

    public String getId() {
        return id;
    }

    public Evento getEvento() {
        return evento;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public int getPrecioCentimos() {
        return precioCentimos;
    }

    public int getCantidad() {
        return cantidad;
    }

    public int getVendidas() {
        return vendidas;
    }

    public Instant getVentaDesde() {
        return ventaDesde;
    }

    public Instant getVentaHasta() {
        return ventaHasta;
    }

    public int getMaximoPorPedido() {
        return maximoPorPedido;
    }

    public int getConsumicionesIncluidas() {
        return consumicionesIncluidas;
    }

    public boolean isActivo() {
        return activo;
    }
}
