package pe.ssiomama.backend.entradas;

/**
 * Si una entrada sirve para pasar. Refleja el enum nativo {@code TicketStatus}
 * de PostgreSQL.
 *
 * <p>Es lo que consulta el escáner de la puerta, así que solo hay una pregunta
 * que importa: {@link #VALID} pasa, el resto no. Y la diferencia entre los tres
 * que no pasan no es cosmética: al portero hay que decirle <b>por qué</b>, o
 * discutirá con quien tiene delante sin argumentos.
 */
public enum EstadoDeEntrada {

    /** Emitida y sin usar. Es la única que deja pasar. */
    VALID,

    /** Ya se escaneó. Un segundo intento es un intento de colarse o un error. */
    USED,

    /** Anulada por la casa. */
    VOID,

    /** Devuelta al cliente. */
    REFUNDED;

    public String etiqueta() {
        return switch (this) {
            case VALID -> "Válida";
            case USED -> "Usada";
            case VOID -> "Anulada";
            case REFUNDED -> "Devuelta";
        };
    }

    /** ¿Deja pasar por la puerta? */
    public boolean dejaPasar() {
        return this == VALID;
    }
}
