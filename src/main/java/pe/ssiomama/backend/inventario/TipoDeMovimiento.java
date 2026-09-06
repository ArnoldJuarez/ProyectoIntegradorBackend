package pe.ssiomama.backend.inventario;

/**
 * Por qué se movió el stock. Refleja el tipo enum nativo
 * {@code StockMovementType} de PostgreSQL.
 *
 * <h2>El signo NO está aquí, está en la cantidad</h2>
 *
 * <p>Comprobado sobre los datos reales: las compras se guardan con cantidad
 * positiva (de +9 a +43) y las ventas con cantidad negativa (de −3 a −1). Es la
 * convención que dejó el sistema anterior.
 *
 * <p>Se documenta porque la intuición lleva a lo contrario —guardar todo en
 * positivo y deducir el signo del tipo—, y hacerlo así invierte el signo dos
 * veces: una venta acabaría <b>sumando</b> existencias. Este enum solo dice el
 * motivo del movimiento; cuánto sumó o restó lo dice la cantidad.
 *
 * <p>{@code ADJUSTMENT} es la prueba de que tiene que ser así: una corrección
 * tras un recuento puede ir en los dos sentidos, y con el signo en el tipo no
 * habría forma de expresarla.
 */
public enum TipoDeMovimiento {

    /** Llegó mercancía del proveedor. */
    COMPRA,

    /** Se vendió en barra. */
    VENTA,

    /** Rotura, derrame o caducidad. */
    MERMA,

    /** Corrección tras un recuento. Puede ir en los dos sentidos. */
    AJUSTE,

    /** Paso de una sala a otra. */
    TRANSFERENCIA;

    /** Cómo se llama en pantalla. */
    public String etiqueta() {
        return switch (this) {
            case COMPRA -> "Compra";
            case VENTA -> "Venta";
            case MERMA -> "Merma";
            case AJUSTE -> "Ajuste";
            case TRANSFERENCIA -> "Traspaso";
        };
    }
}
