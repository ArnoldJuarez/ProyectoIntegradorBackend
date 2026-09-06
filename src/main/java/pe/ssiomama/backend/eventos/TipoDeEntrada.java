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

    /**
     * Aparta unidades para un pedido que se acaba de crear.
     *
     * <p>El cupo se reserva <b>antes</b> de cobrar, no después. Si se esperara al
     * pago, dos personas podrían llegar a la pasarela con la última entrada y
     * una de las dos pagaría algo que no existe. Por eso un pedido pendiente ya
     * cuenta como vendido, y por eso cancelarlo tiene que devolverlo.
     *
     * <p>No comprueba el máximo por pedido: eso es una regla de la compra —qué
     * puede pedir una persona de una vez—, no del cupo. Aquí solo importa que no
     * se venda más de lo que hay.
     *
     * @throws IllegalStateException si no quedan suficientes. Quien llama lo
     *     traduce a un mensaje para el comprador; que reviente es preferible a
     *     dejar {@code vendidas} por encima de {@code cantidad} en silencio.
     */
    public void reservarCupo(int unidades) {
        if (unidades <= 0) {
            throw new IllegalArgumentException("Se piden entradas de una en una, no cero ni menos.");
        }
        if (unidades > getDisponibles()) {
            throw new IllegalStateException(
                    "Solo quedan %d entradas %s.".formatted(getDisponibles(), nombre));
        }
        this.vendidas += unidades;
    }

    /** Devuelve el cupo de un pedido que se cae. */
    public void liberarCupo(int unidades) {
        this.vendidas = Math.max(this.vendidas - unidades, 0);
    }

    /**
     * ¿Se puede comprar ahora mismo?
     *
     * <p>Las fechas de venta son opcionales: sin ellas el tipo está a la venta
     * mientras el evento lo esté. Con ellas se hacen las entradas anticipadas,
     * que dejan de venderse a una hora concreta.
     */
    public boolean estaALaVenta(Instant ahora) {
        if (!activo) {
            return false;
        }
        if (ventaDesde != null && ahora.isBefore(ventaDesde)) {
            return false;
        }
        return ventaHasta == null || !ahora.isAfter(ventaHasta);
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
