package pe.ssiomama.backend.personal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import pe.ssiomama.backend.auth.Usuario;

/**
 * Alguien que trabaja en la sala — tabla {@code "Employee"}.
 *
 * <p>Es una cosa distinta de {@link Usuario}: el usuario es la cuenta con la que
 * se entra al sistema, y el empleado son sus datos laborales. Están separados
 * porque no todo usuario trabaja aquí —un cliente también tiene cuenta— y porque
 * un contrato tiene fecha de alta y sueldo, que no pintan nada en una cuenta de
 * acceso.
 *
 * <p>La relación es uno a uno: {@code userId} es único en la tabla.
 */
@Entity
@Table(name = "Employee")
public class Empleado {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "userId", nullable = false, unique = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "positionId", nullable = false)
    private PuestoDeTrabajo puesto;

    /** Columna {@code date}: la fecha de alta no tiene hora. */
    @Column(name = "hireDate", nullable = false)
    private LocalDate fechaDeAlta;

    /** Sueldo por hora, en céntimos enteros. */
    @Column(name = "hourlyRateCents", nullable = false)
    private int sueldoPorHoraCentimos;

    @Column(name = "active", nullable = false)
    private boolean activo;

    protected Empleado() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /** El nombre con el que se le conoce, que es el de su cuenta. */
    public String getNombre() {
        return usuario.getNombre();
    }

    public String getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public PuestoDeTrabajo getPuesto() {
        return puesto;
    }

    public LocalDate getFechaDeAlta() {
        return fechaDeAlta;
    }

    public int getSueldoPorHoraCentimos() {
        return sueldoPorHoraCentimos;
    }

    public boolean isActivo() {
        return activo;
    }
}
