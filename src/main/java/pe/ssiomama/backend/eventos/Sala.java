package pe.ssiomama.backend.eventos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Table;

/**
 * Sala donde se celebra la noche — tabla {@code "Sala"}.
 *
 * <p>Las medidas del plano ({@code planoAncho}, {@code planoFondo}) son las de
 * la pantalla del local. 0 = todavía no se ha dibujado.
 */
@Entity
@Table(name = "Sala")
public class Sala {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "aforo", nullable = false)
    private int aforo;

    @Column(name = "descripcion")
    private String descripcion;

    /** Medidas del local en metros. */
    @Column(name = "planoAncho", nullable = false)
    private double planoAncho;

    @Column(name = "planoFondo", nullable = false)
    private double planoFondo;

    protected Sala() {
        // Hibernate necesita un constructor sin argumentos.
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public int getAforo() {
        return aforo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public double getPlanoAncho() {
        return planoAncho;
    }

    public double getPlanoFondo() {
        return planoFondo;
    }

    public void medir(double ancho, double fondo) {
        this.planoAncho = ancho;
        this.planoFondo = fondo;
    }
}
