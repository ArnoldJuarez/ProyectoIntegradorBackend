package pe.ssiomama.backend.reservados;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Un reservado del local — tabla {@code "VipTable"}. */
@Entity
@Table(name = "Box")
public class MesaVip {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    /**
     * En qué zona está. Puede no tener: hay mesas sueltas que no pertenecen a
     * ninguna zona con nombre.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zonaId")
    private Zona zona;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Cuánta gente cabe. Es el tope de una solicitud. */
    @Column(name = "plazas", nullable = false)
    private int plazas;

    /**
     * Consumo mínimo, en céntimos. 0 = no lo tiene.
     *
     * <p>Sale a la web: es lo primero que pregunta quien va a reservar, y no
     * decirlo hasta la llamada es la forma más rápida de que cuelguen.
     */
    @Column(name = "consumoMinimoCentimos", nullable = false)
    private int consumoMinimoCentimos;

    @Column(name = "activo", nullable = false)
    private boolean activa;

    protected MesaVip() {
        // Hibernate necesita un constructor sin argumentos.
    }

    public String getId() {
        return id;
    }

    public Zona getZona() {
        return zona;
    }

    public String getNombre() {
        return nombre;
    }

    public int getPlazas() {
        return plazas;
    }

    public int getConsumoMinimoCentimos() {
        return consumoMinimoCentimos;
    }

    public boolean isActiva() {
        return activa;
    }
}
