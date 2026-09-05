package pe.ssiomama.backend.caja;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import pe.ssiomama.backend.eventos.Sala;
import pe.ssiomama.backend.personal.Empleado;

/**
 * Una sesión de caja, de que se abre hasta que se cuadra — tabla
 * {@code "CashSession"}.
 *
 * <p>Es el turno de una persona en la barra. Se abre con un fondo, se van
 * registrando ventas contra ella, y al cerrar se cuenta el dinero y se compara
 * con lo que debería haber.
 *
 * <h2>Los tres importes que hay que distinguir</h2>
 *
 * <ul>
 *   <li><b>Apertura</b> — el fondo con el que se empieza, para dar cambio.
 *   <li><b>Esperado</b> — apertura más lo que dicen las ventas registradas.
 *   <li><b>Contado</b> — lo que hay de verdad en el cajón al cerrar.
 * </ul>
 *
 * <p>La diferencia entre los dos últimos es lo único que importa de un arqueo, y
 * puede ir en los dos sentidos: sobra dinero tan a menudo como falta, casi
 * siempre por un cambio mal dado.
 *
 * <h2>Por qué la diferencia está guardada y no se calcula</h2>
 *
 * <p>Porque es un dato del cierre, no una fórmula. Si mañana se corrige el
 * precio de un producto, lo esperado de aquella noche cambiaría y con él una
 * diferencia recalculada, reescribiendo el pasado. El arqueo tiene que seguir
 * diciendo lo que se contó aquel día.
 */
@Entity
@Table(name = "CashSession")
public class ArqueoDeCaja {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venueId", nullable = false)
    private Sala sala;

    /** Quién se hace responsable de esta caja. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employeeId", nullable = false)
    private Empleado responsable;

    /** El fondo inicial, para dar cambio. */
    @Column(name = "openingCents", nullable = false)
    private int aperturaCentimos;

    /** Apertura más lo que suman las ventas registradas. */
    @Column(name = "expectedCents", nullable = false)
    private int esperadoCentimos;

    /** Lo que se contó al cerrar. Nulo mientras la caja siga abierta. */
    @Column(name = "countedCents")
    private Integer contadoCentimos;

    /**
     * Contado menos esperado, tal y como quedó al cerrar.
     *
     * <p>Positivo = sobra dinero. Negativo = falta. Nulo mientras esté abierta.
     */
    @Column(name = "differenceCents")
    private Integer diferenciaCentimos;

    @Column(name = "openedAt", nullable = false)
    private Instant abiertaEn;

    /** Nulo mientras siga abierta. */
    @Column(name = "closedAt")
    private Instant cerradaEn;

    protected ArqueoDeCaja() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /** ¿Sigue abierta? Se mira por la fecha de cierre, que es el hecho. */
    public boolean estaAbierta() {
        return cerradaEn == null;
    }

    /**
     * ¿El arqueo cuadró?
     *
     * <p>Una caja abierta todavía no cuadra ni descuadra: no hay recuento.
     */
    public boolean cuadro() {
        return diferenciaCentimos != null && diferenciaCentimos == 0;
    }

    /** Cuánto se desvió, sin signo. 0 si cuadró o si sigue abierta. */
    public int desviacionCentimos() {
        return diferenciaCentimos == null ? 0 : Math.abs(diferenciaCentimos);
    }

    /**
     * El efectivo que entró durante el turno: lo esperado menos el fondo
     * inicial.
     *
     * <p><b>No es lo que se vendió.</b> Comprobado sobre los datos reales: en un
     * turno con apertura de 200, esperado 1130 y 4671 cobrados con tarjeta, esta
     * resta da 930 — solo el efectivo. Lo vendido de verdad eran 5601.
     *
     * <p>Y tiene que ser así: lo esperado en caja es lo que debería haber
     * <b>en el cajón</b>, y una tarjeta no llega al cajón. Llamar «vendido» a
     * esta cifra haría creer que un turno facturó seis veces menos de lo que
     * facturó.
     *
     * <p>Para el total vendido hay que sumar todos los métodos de pago, que es
     * lo que hace la ficha del arqueo.
     */
    public int efectivoCobradoCentimos() {
        return esperadoCentimos - aperturaCentimos;
    }

    public String getId() {
        return id;
    }

    public Sala getSala() {
        return sala;
    }

    public Empleado getResponsable() {
        return responsable;
    }

    public int getAperturaCentimos() {
        return aperturaCentimos;
    }

    public int getEsperadoCentimos() {
        return esperadoCentimos;
    }

    public Integer getContadoCentimos() {
        return contadoCentimos;
    }

    public Integer getDiferenciaCentimos() {
        return diferenciaCentimos;
    }

    public Instant getAbiertaEn() {
        return abiertaEn;
    }

    public Instant getCerradaEn() {
        return cerradaEn;
    }
}
