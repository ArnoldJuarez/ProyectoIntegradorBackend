package pe.ssiomama.backend.entradas;

/**
 * En qué punto está una compra. Refleja el enum nativo {@code OrderStatus} de
 * PostgreSQL.
 *
 * <p>Un pedido nace {@link #PENDING} y ya <b>reserva el cupo</b>: si no lo
 * hiciera, dos personas podrían llegar al pago con la última entrada. Por eso
 * cancelar tiene que devolver ese cupo, y por eso un pendiente no es «nada
 * todavía».
 */
public enum EstadoDePedido {

    /** Creado y con el cupo apartado, pero sin pagar. */
    PENDING,

    /** Pagado. Es cuando se emiten las entradas con su QR. */
    PAID,

    /** Anulado antes de pagar. El cupo vuelve a estar libre. */
    CANCELLED,

    /** Pagado y devuelto después. */
    REFUNDED;

    /** Cómo se llama en pantalla. */
    public String etiqueta() {
        return switch (this) {
            case PENDING -> "Pendiente";
            case PAID -> "Pagado";
            case CANCELLED -> "Cancelado";
            case REFUNDED -> "Devuelto";
        };
    }

    /** ¿Este pedido cuenta como ingreso? Solo lo pagado y no devuelto. */
    public boolean cuentaComoIngreso() {
        return this == PAID;
    }
}
