package pe.ssiomama.backend.inventario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Producto de barra y almacén — tabla {@code "Product"}.
 *
 * <p>Primera entidad de la migración a propósito: el inventario es el dominio
 * más aislado del sistema. No entra en ninguna transacción serializable, así que
 * si el mapeo contra el esquema de Prisma tiene algún fallo, se descubre aquí y
 * no en la venta de entradas.
 *
 * <h2>El dinero, en céntimos enteros</h2>
 *
 * <p>{@code costCents} y {@code priceCents} son {@code int}, nunca {@code
 * double} ni {@code float}. Es la misma regla que sigue el resto del sistema y
 * la razón por la que todos los campos monetarios acaban en {@code Cents}: con
 * coma flotante, sumar cien consumiciones de 3,30 no da 330,00.
 */
@Entity
@Table(name = "Product")
public class Producto {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @Column(name = "name", nullable = false)
    private String nombre;

    /**
     * La categoría se carga en diferido: casi ninguna pantalla del inventario la
     * necesita, y traerla siempre serían diez consultas de más al listar.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoryId", nullable = false)
    private CategoriaDeProducto categoria;

    /**
     * {@code unit} no es texto en la base: es el tipo enum <b>nativo</b> de
     * PostgreSQL {@code ProductUnit}.
     *
     * <p>Con solo {@code @Enumerated(STRING)} Hibernate manda un {@code varchar}
     * y PostgreSQL rechaza la escritura, porque no convierte texto a enum por su
     * cuenta. {@code NAMED_ENUM} es lo que le dice que al otro lado hay un tipo
     * con nombre y que lo trate como tal.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "unit", nullable = false)
    private UnidadDeProducto unidad;

    @Column(name = "costCents", nullable = false)
    private int costeCentimos;

    @Column(name = "priceCents", nullable = false)
    private int precioCentimos;

    /** Por debajo de esta cantidad, la pantalla de inventario avisa de reponer. */
    @Column(name = "stockAlert", nullable = false)
    private int alertaDeStock;

    @Column(name = "active", nullable = false)
    private boolean activo;

    protected Producto() {
        // Hibernate necesita un constructor sin argumentos.
    }

    public String getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getNombre() {
        return nombre;
    }

    public CategoriaDeProducto getCategoria() {
        return categoria;
    }

    public UnidadDeProducto getUnidad() {
        return unidad;
    }

    public int getCosteCentimos() {
        return costeCentimos;
    }

    public int getPrecioCentimos() {
        return precioCentimos;
    }

    public int getAlertaDeStock() {
        return alertaDeStock;
    }

    public boolean isActivo() {
        return activo;
    }
}
