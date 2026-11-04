package pe.ssiomama.backend.ia;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Las fechas que mueven gente en Arequipa: feriados nacionales, el aniversario
 * de la ciudad y las noches de fiesta que no son feriado (Halloween, San Juan).
 *
 * <p>Para una discoteca, lo que cuenta es la <b>víspera</b>: la noche antes de
 * un feriado se llena como un sábado aunque caiga en miércoles. El programador
 * de noches lo sabe porque se lo dice esta lista, no porque el modelo se acuerde
 * del calendario peruano —que se lo inventaría—.
 */
final class CalendarioPeru {

    record Fecha(LocalDate dia, String nombre, boolean feriado) {}

    /** Feriados nacionales de fecha fija (Ley 31186 y anteriores). */
    private static final Map<MonthDay, String> FERIADOS_FIJOS = Map.ofEntries(
            Map.entry(MonthDay.of(1, 1), "Año Nuevo"),
            Map.entry(MonthDay.of(5, 1), "Día del Trabajo"),
            Map.entry(MonthDay.of(6, 7), "Día de la Bandera"),
            Map.entry(MonthDay.of(6, 29), "San Pedro y San Pablo"),
            Map.entry(MonthDay.of(7, 23), "Día de la Fuerza Aérea"),
            Map.entry(MonthDay.of(7, 28), "Fiestas Patrias"),
            Map.entry(MonthDay.of(7, 29), "Fiestas Patrias"),
            Map.entry(MonthDay.of(8, 6), "Batalla de Junín"),
            Map.entry(MonthDay.of(8, 30), "Santa Rosa de Lima"),
            Map.entry(MonthDay.of(10, 8), "Combate de Angamos"),
            Map.entry(MonthDay.of(11, 1), "Todos los Santos"),
            Map.entry(MonthDay.of(12, 8), "Inmaculada Concepción"),
            Map.entry(MonthDay.of(12, 9), "Batalla de Ayacucho"),
            Map.entry(MonthDay.of(12, 25), "Navidad"));

    /** Noches de fiesta que no son feriado, más el aniversario de la ciudad. */
    private static final Map<MonthDay, String> FECHAS_DE_FIESTA = Map.ofEntries(
            Map.entry(MonthDay.of(2, 14), "San Valentín"),
            Map.entry(MonthDay.of(6, 24), "San Juan e Inti Raymi"),
            Map.entry(MonthDay.of(8, 15), "Aniversario de Arequipa"),
            Map.entry(MonthDay.of(10, 31), "Halloween y Día de la Canción Criolla"),
            Map.entry(MonthDay.of(12, 31), "Fin de año"));

    private CalendarioPeru() {}

    /** Las fechas señaladas entre dos días, ambos incluidos, en orden. */
    static List<Fecha> entre(LocalDate desde, LocalDate hasta) {
        List<Fecha> fechas = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            MonthDay md = MonthDay.from(d);
            if (FERIADOS_FIJOS.containsKey(md)) fechas.add(new Fecha(d, FERIADOS_FIJOS.get(md), true));
            if (FECHAS_DE_FIESTA.containsKey(md)) fechas.add(new Fecha(d, FECHAS_DE_FIESTA.get(md), false));
        }
        for (int anio = desde.getYear(); anio <= hasta.getYear(); anio++) {
            LocalDate pascua = domingoDePascua(anio);
            for (Fecha santa : List.of(
                    new Fecha(pascua.minusDays(3), "Jueves Santo", true),
                    new Fecha(pascua.minusDays(2), "Viernes Santo", true))) {
                if (!santa.dia().isBefore(desde) && !santa.dia().isAfter(hasta)) fechas.add(santa);
            }
        }
        fechas.sort(Comparator.comparing(Fecha::dia));
        return fechas;
    }

    /** ¿Es feriado nacional? */
    static boolean esFeriado(LocalDate dia) {
        return entre(dia, dia).stream().anyMatch(Fecha::feriado);
    }

    /** Algoritmo anónimo gregoriano (Meeus/Jones/Butcher). */
    static LocalDate domingoDePascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anio, mes, dia);
    }
}
