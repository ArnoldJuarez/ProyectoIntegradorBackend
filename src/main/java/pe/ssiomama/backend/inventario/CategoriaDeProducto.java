package pe.ssiomama.backend.inventario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Categoría de producto — tabla {@code "ProductCategory"}.
 *
 * <p>Los nombres van escritos tal cual están en la base, con su mayúscula
 * inicial y su camelCase. Prisma crea los identificadores así, y en PostgreSQL
 * un identificador sin comillas se pliega a minúsculas: sin
 * {@code globally_quoted_identifiers} activado (ver {@code application.yml}),
 * Hibernate buscaría una tabla {@code productcategory} que no existe.
 */
@Entity
@Table(name = "ProductCategory")
public class CategoriaDeProducto {

    /**
     * Lo genera Prisma con {@code cuid()} desde la aplicación, no la base: no hay
     * secuencia ni {@code DEFAULT} detrás, así que aquí el id es asignado y no
     * lleva {@code @GeneratedValue}.
     */
    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "name", nullable = false, unique = true)
    private String nombre;

    protected CategoriaDeProducto() {
        // Hibernate necesita un constructor sin argumentos.
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }
}
