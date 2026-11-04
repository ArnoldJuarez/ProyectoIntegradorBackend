package pe.ssiomama.backend.inventario;

/**
 * En qué punto está un pedido a proveedor — enum {@code EstadoOrdenCompra} de
 * PostgreSQL.
 *
 * <pre>
 *   BORRADOR ──enviar──▶ ENVIADO ──recibir──▶ RECIBIDO
 *      │                    │
 *      └──────cancelar──────┴──────────▶ CANCELADO
 * </pre>
 *
 * <p>Solo un borrador se edita: un pedido enviado es lo que el proveedor tiene
 * en la mano, y cambiarlo aquí no lo cambia allí. Recibir es lo que mete la
 * mercancía en el almacén.
 */
public enum EstadoDeOrden {
    BORRADOR,
    ENVIADO,
    RECIBIDO,
    CANCELADO
}
