package pe.ssiomama.backend.eventos;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Una noche — tabla {@code "Event"}.
 *
 * <h2>Las fechas van en UTC</h2>
 *
 * <p>Las columnas son {@code timestamp without time zone} y guardan el instante
 * en UTC: unas puertas a las 23:30 de Lima están escritas como {@code 04:30} del
 * día siguiente. Aquí se mapean como {@link Instant} y
 * {@code hibernate.jdbc.time_zone: UTC} en {@code application.yml} es lo que
 * evita que Hibernate las reinterprete en la zona del sistema operativo.
 *
 * <p>Sin esa configuración, en una máquina en Lima todas las horas de la web
 * saldrían cinco horas corridas, y sin ningún error por medio. Hay un test que
 * comprueba un instante concreto justamente por eso.
 *
 * <p>Convertir a hora de la sala es tarea de quien lo enseña, no de quien lo
 * guarda.
 */
@Entity
@Table(name = "Event")
public class Evento {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    /** Identificador de la URL pública: {@code /evento/viernes-urbano}. */
    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "title", nullable = false)
    private String titulo;

    @Column(name = "description")
    private String descripcion;

    @Column(name = "coverImage")
    private String portada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venueId", nullable = false)
    private Sala sala;

    @Column(name = "doorsAt", nullable = false)
    private Instant puertasA;

    @Column(name = "startsAt", nullable = false)
    private Instant empiezaA;

    @Column(name = "endsAt", nullable = false)
    private Instant terminaA;

    @Column(name = "minAge", nullable = false)
    private int edadMinima;

    @Column(name = "dressCode")
    private String vestimenta;

    /**
     * El tipo enum de PostgreSQL se llama {@code EventStatus}, no como esta
     * clase.
     *
     * <p>Eso obliga a una regla al escribir consultas: <b>nunca poner un literal
     * de este enum en el HQL</b>. Para un literal, Hibernate deduce el nombre
     * del tipo del de la clase Java y emite {@code ::EstadoDeEvento} contra un
     * tipo que no existe; y no hay anotación que lo cambie —{@code
     * columnDefinition} no afecta a los literales—. Los valores van siempre como
     * parámetro, que sí se enlaza con el tipo mapeado.
     *
     * <p>Leer funciona en cualquier caso, así que el fallo no aparece hasta la
     * primera consulta que <b>filtre</b> por el estado, y el error menciona un
     * tipo que no está escrito en ninguna parte del proyecto.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private EstadoDeEvento estado;

    @Column(name = "publishedAt")
    private Instant publicadoEn;

    @Column(name = "createdAt", nullable = false)
    private Instant creadoEn;

    /**
     * Los tipos de entrada, en el orden en que se muestran al comprar.
     *
     * <p>Va en {@code LAZY} aunque casi siempre se necesiten: el listado de la
     * agenda muestra decenas de noches y traer sus entradas sería una consulta
     * por cada una. Quien las quiera, que las pida con un {@code join fetch}.
     */
    @OneToMany(mappedBy = "evento", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("precioCentimos ASC")
    private List<TipoDeEntrada> tiposDeEntrada = new ArrayList<>();

    /**
     * El cartel, con los cabezas de cartel primero y luego por hora de set.
     *
     * <p>Es el orden en que se lee un cartel: el nombre grande arriba y el resto
     * en el orden en que van a tocar.
     */
    @OneToMany(mappedBy = "evento", fetch = FetchType.LAZY)
    @OrderBy("cabezaDeCartel DESC, empiezaA ASC")
    private List<Actuacion> cartel = new ArrayList<>();

    protected Evento() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /** Aforo puesto a la venta, sumando todos los tipos activos. */
    public int getAforoALaVenta() {
        return tiposDeEntrada.stream()
                .filter(TipoDeEntrada::isActivo)
                .mapToInt(TipoDeEntrada::getCantidad)
                .sum();
    }

    /** Entradas vendidas en total. */
    public int getVendidas() {
        return tiposDeEntrada.stream().mapToInt(TipoDeEntrada::getVendidas).sum();
    }

    /**
     * Lo recaudado por entradas, en céntimos.
     *
     * <p>Es el precio de cada tipo por lo vendido de ese tipo, no una media: en
     * una noche con Early Bird a 12 y VIP a 35, la media no se parece a nada.
     */
    public long getRecaudadoCentimos() {
        return tiposDeEntrada.stream()
                .mapToLong(t -> (long) t.getPrecioCentimos() * t.getVendidas())
                .sum();
    }

    public String getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getPortada() {
        return portada;
    }

    public Sala getSala() {
        return sala;
    }

    public Instant getPuertasA() {
        return puertasA;
    }

    public Instant getEmpiezaA() {
        return empiezaA;
    }

    public Instant getTerminaA() {
        return terminaA;
    }

    public int getEdadMinima() {
        return edadMinima;
    }

    public String getVestimenta() {
        return vestimenta;
    }

    public EstadoDeEvento getEstado() {
        return estado;
    }

    public Instant getPublicadoEn() {
        return publicadoEn;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public List<TipoDeEntrada> getTiposDeEntrada() {
        return tiposDeEntrada;
    }

    public List<Actuacion> getCartel() {
        return cartel;
    }

    /** Lo que cuesta entrar: el tipo activo más barato, en céntimos. */
    public int getPrecioDesdeCentimos() {
        return tiposDeEntrada.stream()
                .filter(TipoDeEntrada::isActivo)
                .mapToInt(TipoDeEntrada::getPrecioCentimos)
                .min()
                .orElse(0);
    }

    /**
     * Porcentaje de aforo vendido, redondeado.
     *
     * <p>Sale a la web pública; lo recaudado no. Que una noche vaya al 80% anima
     * a comprar, pero cuánto se ha ingresado es cosa de la casa.
     */
    public int getPorcentajeVendido() {
        int aforo = getAforoALaVenta();
        return aforo > 0 ? Math.round(getVendidas() * 100f / aforo) : 0;
    }
}
