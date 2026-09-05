package pe.ssiomama.backend.reservados;

/**
 * Estado de una solicitud de reservado. Refleja el enum nativo
 * {@code ReservationStatus} de PostgreSQL.
 *
 * <p>Una solicitud de la web nace siempre en {@link #REQUESTED}: la mesa no
 * queda apartada hasta que alguien llama, confirma y acuerda el depósito.
 */
public enum EstadoDeReserva {
    /** Pedida por la web. Todavía no vale para entrar. */
    REQUESTED,
    /** Confirmada por teléfono, con su depósito acordado. */
    CONFIRMED,
    /** Se sentaron. */
    SEATED,
    /** Confirmada y no vinieron. */
    NO_SHOW,
    CANCELLED
}
