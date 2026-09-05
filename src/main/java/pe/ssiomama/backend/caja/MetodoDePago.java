package pe.ssiomama.backend.caja;

/**
 * Cómo se pagó. Refleja el enum nativo {@code PaymentMethod} de PostgreSQL.
 *
 * <p>Yape y Plin son billeteras móviles peruanas, y van separadas de
 * {@code TRANSFER} a propósito: en la práctica son pagos instantáneos por QR,
 * no transferencias bancarias, y la casa las concilia por caminos distintos.
 *
 * <p>Solo {@link #CASH} llega físicamente al cajón, y por eso es el único que
 * cuenta para el descuadre de un arqueo.
 */
public enum MetodoDePago {
    CASH,
    CARD,
    YAPE,
    PLIN,
    TRANSFER;

    /** Cómo se llama en pantalla. */
    public String etiqueta() {
        return switch (this) {
            case CASH -> "Efectivo";
            case CARD -> "Tarjeta";
            case YAPE -> "Yape";
            case PLIN -> "Plin";
            case TRANSFER -> "Transferencia";
        };
    }

    /** ¿Este pago acaba en el cajón? Es lo que se cuenta al cerrar la caja. */
    public boolean esEfectivo() {
        return this == CASH;
    }
}
