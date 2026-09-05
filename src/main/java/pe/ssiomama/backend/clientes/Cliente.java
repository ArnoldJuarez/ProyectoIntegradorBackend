package pe.ssiomama.backend.clientes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Alguien que ha comprado, reservado o pedido el aviso del cartel — tabla
 * {@code "Customer"}.
 *
 * <h2>De dónde salen los ids</h2>
 *
 * <p>Las columnas {@code id} no tienen {@code DEFAULT} en la base: el sistema
 * anterior las generaba en la aplicación con {@code cuid()}. Por eso aquí el id
 * es asignado y no lleva {@code @GeneratedValue}.
 *
 * <p>Las filas nuevas llevan un {@code UUID} en vez de un cuid, así que en la
 * tabla convivirán los dos formatos. Es aceptable porque el id es una cadena
 * opaca: nada en el sistema lo interpreta ni deduce nada de su forma. Reproducir
 * cuid en Java sería reimplementar una librería ajena sin forma de comprobar que
 * genera lo mismo, y ese riesgo no compra nada.
 */
@Entity
@Table(name = "Customer")
public class Cliente {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "firstName", nullable = false)
    private String nombre;

    @Column(name = "lastName")
    private String apellido;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "phone")
    private String telefono;

    /** Columna {@code date}, sin hora: aquí una fecha de nacimiento no tiene instante. */
    @Column(name = "birthDate")
    private LocalDate fechaDeNacimiento;

    @Column(name = "district")
    private String distrito;

    @Column(name = "marketingConsent", nullable = false)
    private boolean aceptaMarketing;

    /**
     * Cuándo dijo que sí.
     *
     * <p>Un consentimiento sin fecha no vale: si mañana alguien pregunta cuándo
     * lo dio, hay que poder responder.
     */
    @Column(name = "consentAt")
    private Instant consintioEn;

    /*
     * Contadores cacheados. Los mantiene quien registra la compra o la visita,
     * no se recalculan al leer: el panel los enseña en cada listado y hacerlo
     * con count() por cliente sería una consulta por fila.
     */
    @Column(name = "visitCount", nullable = false)
    private int visitas;

    @Column(name = "totalSpentCents", nullable = false)
    private int gastadoCentimos;

    @Column(name = "lastVisitAt")
    private Instant ultimaVisita;

    @Column(name = "createdAt", nullable = false)
    private Instant creadoEn;

    protected Cliente() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /** Alguien que llega por primera vez, dando su consentimiento. */
    public static Cliente nuevoConConsentimiento(String nombre, String email) {
        Cliente cliente = new Cliente();
        cliente.id = UUID.randomUUID().toString();
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
        cliente.id = UUID.randomUUID().toString();
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

    public String getId() {
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
