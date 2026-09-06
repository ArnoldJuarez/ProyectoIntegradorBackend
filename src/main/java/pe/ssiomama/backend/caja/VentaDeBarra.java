package pe.ssiomama.backend.caja;

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
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.ssiomama.backend.eventos.Evento;

/**
 * Un cobro en la barra — tabla {@code "BarSale"}.
 *
 * <p>Cada venta cuelga de un {@link ArqueoDeCaja}: es lo que permite saber al
 * cerrar cuánto debería haber en el cajón. Y cuelga de una noche cuando la hay,
 * para poder calcular el consumo por asistente.
 *
 * <p>El evento es opcional: se puede cobrar fuera de una noche con cartel —una
 * prueba, un privado— y la venta sigue siendo válida.
 */
@Entity
@Table(name = "VentaBarra")
public class VentaDeBarra {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sesionCajaId", nullable = false)
    private ArqueoDeCaja arqueo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eventoId")
    private Evento evento;

    /**
     * El total cobrado, en céntimos.
     *
     * <p>Se guarda además de poder sumarse desde las líneas porque es el importe
     * que se cobró de verdad: si mañana cambia el precio de un producto, esta
     * venta tiene que seguir diciendo lo que pagó aquel cliente.
     */
    @Column(name = "totalCentimos", nullable = false)
    private int totalCentimos;

    /** Recuerda pasarlo como parámetro en las consultas, nunca como literal. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "metodoPago", nullable = false)
    private MetodoDePago metodoDePago;

    @Column(name = "creadoEn", nullable = false)
    private Instant creadaEn;

    @OneToMany(mappedBy = "venta", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<LineaDeVenta> lineas = new ArrayList<>();

    protected VentaDeBarra() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /**
     * Abre una venta vacía colgada de un turno de caja.
     *
     * <p>Cuelga siempre de un arqueo, nunca del aire: una venta que no pertenece
     * a ningún turno no se le puede reclamar a nadie cuando el cajón no cuadra.
     *
     * @param evento la noche en que se cobró, si la hay. Es opcional porque
     *     también se vende un martes sin evento programado, y sirve para saber
     *     cuánto factura la barra en cada noche.
     */
    static VentaDeBarra registrar(ArqueoDeCaja arqueo, Evento evento, MetodoDePago metodo) {
        VentaDeBarra v = new VentaDeBarra();
        v.id = UUID.randomUUID().toString();
        v.arqueo = arqueo;
        v.evento = evento;
        v.metodoDePago = metodo;
        v.totalCentimos = 0;
        v.creadaEn = Instant.now();
        return v;
    }

    /** Añade una línea y recalcula el total. */
    void anadirLinea(LineaDeVenta linea) {
        lineas.add(linea);
        // toIntExact y no un cast: la columna es integer, y un total que no cabe
        // tiene que reventar aquí y no dar la vuelta a negativo en la base.
        this.totalCentimos =
                Math.toIntExact(lineas.stream().mapToLong(LineaDeVenta::importeCentimos).sum());
    }

    /** Cuántas unidades salieron en esta venta, sumando todas las líneas. */
    public int unidadesVendidas() {
        return lineas.stream().mapToInt(LineaDeVenta::getCantidad).sum();
    }

    /** ¿Este cobro acabó en el cajón? Solo el efectivo cuenta para el arqueo. */
    public boolean esEfectivo() {
        return metodoDePago.esEfectivo();
    }

    public String getId() {
        return id;
    }

    public ArqueoDeCaja getArqueo() {
        return arqueo;
    }

    public Evento getEvento() {
        return evento;
    }

    public int getTotalCentimos() {
        return totalCentimos;
    }

    public MetodoDePago getMetodoDePago() {
        return metodoDePago;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public List<LineaDeVenta> getLineas() {
        return lineas;
    }
}
