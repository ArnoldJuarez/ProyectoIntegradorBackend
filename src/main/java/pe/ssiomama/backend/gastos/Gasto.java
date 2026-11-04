package pe.ssiomama.backend.gastos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.eventos.Evento;

/**
 * Dinero que sale — tabla {@code "Gasto"}.
 *
 * <p>Puede ir atado a una noche (el caché del DJ, los vigilantes de esa noche)
 * o a ninguna (la factura de la luz). Los de una noche son los que restan en su
 * margen: el informe post-evento y el resumen del panel los cuentan así.
 */
@Entity
@Table(name = "Gasto")
public class Gasto {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eventoId")
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoriaId", nullable = false)
    private CategoriaDeGasto categoria;

    @Column(name = "importeCentimos", nullable = false)
    private int importeCentimos;

    @Column(name = "descripcion")
    private String descripcion;

    /** Quién lo apuntó. Nulo en lo que vino del sistema anterior. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuarioId")
    private Usuario apuntadoPor;

    @Column(name = "fechaGasto", nullable = false)
    private Instant fecha;

    protected Gasto() {
        // Hibernate necesita un constructor sin argumentos.
    }

    static Gasto apuntar(
            CategoriaDeGasto categoria, Evento evento, int importeCentimos, String descripcion, Instant fecha,
            Usuario quien) {
        Gasto g = new Gasto();
        g.id = UUID.randomUUID().toString();
        g.apuntadoPor = quien;
        g.cambiar(categoria, evento, importeCentimos, descripcion, fecha);
        return g;
    }

    void cambiar(CategoriaDeGasto categoria, Evento evento, int importeCentimos, String descripcion, Instant fecha) {
        this.categoria = categoria;
        this.evento = evento;
        this.importeCentimos = importeCentimos;
        this.descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
        this.fecha = fecha;
    }

    public String getId() {
        return id;
    }

    public Evento getEvento() {
        return evento;
    }

    public CategoriaDeGasto getCategoria() {
        return categoria;
    }

    public int getImporteCentimos() {
        return importeCentimos;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Usuario getApuntadoPor() {
        return apuntadoPor;
    }

    public Instant getFecha() {
        return fecha;
    }
}
