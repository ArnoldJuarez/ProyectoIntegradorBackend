package pe.ssiomama.backend.entradas;

import java.time.Instant;

/**
 * Lo que el escáner responde al leer un QR.
 *
 * <p>Un rechazo <b>no</b> es un error HTTP: la petición se atendió bien, la
 * respuesta es «no pasa». Devolver 400 o 403 obligaría al frontend a leer el
 * mensaje del cuerpo de error igualmente, y confundiría un problema de la
 * aplicación con una entrada caducada.
 *
 * <p>Por eso {@link #motivo} viaja aparte de {@link #mensaje}: el portero lee el
 * texto, pero la pantalla decide el color y el sonido con el motivo. Enseñar
 * «REPETIDA» en ámbar y «FALSA» en rojo cambia cómo reacciona quien está en la
 * puerta.
 *
 * @param titular a quién pertenece, para cotejar con el DNI. Va también en los
 *     rechazos que lo tienen: es lo primero que se pregunta al discutir.
 */
public record ResultadoDeEscaneo(
        boolean ok,
        MotivoDeRechazo motivo,
        String mensaje,
        String titular,
        String tipoDeEntrada,
        int consumicionesIncluidas,
        String evento,
        Instant escaneadoEn) {

    /** Por qué no pasa. Cada uno se atiende de una manera distinta. */
    public enum MotivoDeRechazo {
        /** La firma no cuadra: el código está inventado o manipulado. */
        FALSA,
        /** La firma cuadra pero no hay tal entrada. Base incoherente o secreto cambiado. */
        NO_EXISTE,
        /** Anulada por la casa o devuelta al cliente. */
        ANULADA,
        /** Ya se escaneó antes. O se coló, o es una copia del QR. */
        REPETIDA,
        /** Es de otra noche. La más común, y la que más se discute. */
        OTRA_FECHA;

        public String etiqueta() {
            return switch (this) {
                case FALSA -> "Falsa";
                case NO_EXISTE -> "No existe";
                case ANULADA -> "Anulada";
                case REPETIDA -> "Repetida";
                case OTRA_FECHA -> "Otra fecha";
            };
        }
    }

    /** Pasa. Lleva lo que el portero necesita decir en voz alta. */
    static ResultadoDeEscaneo adelante(Entrada entrada, Instant cuando) {
        return new ResultadoDeEscaneo(
                true,
                null,
                "Adelante.",
                entrada.getTitular(),
                entrada.getTipoDeEntrada().getNombre(),
                entrada.getTipoDeEntrada().getConsumicionesIncluidas(),
                entrada.getEvento().getTitulo(),
                cuando);
    }

    /** No pasa, y no hay entrada que enseñar. */
    static ResultadoDeEscaneo rechazo(MotivoDeRechazo motivo, String mensaje) {
        return new ResultadoDeEscaneo(false, motivo, mensaje, null, null, 0, null, null);
    }

    /** No pasa, pero la entrada existe: se enseña de quién es y de qué noche. */
    static ResultadoDeEscaneo rechazo(
            MotivoDeRechazo motivo, String mensaje, Entrada entrada) {
        return new ResultadoDeEscaneo(
                false,
                motivo,
                mensaje,
                entrada.getTitular(),
                entrada.getTipoDeEntrada().getNombre(),
                entrada.getTipoDeEntrada().getConsumicionesIncluidas(),
                entrada.getEvento().getTitulo(),
                null);
    }
}
