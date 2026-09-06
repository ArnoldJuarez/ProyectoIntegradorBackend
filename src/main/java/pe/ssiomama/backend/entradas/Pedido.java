package pe.ssiomama.backend.entradas;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.ssiomama.backend.caja.MetodoDePago;
import pe.ssiomama.backend.clientes.Cliente;
import pe.ssiomama.backend.eventos.Evento;

/**
 * Una compra de entradas — tabla {@code "Order"}.
 *
 * <p>Ojo con el nombre: {@code order} es palabra reservada en SQL. Funciona
 * porque los identificadores van entrecomillados; sin las comillas, la consulta
 * fallaría con un error de sintaxis que no menciona la tabla.
 *
 * <h2>El código, y por qué no es el id</h2>
 *
 * <p>{@code code} es lo que se le enseña al cliente ({@code DSC-XXXXXX}) y lo
 * que aparece en la URL de sus entradas. Es distinto del id a propósito: el id
 * es interno y el código es corto, legible por teléfono y no dice cuántos
 * pedidos lleva el local.
 *
 * <h2>Los importes van guardados</h2>
 *
 * <p>{@code totalCents} podría sumarse desde las líneas, pero es lo que se le
 * cobró de verdad a esa persona. Si mañana cambia un precio o una comisión, este
 * pedido tiene que seguir diciendo lo que se pagó entonces.
 */
@Entity
@Table(name = "Pedido")
public class Pedido {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    /** El que ve el cliente: {@code DSC-000123}. */
    @Column(name = "codigo", nullable = false, unique = true)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clienteId", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eventoId", nullable = false)
    private Evento evento;

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

    /** Cuántas entradas se compraron, sumando todas las líneas. */
    public int totalDeEntradas() {
        return lineas.stream().mapToInt(LineaDePedido::getCantidad).sum();
    }

    public boolean estaPagado() {
        return estado == EstadoDePedido.PAGADO;
    }

    public String getId() {
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
