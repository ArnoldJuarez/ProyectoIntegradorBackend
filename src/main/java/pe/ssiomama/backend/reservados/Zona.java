package pe.ssiomama.backend.reservados;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Zona física del local: pista, barra, reservados, terraza — tabla
 * {@code "Zone"}.
 *
 * <p>Antes era un texto suelto repetido en cada mesa. Como tabla propia lleva su
 * aforo parcial y sirve para agrupar los reservados por sitio.
 *
 * <p>Las columnas del plano ({@code x}, {@code y}, {@code width}, {@code depth},
 * {@code height}, {@code hue}) no se mapean todavía: son de la vista 3D, que
 * está aparcada. {@code validate} solo exige que las columnas mapeadas existan.
 */
@Entity
@Table(name = "Zone")
public class Zona {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String nombre;

    /** Aforo de la zona. 0 = no se controla por separado. */
    @Column(name = "capacity", nullable = false)
    private int aforo;

    /** Orden en que se pinta en el plano y en los listados. */
    @Column(name = "sortOrder", nullable = false)
    private int orden;

    protected Zona() {
        // Hibernate necesita un constructor sin argumentos.
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public int getAforo() {
        return aforo;
    }

    public int getOrden() {
        return orden;
    }
}
