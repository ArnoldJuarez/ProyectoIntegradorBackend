package pe.ssiomama.backend.inventario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
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
@Table(name = "CategoriaProducto")
public class CategoriaDeProducto {

    /** Lo numera la base: columna identity (1, 2, 3…). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "nombre", nullable = false, unique = true)
    private String nombre;

    protected CategoriaDeProducto() {
        // Hibernate necesita un constructor sin argumentos.
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }
}
