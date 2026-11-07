package pe.ssiomama.backend.auth;

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
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.ssiomama.backend.personal.Puesto;

/**
 * Personal del local — tabla {@code "Usuario"}.
 *
 * <h2>Absorbió dos tablas del esquema anterior</h2>
 *
 * <p>Antes esto eran tres tablas: {@code User} con la cuenta de acceso,
 * {@code Employee} con los datos laborales y {@code Promoter} con la comisión.
 * Se fusionaron en una.
 *
 * <p>La consecuencia visible es que {@link #correo} y {@link #hashClave} pasan a
 * ser <b>opcionales</b>: hay personal registrado que no entra al sistema —un
 * camarero no necesita cuenta— y antes eso obligaba a inventarle credenciales.
 *
 * <p>Quien no tiene correo, sencillamente no puede iniciar sesión. El login lo
 * busca por correo, así que esas filas nunca aparecen.
 */
@Entity
@Table(name = "Usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Opcional: hay personal sin cuenta de acceso. */
    @Column(name = "correo", unique = true)
    private String email;

    /**
     * Formato {@code scrypt:salt:derivada}, hasheado por el sistema anterior.
     * Nunca sale de aquí: no hay ningún DTO que lo exponga.
     *
     * <p>Nulo para quien no tiene cuenta.
     */
    @Column(name = "hashClave")
    private String hashDeContrasena;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "rol", nullable = false)
    private Rol rol;

    /**
     * Una cuenta desactivada no entra, aunque la contraseña sea correcta. Se
     * comprueba también al validar cada petición, no solo al iniciar sesión: si
     * se comprobara únicamente en el login, despedir a alguien no le cerraría la
     * sesión que ya tiene abierta.
     */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "creadoEn", nullable = false)
    private Instant creadoEn;

    // ── Lo que venía de la tabla Employee ─────────────────────────

    /** Su puesto. Nulo para quien no es personal contratado. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "puestoId")
    private Puesto puesto;

    /** Columna {@code date}: la fecha de alta no tiene hora. */
    @Column(name = "fechaAlta")
    private LocalDate fechaDeAlta;

    /** Sueldo por hora, en céntimos enteros. */
    @Column(name = "tarifaHoraCentimos", nullable = false)
    private int sueldoPorHoraCentimos;

    // ── Lo que venía de la tabla Promoter ─────────────────────────

    /** El código con el que un promotor trae gente. Nulo si no lo es. */
    @Column(name = "codigoPromotor")
    private String codigoDePromotor;

    /** Su comisión, en porcentaje entero. Nulo si no es promotor. */
    @Column(name = "comisionPct")
    private Integer comisionPorcentaje;

    /**
     * Lo que se le paga por cada persona de sus listas que entra de verdad.
     * Cero si solo cobra porcentaje.
     */
    @Column(name = "bonoInvitadoCentimos", nullable = false)
    private int bonoPorInvitadoCentimos;

    protected Usuario() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /** Alguien nuevo en el equipo, activo desde hoy. Sin correo ni clave no podrá entrar al sistema. */
    static Usuario alta(String nombre, Rol rol, LocalDate fechaDeAlta) {
        Usuario u = new Usuario();
        u.nombre = nombre.trim();
        u.rol = rol;
        u.activo = true;
        u.creadoEn = Instant.now();
        u.fechaDeAlta = fechaDeAlta;
        return u;
    }

    /** Cambia la ficha. La clave va aparte: nunca viaja con el resto del formulario. */
    void actualizar(
            String nombre,
            String email,
            Rol rol,
            boolean activo,
            Puesto puesto,
            int sueldoPorHoraCentimos,
            String codigoDePromotor,
            Integer comisionPorcentaje) {
        this.nombre = nombre.trim();
        this.email = email == null || email.isBlank() ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
        this.rol = rol;
        this.activo = activo;
        this.puesto = puesto;
        this.sueldoPorHoraCentimos = sueldoPorHoraCentimos;
        this.codigoDePromotor = codigoDePromotor == null || codigoDePromotor.isBlank()
                ? null
                : codigoDePromotor.trim().toUpperCase(java.util.Locale.ROOT);
        this.comisionPorcentaje = this.codigoDePromotor == null ? null : comisionPorcentaje;
    }

    /** Va aparte de {@link #actualizar} para no tocar a quien ya lo llama. Sin código de promotor, cero. */
    void cambiarBonoPorInvitado(int centimos) {
        this.bonoPorInvitadoCentimos = esPromotor() ? Math.max(0, centimos) : 0;
    }

    void cambiarHashDeContrasena(String hash) {
        this.hashDeContrasena = hash;
    }

    /** ¿Puede iniciar sesión? Solo quien tiene correo y contraseña. */
    public boolean puedeEntrar() {
        return activo && email != null && hashDeContrasena != null;
    }

    /** ¿Es promotor con código asignado? */
    public boolean esPromotor() {
        return codigoDePromotor != null && !codigoDePromotor.isBlank();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getHashDeContrasena() {
        return hashDeContrasena;
    }

    public String getNombre() {
        return nombre;
    }

    public Rol getRol() {
        return rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Puesto getPuesto() {
        return puesto;
    }

    public LocalDate getFechaDeAlta() {
        return fechaDeAlta;
    }

    public int getSueldoPorHoraCentimos() {
        return sueldoPorHoraCentimos;
    }

    public String getCodigoDePromotor() {
        return codigoDePromotor;
    }

    public Integer getComisionPorcentaje() {
        return comisionPorcentaje;
    }

    public int getBonoPorInvitadoCentimos() {
        return bonoPorInvitadoCentimos;
    }
}
