package pe.ssiomama.backend.clientes;

/**
 * En qué punto está una campaña. Refleja el enum nativo {@code EstadoCampania}
 * de PostgreSQL.
 *
 * <p>Una campaña nace {@link #BORRADOR} y se queda ahí hasta que alguien
 * decide. Es a propósito: mandar un mensaje a cientos de personas no debería
 * poder ocurrir por pulsar un botón sin querer.
 */
public enum EstadoDeCampania {

    /** Escrita pero sin salir. Es donde nacen todas. */
    BORRADOR,

    /** Con fecha de envío puesta. */
    PROGRAMADO,

    /** Ya salió. */
    ENVIADO,

    /** Descartada antes de salir. */
    CANCELADO;

    public String etiqueta() {
        return switch (this) {
            case BORRADOR -> "Borrador";
            case PROGRAMADO -> "Programada";
            case ENVIADO -> "Enviada";
            case CANCELADO -> "Cancelada";
        };
    }

    /** ¿Se puede todavía cambiar o descartar? */
    public boolean sePuedeTocar() {
        return this == BORRADOR || this == PROGRAMADO;
    }
}
