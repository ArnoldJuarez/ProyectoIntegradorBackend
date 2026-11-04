package pe.ssiomama.backend.gastos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A qué se va el dinero: caché de artista, seguridad, limpieza, APDAYC…
 * — tabla {@code "CategoriaGasto"}.
 *
 * <p>Es una tabla y no un enum porque la lista la decide el negocio: el día
 * que se contrate un fotógrafo fijo, se añade «Fotografía» sin tocar código.
 */
@Entity
@Table(name = "CategoriaGasto")
public class CategoriaDeGasto {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Una categoría retirada no se ofrece al apuntar, pero sus gastos siguen contando. */
    @Column(name = "activo", nullable = false)
    private boolean activa;

    protected CategoriaDeGasto() {
        // Hibernate necesita un constructor sin argumentos.
    }

    static CategoriaDeGasto crear(String nombre) {
        CategoriaDeGasto c = new CategoriaDeGasto();
        c.id = UUID.randomUUID().toString();
        c.nombre = nombre.trim();
        c.activa = true;
        return c;
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isActiva() {
        return activa;
    }
}
