package pe.ssiomama.backend.eventos;

/**
 * Estado de una noche. Refleja el enum nativo {@code EstadoEvento} de PostgreSQL.
 *
 * <p>Solo {@link #PUBLICADO} sale a la web pública: un borrador con el cartel a
 * medias no debe aparecer en la agenda ni admitir compras.
 */
public enum EstadoDeEvento {
    /** Se está preparando. No sale a la web. */
    BORRADOR,
    /** A la venta. */
    PUBLICADO,
    /** Agotado: sigue visible, pero sin entradas. */
    AGOTADO,
    /** Cancelado. */
    CANCELADO,
    /** Ya pasó. */
    CERRADO
}
