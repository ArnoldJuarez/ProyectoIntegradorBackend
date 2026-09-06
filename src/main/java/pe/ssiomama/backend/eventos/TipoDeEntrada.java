package pe.ssiomama.backend.eventos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

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

    /**
     * Crea un tipo de entrada para una noche.
     *
     * <p>{@code ventaHasta} se fija al comienzo del evento: no tiene sentido
     * vender una entrada anticipada cuando la noche ya empezó. Quien quiera otra
     * fecha de corte —una Early Bird que cierra una semana antes— la edita
     * después; el valor por defecto es el que no sorprende a nadie.
     */
    static TipoDeEntrada crear(
            Evento evento,
            String nombre,
            String descripcion,
            int precioCentimos,
            int cantidad,
            int maximoPorPedido,
            int consumicionesIncluidas,
            boolean activo) {

        TipoDeEntrada t = new TipoDeEntrada();
        t.id = UUID.randomUUID().toString();
        t.evento = evento;
        t.nombre = nombre;
        t.descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
        t.precioCentimos = precioCentimos;
        t.cantidad = cantidad;
        t.vendidas = 0;
        t.ventaHasta = evento.getEmpiezaA();
        t.maximoPorPedido = maximoPorPedido;
        t.consumicionesIncluidas = consumicionesIncluidas;
        t.activo = activo;
        return t;
    }

    /**
     * Cambia los datos de un tipo de entrada.
     *
     * <p>{@code vendidas} no se toca nunca desde aquí. Es un recuento de hechos
     * —cuánta gente compró—, no un campo del formulario, y editarlo a mano
     * descuadraría el aforo respecto a los pedidos que ya existen.
     */
    void actualizar(
            String nombre,
            String descripcion,
            int precioCentimos,
            int cantidad,
            int maximoPorPedido,
            int consumicionesIncluidas,
            boolean activo) {

        this.nombre = nombre;
        this.descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
        this.precioCentimos = precioCentimos;
        this.cantidad = cantidad;
        this.maximoPorPedido = maximoPorPedido;
        this.consumicionesIncluidas = consumicionesIncluidas;
        this.activo = activo;
    }

    /** Lo retira de la venta sin borrarlo: las entradas ya emitidas siguen valiendo. */
    void retirarDeLaVenta() {
        this.activo = false;
    }

    /** ¿Se ha vendido alguna? Decide si se puede borrar o solo retirar. */
    public boolean tieneVentas() {
        return vendidas > 0;
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
