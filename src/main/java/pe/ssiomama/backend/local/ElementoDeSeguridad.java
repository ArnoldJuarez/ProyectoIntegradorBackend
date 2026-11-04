package pe.ssiomama.backend.local;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Un extintor, un botiquín, una zona segura o el punto de reunión — tabla
 * {@code "ElementoSeguridad"}. Un punto con tipo en la planta que toque.
 */
@Entity
@Table(name = "ElementoSeguridad")
public class ElementoDeSeguridad {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "salaId", nullable = false)
    private String salaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoDeElemento tipo;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "piso", nullable = false)
    private int piso;

    @Column(name = "x", nullable = false)
    private double x;

    @Column(name = "y", nullable = false)
    private double y;

    protected ElementoDeSeguridad() {
        // Hibernate necesita un constructor sin argumentos.
    }

    static ElementoDeSeguridad nuevo(String salaId) {
        ElementoDeSeguridad e = new ElementoDeSeguridad();
        e.id = UUID.randomUUID().toString();
        e.salaId = salaId;
        return e;
    }

    void colocar(TipoDeElemento tipo, String nombre, int piso, double x, double y) {
        this.tipo = tipo;
        this.nombre = nombre == null || nombre.isBlank() ? tipo.name() : nombre.trim();
        this.piso = piso;
        this.x = x;
        this.y = y;
    }

    public String getId() {
        return id;
    }

    public String getSalaId() {
        return salaId;
    }

    public TipoDeElemento getTipo() {
        return tipo;
    }

    public String getNombre() {
        return nombre;
    }

    public int getPiso() {
        return piso;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }
}
