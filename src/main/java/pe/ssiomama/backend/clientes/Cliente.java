package pe.ssiomama.backend.clientes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Alguien que ha comprado, reservado o pedido el aviso del cartel — tabla
 * {@code "Customer"}.
 *
 * <h2>De dónde salen los ids</h2>
 *
 * <p>Los numera la base: {@code id} es una columna identity (1, 2, 3…) y
 * Hibernate lo lee al insertar. Antes de guardarse, un cliente nuevo no tiene id.
 */
@Entity
@Table(name = "Cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "apellido")
    private String apellido;

    @Column(name = "correo", nullable = false, unique = true)
    private String email;

    @Column(name = "telefono")
    private String telefono;

    /** Columna {@code date}, sin hora: aquí una fecha de nacimiento no tiene instante. */
    @Column(name = "fechaNacimiento")
    private LocalDate fechaDeNacimiento;

    @Column(name = "distrito")
    private String distrito;

    @Column(name = "consienteMarketing", nullable = false)
    private boolean aceptaMarketing;

    /**
     * Cuándo dijo que sí.
     *
     * <p>Un consentimiento sin fecha no vale: si mañana alguien pregunta cuándo
     * lo dio, hay que poder responder.
     */
    @Column(name = "consintioEn")
    private Instant consintioEn;

    /*
     * Contadores cacheados. Los mantiene quien registra la compra o la visita,
     * no se recalculan al leer: el panel los enseña en cada listado y hacerlo
     * con count() por cliente sería una consulta por fila.
     */
    @Column(name = "visitas", nullable = false)
    private int visitas;

    @Column(name = "gastoTotalCentimos", nullable = false)
    private int gastadoCentimos;

    @Column(name = "ultimaVisitaEn")
    private Instant ultimaVisita;

    @Column(name = "creadoEn", nullable = false)
    private Instant creadoEn;

    protected Cliente() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /** Alguien que llega por primera vez, dando su consentimiento. */
    public static Cliente nuevoConConsentimiento(String nombre, String email) {
        Cliente cliente = new Cliente();
        cliente.nombre = nombre;
        cliente.email = email;
        cliente.aceptaMarketing = true;
        cliente.consintioEn = Instant.now();
        cliente.creadoEn = Instant.now();
        return cliente;
    }

    /**
     * Marca el consentimiento de quien ya estaba en la base.
     *
     * <p>No toca el nombre a propósito: quien ya compró puede tener nombre y
     * apellido correctos de entonces, y pisarlos con lo que escriba hoy en un
     * formulario de dos campos sería perder información.
     */
    public void aceptarMarketing() {
        this.aceptaMarketing = true;
        this.consintioEn = Instant.now();
    }

    /** Alguien que llega pidiendo un reservado. */
    public static Cliente nuevoConContacto(
            String nombre, String apellido, String email, String telefono) {
        Cliente cliente = new Cliente();
        cliente.nombre = nombre;
        cliente.apellido = apellido == null || apellido.isBlank() ? null : apellido;
        cliente.email = email;
        cliente.telefono = telefono;
        cliente.creadoEn = Instant.now();
        return cliente;
    }

    /**
     * Actualiza los datos de contacto de quien ya estaba.
     *
     * <p>Aquí sí se pisan, al revés que en el boletín: un formulario de reserva
     * pide nombre, apellido y teléfono, y quien lo rellena está dando sus datos
     * de hoy. Un teléfono viejo es peor que ninguno cuando hay que llamar para
     * confirmar la mesa.
     *
     * <p>El apellido solo se pisa si viene algo: es opcional en el formulario, y
     * dejarlo vacío no debería borrar el que ya había.
     */
    public void actualizarContacto(String nombre, String apellido, String telefono) {
        this.nombre = nombre;
        if (apellido != null && !apellido.isBlank()) {
            this.apellido = apellido;
        }
        this.telefono = telefono;
    }

    /**
     * Anota una compra pagada en la ficha.
     *
     * <p>Estos tres campos son un resumen: se podrían calcular sumando los
     * pedidos cada vez que se abre una ficha. Están precalculados porque la
     * lista de clientes los ordena y filtra por ellos, y recalcularlos ahí
     * significaría recorrer todos los pedidos de todos los clientes para pintar
     * una tabla.
     *
     * <p>El precio de tenerlos es este método: si una compra se registra sin
     * pasar por aquí, el resumen y los pedidos dejan de cuadrar.
     */
    public void registrarCompra(int importeCentimos, Instant cuando) {
        this.gastadoCentimos += importeCentimos;
        this.visitas += 1;
        this.ultimaVisita = cuando;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellido() {
        return apellido;
    }

    /** Nombre y apellido, o solo el nombre si no hay apellido. */
    public String getNombreCompleto() {
        return apellido == null || apellido.isBlank() ? nombre : nombre + " " + apellido;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefono() {
        return telefono;
    }

    public LocalDate getFechaDeNacimiento() {
        return fechaDeNacimiento;
    }

    public String getDistrito() {
        return distrito;
    }

    public boolean isAceptaMarketing() {
        return aceptaMarketing;
    }

    public Instant getConsintioEn() {
        return consintioEn;
    }

    public int getVisitas() {
        return visitas;
    }

    public int getGastadoCentimos() {
        return gastadoCentimos;
    }

    public Instant getUltimaVisita() {
        return ultimaVisita;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
