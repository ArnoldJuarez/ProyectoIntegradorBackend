package pe.ssiomama.backend.ia;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    /** Las promociones de siempre, tal como salen en los flyers de la casa. */
    static final List<String> PROMOCIONES = List.of(
            "Barra libre para mujeres de 11:00 p. m. a 12:00 a. m.",
            "Sellada de Cartavio a S/ 100",
            "Jarra de sangría a S/ 30",
            "2 Coronitas por S/ 12",
            "Por cumpleaños: box y sellada gratis",
            "Promo «Ven con 5 amigas» para grupos");

    /** La hora de la sala. Sin esto, un servidor en otra zona daría las puertas cinco horas corridas. */
    static final ZoneId ZONA = ZoneId.of("America/Lima");

    private static final Locale ES_PE = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES_PE).withZone(ZONA);
    private static final DateTimeFormatter FECHA_CON_ANIO =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES_PE).withZone(ZONA);
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

    /**
     * «jueves 31 de diciembre de 2026». Para el conserje: sin el año, una noche
     * llamada «Año Nuevo 2027» el 31 de diciembre le parecía de otro año y
     * contestaba que no sabía el precio.
     */
    static String fechaConAnio(Instant instante) {
        return FECHA_CON_ANIO.format(instante);
    }

    static String fechaCorta(Instant instante) {
        return FECHA_CORTA.format(instante).replace(".", "");
    }

    static String hora(Instant instante) {
        return HORA.format(instante);
    }

    /** «jueves 8 de octubre» para un día sin hora. */
    static String fechaLarga(LocalDate dia) {
        return FECHA_LARGA.format(dia.atTime(21, 0).atZone(ZONA));
    }

    /** La noche a la que pertenece un instante: lo que empieza a las 00:30 es de la noche anterior. */
    static LocalDate nocheDe(Instant instante) {
        return instante.atZone(ZONA).minusHours(6).toLocalDate();
    }
}
