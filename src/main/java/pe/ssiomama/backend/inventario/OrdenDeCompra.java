package pe.ssiomama.backend.inventario;

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

/**
 * Un pedido a proveedor — tabla {@code "OrdenCompra"}.
 *
 * <p>Los crea a mano quien lleva el almacén, o el agente de inventario al
 * aprobar su propuesta. En los dos casos nacen en {@link EstadoDeOrden#BORRADOR}
 * y una persona decide cuándo enviarlos y confirma cuándo llegan.
 *
 * <p>El total es un saldo cacheado de las líneas, igual que el stock lo es de
 * los movimientos: se recalcula cada vez que cambian, en el mismo sitio, para
 * que no puedan decir cosas distintas.
 *
 * <p>Como en {@code Evento}, el enum de la base se llama distinto que la clase
 * Java ({@code EstadoOrdenCompra}): <b>nunca un literal del estado en HQL</b>,
 * siempre como parámetro.
 */
@Entity
@Table(name = "OrdenCompra")
public class OrdenDeCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedorId", nullable = false)
    private Proveedor proveedor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "estado", nullable = false)
    private EstadoDeOrden estado;

    @Column(name = "totalCentimos", nullable = false)
    private int totalCentimos;

    /** Cuándo dijo el proveedor que llegaría. Opcional: muchos no lo dicen. */
    @Column(name = "esperadoEn")
    private Instant esperadoEn;

    @Column(name = "recibidoEn")
    private Instant recibidoEn;

    @Column(name = "creadoEn", nullable = false)
    private Instant creadoEn;

    @OneToMany(mappedBy = "orden", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LineaDeOrden> lineas = new ArrayList<>();

    protected OrdenDeCompra() {
        // Hibernate necesita un constructor sin argumentos.
    }

    static OrdenDeCompra borrador(Proveedor proveedor, Instant esperadoEn) {
        OrdenDeCompra o = new OrdenDeCompra();
        o.proveedor = proveedor;
        o.estado = EstadoDeOrden.BORRADOR;
        o.esperadoEn = esperadoEn;
        o.creadoEn = Instant.now();
        return o;
    }

    /** Cambia proveedor, fecha y líneas de un borrador. Las líneas se sustituyen enteras. */
    void rehacer(Proveedor proveedor, Instant esperadoEn, List<LineaPedida> pedidas) {
        this.proveedor = proveedor;
        this.esperadoEn = esperadoEn;
        this.lineas.clear();
        for (LineaPedida p : pedidas) {
            this.lineas.add(LineaDeOrden.de(this, p.producto(), p.cantidad(), p.costeUnitarioCentimos()));
        }
        long total = lineas.stream().mapToLong(LineaDeOrden::importeCentimos).sum();
        this.totalCentimos = (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** Una línea tal como llega del formulario, con el producto ya resuelto. */
    record LineaPedida(Producto producto, int cantidad, int costeUnitarioCentimos) {}

    void enviar() {
        this.estado = EstadoDeOrden.ENVIADO;
    }

    void recibir(Instant cuando) {
        this.estado = EstadoDeOrden.RECIBIDO;
        this.recibidoEn = cuando;
    }

    void cancelar() {
        this.estado = EstadoDeOrden.CANCELADO;
    }

    public Long getId() {
        return id;
    }

    public Proveedor getProveedor() {
        return proveedor;
    }

    public EstadoDeOrden getEstado() {
        return estado;
    }

    public int getTotalCentimos() {
        return totalCentimos;
    }

    public Instant getEsperadoEn() {
        return esperadoEn;
    }

    public Instant getRecibidoEn() {
        return recibidoEn;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public List<LineaDeOrden> getLineas() {
        return lineas;
    }

    /** El número de la orden con ceros delante (OC-0007): lo que se dicta por teléfono al proveedor. */
    public String getCodigo() {
        return "OC-" + String.format("%04d", id);
    }
}
