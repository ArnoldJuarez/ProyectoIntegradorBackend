package pe.ssiomama.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Personal con acceso al sistema — tabla {@code "User"}.
 *
 * <p>Ojo con el nombre: {@code user} es palabra reservada en PostgreSQL. Aquí
 * funciona porque los identificadores van entrecomillados
 * ({@code globally_quoted_identifiers}), que convierte la consulta en
 * {@code select ... from "User"}. Sin las comillas, PostgreSQL entendería la
 * función {@code user} y la consulta fallaría con un error que no menciona la
 * tabla por ninguna parte.
 */
@Entity
@Table(name = "User")
public class Usuario {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /**
     * Formato {@code scrypt:salt:derivada}, hasheado por el sistema anterior.
     * Nunca sale de aquí: no hay getter público que lo exponga en un DTO.
     */
    @Column(name = "passwordHash", nullable = false)
    private String hashDeContrasena;

    @Column(name = "name", nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false)
    private Rol rol;

    /**
     * Una cuenta desactivada no entra, aunque la contraseña sea correcta. Se
     * comprueba también al validar cada petición, no solo al iniciar sesión: si
     * se comprobara únicamente en el login, despedir a alguien no le cerraría la
     * sesión que ya tiene abierta.
     */
    @Column(name = "active", nullable = false)
    private boolean activo;

    @Column(name = "createdAt", nullable = false)
    private Instant creadoEn;

    protected Usuario() {
        // Hibernate necesita un constructor sin argumentos.
    }

    public String getId() {
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
}
