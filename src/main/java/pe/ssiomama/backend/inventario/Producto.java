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
@Table(name = "Producto")
public class Producto {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /**
     * La categoría se carga en diferido: casi ninguna pantalla del inventario la
     * necesita, y traerla siempre serían diez consultas de más al listar.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoriaId", nullable = false)
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
    @Column(name = "unidad", nullable = false)
    private UnidadDeProducto unidad;

    @Column(name = "costeCentimos", nullable = false)
    private int costeCentimos;

    @Column(name = "precioCentimos", nullable = false)
    private int precioCentimos;

    /** Por debajo de esta cantidad, la pantalla de inventario avisa de reponer. */
    @Column(name = "alertaStock", nullable = false)
    private int alertaDeStock;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    /**
     * Cuántas unidades quedan.
     *
     * <p>Antes esto era una tabla aparte, {@code StockItem}, con el saldo por
     * producto <b>y por sala</b>. Al colapsarse en esta columna, el stock deja
     * de distinguir salas: hay una sola cifra por producto.
     *
     * <p>Es una simplificación razonable mientras solo haya un local. El día que
     * haya dos, habrá que volver a separarlo — los movimientos sí siguen
     * guardando de qué sala salieron.
     *
     * <p>Es un saldo cacheado, no un {@code sum()} sobre los movimientos: la
     * pantalla de almacén lo consulta constantemente y recorrer meses de
     * historial en cada carga no sale a cuenta. El precio es que solo debería
     * cambiarlo quien registra el movimiento, nunca a mano.
     */
    @Column(name = "stock", nullable = false)
    private int stock;

    protected Producto() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /**
     * ¿Hay que reponer?
     *
     * <p>Compara contra el umbral que lleva cada producto, no contra un número
     * fijo: no se repone igual una caja de cerveza que una botella de whisky.
     */
    public boolean necesitaReposicion() {
        return stock <= alertaDeStock;
    }

    /** Cuánto falta para volver al umbral. 0 si no hace falta reponer. */
    public int faltanParaElUmbral() {
        return Math.max(alertaDeStock - stock, 0);
    }

    /**
     * Lo que vale lo que hay guardado, en céntimos.
     *
     * <p>Se valora a coste, no a precio de venta: es lo que se ha pagado por
     * ello, no lo que se espera cobrar.
     */
    public long valorACosteCentimos() {
        return (long) stock * costeCentimos;
    }

    /**
     * Descuenta unidades al venderlas en barra.
     *
     * <p><b>Deja el stock en negativo y no lo impide.</b> Es deliberado: a las
     * dos de la mañana la botella está físicamente ahí aunque el sistema crea
     * que no —porque alguien no registró la última compra—, y negarse a cobrar
     * por un descuadre de papel es perder dinero de verdad. El negativo queda a
     * la vista en la pantalla de almacén, que es donde se arregla.
     *
     * <p>El movimiento que explica la bajada lo crea quien llama, no esto: una
     * entidad no debería decidir qué rastro deja otra tabla.
     */
    public void descontarStock(int unidades) {
        this.stock -= unidades;
    }

    /** Repone existencias: una compra a proveedor, o un ajuste al alza. */
    public void reponerStock(int unidades) {
        this.stock += unidades;
    }

    public int getStock() {
        return stock;
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
