package pe.ssiomama.backend.eventos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Sala donde se celebra la noche — tabla {@code "Sala"}.
 *
 * <p>De momento solo lo que necesita el listado de eventos. Las medidas del
 * plano ({@code planoAncho}, {@code planoFondo}) y las zonas llegarán con la
 * pantalla del local; mapear columnas que nadie lee solo añade sitios donde
 * equivocarse.
 */
@Entity
@Table(name = "Sala")
public class Sala {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "aforo", nullable = false)
    private int aforo;

    protected Sala() {
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
}
