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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
@Table(name = "Evento")
public class Evento {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    /** Identificador de la URL pública: {@code /evento/viernes-urbano}. */
    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "imagenPortada")
    private String portada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salaId", nullable = false)
    private Sala sala;

    @Column(name = "aperturaPuertas", nullable = false)
    private Instant puertasA;

    @Column(name = "empiezaEn", nullable = false)
    private Instant empiezaA;

    @Column(name = "terminaEn", nullable = false)
    private Instant terminaA;

    @Column(name = "edadMinima", nullable = false)
    private int edadMinima;

    @Column(name = "vestimenta")
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
    @Column(name = "estado", nullable = false)
    private EstadoDeEvento estado;

    @Column(name = "publicadoEn")
    private Instant publicadoEn;

    @Column(name = "creadoEn", nullable = false)
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

    /**
     * Crea una noche nueva.
     *
     * <p>{@code empiezaA} se iguala a la apertura de puertas a propósito, igual
     * que hacía el sistema anterior: la noche se factura por el día en que abren
     * las puertas, y tener dos instantes distintos que casi siempre coinciden
     * solo genera preguntas sobre cuál es el bueno. El día que haga falta
     * separarlos —un concierto que empieza tres horas después de abrir— se
     * añade al formulario.
     */
    static Evento crear(
            String slug,
            String titulo,
            String descripcion,
            Sala sala,
            Instant puertasA,
            Instant terminaA,
            int edadMinima,
            String vestimenta,
            EstadoDeEvento estado) {

        Evento e = new Evento();
        e.id = UUID.randomUUID().toString();
        e.slug = slug;
        e.titulo = titulo;
        e.descripcion = vacioANulo(descripcion);
        e.sala = sala;
        e.puertasA = puertasA;
        e.empiezaA = puertasA;
        e.terminaA = terminaA;
        e.edadMinima = edadMinima;
        e.vestimenta = vacioANulo(vestimenta);
        e.estado = estado;
        e.publicadoEn = estado == EstadoDeEvento.PUBLICADO ? Instant.now() : null;
        e.creadoEn = Instant.now();
        return e;
    }

    /**
     * Cambia los datos de una noche.
     *
     * <p><b>El slug no se toca.</b> Forma parte de la URL pública, y cambiarlo
     * rompería los enlaces que ya se compartieron en redes y los que la gente
     * tiene guardados. Un título nuevo con la dirección de antes es mucho menos
     * grave que una dirección que da 404.
     *
     * <p>{@code publicadoEn} solo se pone la primera vez que se publica. Es la
     * fecha en que la noche salió a la calle, no la del último retoque: si se
     * despublica y se vuelve a publicar, sigue siendo la original.
     */
    void actualizar(
            String titulo,
            String descripcion,
            Sala sala,
            Instant puertasA,
            Instant terminaA,
            int edadMinima,
            String vestimenta,
            EstadoDeEvento estado) {

        this.titulo = titulo;
        this.descripcion = vacioANulo(descripcion);
        this.sala = sala;

        /*
         * Al mover las puertas, la hora de empezar se mueve con ellas y
         * conserva el hueco.
         *
         * No se iguala a la apertura como al crear: una noche puede abrir a las
         * 20:00 y arrancar la música a las 21:00, y esa hora de diferencia es un
         * dato que alguien decidió. Igualarlas en cada edición la borraría en
         * silencio, y quien solo venía a corregir una falta de ortografía no se
         * enteraría de que ha movido el comienzo.
         */
        this.empiezaA = puertasA.plus(Duration.between(this.puertasA, this.empiezaA));
        this.puertasA = puertasA;
        this.terminaA = terminaA;
        this.edadMinima = edadMinima;
        this.vestimenta = vacioANulo(vestimenta);
        this.estado = estado;

        if (estado == EstadoDeEvento.PUBLICADO && publicadoEn == null) {
            this.publicadoEn = Instant.now();
        }
    }

    /** La imagen del cartel: una dirección pegada a mano o un flyer subido (ver PortadaController). */
    void cambiarPortada(String url) {
        this.portada = vacioANulo(url);
    }

    /** ¿Cambia la fecha respecto a la que ya tenía? */
    boolean cambiaLaFecha(Instant nuevaApertura) {
        return !puertasA.equals(nuevaApertura);
    }

    /** Un texto vacío en la base es un nulo, no una cadena de cero caracteres. */
    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
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

    /**
     * Lo que cuesta entrar: el tipo activo más barato que se paga, en céntimos.
     *
     * <p>Las listas gratuitas («lista mujeres») no cuentan: un «desde S/ 0» en
     * la cartelera dice que la noche es gratis para todo el mundo, y no lo es.
     * Que haya lista se dice aparte, con {@link #tieneListaGratis()}.
     */
    public int getPrecioDesdeCentimos() {
        return tiposDeEntrada.stream()
                .filter(TipoDeEntrada::isActivo)
                .mapToInt(TipoDeEntrada::getPrecioCentimos)
                .filter(precio -> precio > 0)
                .min()
                .orElse(0);
    }

    /** ¿Hay una lista de entrada gratuita a la venta? */
    public boolean tieneListaGratis() {
        return tiposDeEntrada.stream()
                .anyMatch(t -> t.isActivo() && t.getPrecioCentimos() == 0 && t.getDisponibles() > 0);
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
