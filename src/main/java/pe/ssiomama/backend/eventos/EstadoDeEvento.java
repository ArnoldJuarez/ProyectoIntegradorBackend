package pe.ssiomama.backend.eventos;

/**
 * Estado de una noche. Refleja el enum nativo {@code EventStatus} de PostgreSQL.
 *
 * <p>Solo {@link #PUBLISHED} sale a la web pública: un borrador con el cartel a
 * medias no debe aparecer en la agenda ni admitir compras.
 */
public enum EstadoDeEvento {
    /** Se está preparando. No sale a la web. */
    DRAFT,
    /** A la venta. */
    PUBLISHED,
    /** Agotado: sigue visible, pero sin entradas. */
    SOLD_OUT,
    /** Cancelado. */
    CANCELLED,
    /** Ya pasó. */
    FINISHED
}
