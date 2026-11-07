package pe.ssiomama.backend.entradas;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.caja.MetodoDePago;
import pe.ssiomama.backend.clientes.Cliente;
import pe.ssiomama.backend.eventos.Evento;

/**
 * Una compra de entradas — tabla {@code "Pedido"}.
 *
 * <p>En el esquema anterior se llamaba {@code Order}, que es palabra reservada
 * en SQL y solo funcionaba porque los identificadores van entrecomillados. El
 * nombre en español quita esa trampa de encima.
 *
 * <h2>El código, y por qué no es el id</h2>
 *
 * <p>{@code codigo} es lo que se le enseña al cliente ({@code DSC-XXXXXX}) y lo
 * que aparece en la URL de sus entradas. Es distinto del id a propósito: el id
 * es interno y el código es corto, legible por teléfono y no dice cuántos
 * pedidos lleva el local.
 *
 * <h2>Los importes van guardados</h2>
 *
 * <p>{@code totalCentimos} podría sumarse desde las líneas, pero es lo que se le
 * cobró de verdad a esa persona. Si mañana cambia un precio o una comisión, este
 * pedido tiene que seguir diciendo lo que se pagó entonces.
 */
@Entity
@Table(name = "Pedido")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** El que ve el cliente: {@code DSC-000123}. */
    @Column(name = "codigo", nullable = false, unique = true)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clienteId", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eventoId", nullable = false)
    private Evento evento;

    /**
     * El promotor cuyo código trajo esta compra. Nulo si llegó sola.
     *
     * <p>Es lo que cuenta para su comisión. Se fija al abrir el pedido y no se
     * cambia después: reasignar una venta a otro promotor es cambiar a quién se
     * le debe dinero, y eso no se hace editando un pedido.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotorId")
    private Usuario promotor;

    @Column(name = "subtotalCentimos", nullable = false)
    private int subtotalCentimos;

    /** Gastos de gestión. Van aparte del subtotal para poder enseñarlos. */
    @Column(name = "comisionCentimos", nullable = false)
    private int comisionCentimos;

    @Column(name = "totalCentimos", nullable = false)
    private int totalCentimos;

    /** Recuerda pasarlo como parámetro en las consultas, nunca como literal. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "estado", nullable = false)
    private EstadoDePedido estado;

    /** Nulo mientras no se haya pagado. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "metodoPago")
    private MetodoDePago metodoDePago;

    /** Referencia de la pasarela. Hoy va vacía: el cobro está simulado. */
    @Column(name = "refPago")
    private String referenciaDePago;

    @Column(name = "creadoEn", nullable = false)
    private Instant creadoEn;

    @Column(name = "pagadoEn")
    private Instant pagadoEn;

    @OneToMany(mappedBy = "pedido", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<LineaDePedido> lineas = new ArrayList<>();

    @OneToMany(mappedBy = "pedido", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Entrada> entradas = new ArrayList<>();

    protected Pedido() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /**
     * Abre un pedido sin pagar, con el cupo ya apartado.
     *
     * <p>Nace {@link EstadoDePedido#PENDIENTE} y no «vacío»: las líneas ya
     * descontaron existencias del tipo de entrada, así que un pendiente ocupa
     * sitio de verdad. Quien lo cancele tiene que devolver ese cupo.
     *
     * <p>Los importes se calculan aquí y no llegan desde el navegador. Es la
     * regla más importante de esta clase: el precio lo pone el servidor. Un
     * total que viene del cliente es un total que se puede editar.
     */
    static Pedido abrir(
            String codigo, Cliente cliente, Evento evento, int comisionCentimos, Instant cuando) {
        return abrir(codigo, cliente, evento, null, comisionCentimos, cuando);
    }

    /** Igual, con el promotor que trajo la compra (o nulo). */
    static Pedido abrir(
            String codigo, Cliente cliente, Evento evento, Usuario promotor, int comisionCentimos, Instant cuando) {
        Pedido p = new Pedido();
        p.promotor = promotor;
        p.codigo = codigo;
        p.cliente = cliente;
        p.evento = evento;
        p.subtotalCentimos = 0;
        p.comisionCentimos = comisionCentimos;
        p.totalCentimos = 0;
        p.estado = EstadoDePedido.PENDIENTE;
        p.creadoEn = cuando;
        return p;
    }

    /**
     * Añade una línea y recalcula los totales.
     *
     * <p>La comisión se pasa entera al abrir y se vuelve a fijar aquí porque
     * depende del subtotal, que no se conoce hasta tener todas las líneas.
     */
    void anadirLinea(LineaDePedido linea, int comisionCentimos) {
        lineas.add(linea);
        // toIntExact y no un cast: la columna es integer, y un total que no cabe
        // tiene que reventar aquí y no dar la vuelta a negativo en la base.
        this.subtotalCentimos =
                Math.toIntExact(lineas.stream().mapToLong(LineaDePedido::importeCentimos).sum());
        this.comisionCentimos = comisionCentimos;
        this.totalCentimos = subtotalCentimos + comisionCentimos;
    }

    /**
     * Marca el pedido como pagado.
     *
     * <p>No emite las entradas: de eso se encarga quien orquesta el cobro, que
     * es el único que sabe firmarlas. Aquí solo se cierra el estado.
     *
     * @param referencia lo que devuelve la pasarela. Hoy es un valor simulado,
     *     porque no hay pasarela conectada; cuando la haya, es lo que permite
     *     buscar el cobro en su panel cuando alguien reclama.
     */
    void marcarPagado(MetodoDePago metodo, String referencia, Instant cuando) {
        this.estado = EstadoDePedido.PAGADO;
        this.metodoDePago = metodo;
        this.referenciaDePago = referencia;
        this.pagadoEn = cuando;
    }

    /** ¿Se puede pagar todavía? Solo un pendiente. */
    public boolean admitePago() {
        return estado == EstadoDePedido.PENDIENTE;
    }

    /** Cuántas entradas se compraron, sumando todas las líneas. */
    public int totalDeEntradas() {
        return lineas.stream().mapToInt(LineaDePedido::getCantidad).sum();
    }

    public boolean estaPagado() {
        return estado == EstadoDePedido.PAGADO;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Evento getEvento() {
        return evento;
    }

    /** Nulo si la compra no llegó por ningún promotor. */
    public Usuario getPromotor() {
        return promotor;
    }

    public int getSubtotalCentimos() {
        return subtotalCentimos;
    }

    public int getComisionCentimos() {
        return comisionCentimos;
    }

    public int getTotalCentimos() {
        return totalCentimos;
    }

    public EstadoDePedido getEstado() {
        return estado;
    }

    public MetodoDePago getMetodoDePago() {
        return metodoDePago;
    }

    public String getReferenciaDePago() {
        return referenciaDePago;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getPagadoEn() {
        return pagadoEn;
    }

    public List<LineaDePedido> getLineas() {
        return lineas;
    }

    public List<Entrada> getEntradas() {
        return entradas;
    }
}
