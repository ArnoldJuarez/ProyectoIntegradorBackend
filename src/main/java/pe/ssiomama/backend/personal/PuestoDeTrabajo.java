package pe.ssiomama.backend.personal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Catálogo de puestos del personal — tabla {@code "StaffPosition"}.
 *
 * <p>Antes el puesto se escribía a mano en dos sitios distintos y nada
 * garantizaba que «Barra principal» se escribiera igual las dos veces. Como
 * tabla propia, el nombre es único y los turnos apuntan al mismo sitio.
 */
@Entity
@Table(name = "StaffPosition")
public class PuestoDeTrabajo {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "name", nullable = false, unique = true)
    private String nombre;

    /** Puestos que atienden la barra. De ellos salen los turnos de venta. */
    @Column(name = "isBar", nullable = false)
    private boolean esDeBarra;

    @Column(name = "active", nullable = false)
    private boolean activo;

    protected PuestoDeTrabajo() {
        // Hibernate necesita un constructor sin argumentos.
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isEsDeBarra() {
        return esDeBarra;
    }

    public boolean isActivo() {
        return activo;
    }
}
