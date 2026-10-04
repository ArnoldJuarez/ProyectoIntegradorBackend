package pe.ssiomama.backend.ia;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Los datos fijos del local y cómo se escriben fechas y dinero en los prompts.
 *
 * <p>Son los mismos que {@code CLUB} en el frontend. Viven aquí también porque
 * el conserje y los agentes los necesitan para contestar, y el backend no puede
 * leer la configuración del frontend.
 */
final class Club {

    static final String NOMBRE = "Ssiomama";
    static final String CIUDAD = "Arequipa";
    static final String DIRECCION = "Av. Dolores 119, José Luis Bustamante y Rivero, Arequipa";
    static final String TELEFONO = "+51 986 678 040";
    static final String HORARIO = "Jueves, viernes, sábado y domingo, de 9:00 p. m. a 2:00 a. m.";
    static final int EDAD_MINIMA = 18;

    /** La hora de la sala. Sin esto, un servidor en otra zona daría las puertas cinco horas corridas. */
    static final ZoneId ZONA = ZoneId.of("America/Lima");

    private static final Locale ES_PE = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES_PE).withZone(ZONA);
    private static final DateTimeFormatter FECHA_CORTA =
            DateTimeFormatter.ofPattern("d MMM", ES_PE).withZone(ZONA);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONA);

    private Club() {}

    static String soles(long centimos) {
        NumberFormat f = NumberFormat.getNumberInstance(ES_PE);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "S/ " + f.format(centimos / 100.0);
    }

    static String fechaLarga(Instant instante) {
        return FECHA_LARGA.format(instante);
    }

    static String fechaCorta(Instant instante) {
        return FECHA_CORTA.format(instante).replace(".", "");
    }

    static String hora(Instant instante) {
        return HORA.format(instante);
    }
}
