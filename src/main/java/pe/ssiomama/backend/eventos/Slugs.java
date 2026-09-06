package pe.ssiomama.backend.eventos;

import java.text.Normalizer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Cómo se convierte «Viernes Urbano · Edición Aniversario» en
 * {@code viernes-urbano-edicion-aniversario}.
 *
 * <p>El slug es la dirección pública de una noche, así que solo puede llevar
 * letras, números y guiones: una tilde o un espacio en la URL se codifica como
 * {@code %C3%B3} y convierte un enlace legible en algo que nadie quiere pegar en
 * Instagram.
 *
 * <p><b>Una vez creado no se cambia</b>, aunque cambie el título. Cambiarlo
 * rompería los enlaces ya compartidos y los que la gente tiene guardados; un
 * título nuevo con la dirección de antes es mucho menos grave que una dirección
 * que da 404.
 */
public final class Slugs {

    /** Tope de longitud. Suficiente para cualquier título y no desborda la URL. */
    private static final int LARGO_MAXIMO = 80;

    /** Los signos diacríticos que deja atrás la normalización NFD. */
    private static final Pattern TILDES = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");

    private static final Pattern GUIONES_DE_LOS_BORDES = Pattern.compile("^-+|-+$");

    /** Hasta dónde se prueba con sufijos antes de rendirse y usar la hora. */
    private static final int INTENTOS_CON_SUFIJO = 100;

    private Slugs() {
        // Solo métodos estáticos.
    }

    /**
     * El slug de un texto.
     *
     * <p>La normalización a NFD separa cada letra acentuada en la letra y su
     * tilde, y así una expresión regular puede quitar solo las tildes. Sin ese
     * paso, «canción» daría {@code canci-n}: la ñ y la ó no son ASCII y se
     * borrarían enteras.
     */
    public static String de(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }

        String sinTildes = TILDES.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD))
                .replaceAll("");

        String limpio = NO_ALFANUMERICO
                .matcher(sinTildes.toLowerCase(java.util.Locale.ROOT))
                .replaceAll("-");

        String recortado = GUIONES_DE_LOS_BORDES.matcher(limpio).replaceAll("");

        return recortado.length() > LARGO_MAXIMO
                ? GUIONES_DE_LOS_BORDES.matcher(recortado.substring(0, LARGO_MAXIMO)).replaceAll("")
                : recortado;
    }

    /**
     * Un slug libre, añadiendo un número si el primero ya está cogido.
     *
     * <p>Dos noches pueden llamarse igual —«Viernes Urbano» se repite cada
     * semana—, así que la segunda es {@code viernes-urbano-2}. Es más útil que
     * pegarle un identificador aleatorio: sigue leyéndose y sigue diciendo de
     * qué es.
     *
     * <p>Si a los cien intentos no hay hueco, se cae a la marca de tiempo. No
     * debería pasar nunca, pero devolver un slug repetido reventaría contra la
     * restricción de unicidad de la base con un error que no explica nada.
     *
     * @param existe cómo preguntar si un candidato ya está en uso
     */
    public static String libre(String titulo, Predicate<String> existe) {
        String raiz = de(titulo);
        if (raiz.isEmpty()) {
            raiz = "evento";
        }
        if (!existe.test(raiz)) {
            return raiz;
        }

        for (int sufijo = 2; sufijo < INTENTOS_CON_SUFIJO; sufijo++) {
            String candidato = raiz + "-" + sufijo;
            if (!existe.test(candidato)) {
                return candidato;
            }
        }
        return raiz + "-" + System.currentTimeMillis();
    }
}
