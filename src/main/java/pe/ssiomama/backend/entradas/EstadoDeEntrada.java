package pe.ssiomama.backend.entradas;

/**
 * Si una entrada sirve para pasar. Refleja el enum nativo {@code TicketStatus}
 * de PostgreSQL.
 *
 * <p>Es lo que consulta el escáner de la puerta, así que solo hay una pregunta
 * que importa: {@link #VALIDA} pasa, el resto no. Y la diferencia entre los tres
 * que no pasan no es cosmética: al portero hay que decirle <b>por qué</b>, o
 * discutirá con quien tiene delante sin argumentos.
 */
public enum EstadoDeEntrada {

    /** Emitida y sin usar. Es la única que deja pasar. */
    VALIDA,

    /** Ya se escaneó. Un segundo intento es un intento de colarse o un error. */
    USADA,

    /** Anulada por la casa. */
    ANULADA,

    /** Devuelta al cliente. */
    DEVUELTO;

    public String etiqueta() {
        return switch (this) {
            case VALIDA -> "Válida";
            case USADA -> "Usada";
            case ANULADA -> "Anulada";
            case DEVUELTO -> "Devuelta";
        };
    }

    /** ¿Deja pasar por la puerta? */
    public boolean dejaPasar() {
        return this == VALIDA;
    }
}
