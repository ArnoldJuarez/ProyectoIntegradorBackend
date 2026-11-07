package pe.ssiomama.backend.local;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Table;

/**
 * Un extintor, un botiquín, una zona segura o el punto de reunión — tabla
 * {@code "ElementoSeguridad"}. Un punto con tipo en la planta que toque.
 */
@Entity
@Table(name = "ElementoSeguridad")
public class ElementoDeSeguridad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "salaId", nullable = false)
    private Long salaId;

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

    static ElementoDeSeguridad nuevo(Long salaId) {
        ElementoDeSeguridad e = new ElementoDeSeguridad();
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

    public Long getId() {
        return id;
    }

    public Long getSalaId() {
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
