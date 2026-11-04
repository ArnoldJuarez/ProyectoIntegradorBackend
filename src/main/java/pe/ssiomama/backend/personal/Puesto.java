package pe.ssiomama.backend.personal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Catálogo de puestos del personal — tabla {@code "Puesto"}.
 *
 * <p>Antes el puesto se escribía a mano en dos sitios distintos y nada
 * garantizaba que «Barra principal» se escribiera igual las dos veces. Como
 * tabla propia, el nombre es único y los turnos apuntan al mismo sitio.
 *
 * <p>Quien ocupa cada puesto está en {@code Usuario.puestoId}: la antigua tabla
 * {@code Employee} se fusionó ahí.
 */
@Entity
@Table(name = "Puesto")
public class Puesto {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "nombre", nullable = false, unique = true)
    private String nombre;

    /** Puestos que atienden la barra. De ellos salen los turnos de venta. */
    @Column(name = "esBarra", nullable = false)
    private boolean esDeBarra;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Puesto() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /**
     * Una barra nueva del local: la de la terraza, la del VIP.
     *
     * <p>Cada barra es un puesto porque es justo eso: un sitio donde alguien
     * trabaja un turno. Y así sus ventas se pueden separar de las de otra barra
     * la misma noche.
     */
    public static Puesto nuevaBarra(String nombre) {
        Puesto p = new Puesto();
        p.id = java.util.UUID.randomUUID().toString();
        p.nombre = nombre.trim();
        p.esDeBarra = true;
        p.activo = true;
        return p;
    }

    /** Una barra que ya no se monta. No se borra: sus turnos viejos la siguen nombrando. */
    public void cambiarActivo(boolean activo) {
        this.activo = activo;
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
