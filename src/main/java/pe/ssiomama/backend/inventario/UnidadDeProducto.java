package pe.ssiomama.backend.inventario;

/**
 * Unidad en la que se cuenta y se pide un producto.
 *
 * <p>Refleja el tipo enum nativo {@code ProductUnit} de PostgreSQL. Los nombres
 * tienen que coincidir <b>exactamente</b> con las etiquetas del tipo en la base
 * ({@code UNIDAD, BOTELLA, CAJA, LITRO}): PostgreSQL no acepta un valor que no
 * esté en la lista, así que renombrar una constante aquí rompe las escrituras.
 */
public enum UnidadDeProducto {
    UNIDAD,
    BOTELLA,
    CAJA,
    LITRO
}
