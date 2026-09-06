package pe.ssiomama.backend.inventario;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * El código de almacén de un producto: {@code CERVEZA-CUSQUENA-K7P2}.
 *
 * <h2>Por qué se genera y no se teclea</h2>
 *
 * <p>Es un campo único, y pedirlo en el formulario garantizaba dos cosas: que
 * alguien lo escribiera mal y que chocara con otro. A las once de la noche,
 * dando de alta una marca nueva, nadie quiere inventarse un código.
 *
 * <p>El sufijo corto es lo que evita el choque entre «Cerveza Pilsen» y «Cerveza
 * Pilsen 650», que producen la misma raíz una vez recortada.
 */
public final class Skus {

    /** Cuánto del nombre se conserva. Lo justo para reconocerlo de un vistazo. */
    private static final int LARGO_DE_LA_RAIZ = 18;

    /** Cuántos caracteres al azar lleva el sufijo. */
    private static final int LARGO_DEL_SUFIJO = 4;

    /**
     * Sin caracteres que se confundan al dictarlos o al teclearlos de un albarán:
     * fuera la O y el 0, la I y el 1, la S y el 5.
     */
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRTUVWXYZ2346789";

    private static final Pattern TILDES = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^A-Z0-9]+");

    private static final Pattern GUIONES_DE_LOS_BORDES = Pattern.compile("^-+|-+$");

    /** Cuántas veces se prueba con otro sufijo antes de rendirse. */
    private static final int INTENTOS = 20;

    private static final SecureRandom AZAR = new SecureRandom();

    private Skus() {
        // Solo métodos estáticos.
    }

    /**
     * Un SKU libre para ese nombre.
     *
     * <p>El sufijo es aleatorio, así que en la práctica no choca nunca; aun así
     * se comprueba, porque un choque aquí reventaría contra la restricción de
     * unicidad de la base con un error que no explica nada a quien está dando de
     * alta un producto.
     *
     * @param existe cómo preguntar si un candidato ya está en uso
     */
    public static String libre(String nombre, Predicate<String> existe) {
        String raiz = raizDe(nombre);

        for (int intento = 0; intento < INTENTOS; intento++) {
            String candidato = raiz + "-" + sufijo();
            if (!existe.test(candidato)) {
                return candidato;
            }
        }
        // Rendición razonada: la hora en milisegundos no puede repetirse dentro
        // del mismo proceso, así que corta la racha aunque quede feo.
        return raiz + "-" + System.currentTimeMillis();
    }

    /** La parte legible: el nombre en mayúsculas, sin tildes y recortado. */
    private static String raizDe(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "PRODUCTO";
        }

        String sinTildes = TILDES.matcher(Normalizer.normalize(nombre, Normalizer.Form.NFD))
                .replaceAll("");

        String limpio = NO_ALFANUMERICO
                .matcher(sinTildes.toUpperCase(Locale.ROOT))
                .replaceAll("-");

        String recortado = GUIONES_DE_LOS_BORDES.matcher(limpio).replaceAll("");
        if (recortado.length() > LARGO_DE_LA_RAIZ) {
            recortado = GUIONES_DE_LOS_BORDES
                    .matcher(recortado.substring(0, LARGO_DE_LA_RAIZ))
                    .replaceAll("");
        }

        return recortado.isEmpty() ? "PRODUCTO" : recortado;
    }

    private static String sufijo() {
        StringBuilder s = new StringBuilder(LARGO_DEL_SUFIJO);
        for (int i = 0; i < LARGO_DEL_SUFIJO; i++) {
            s.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return s.toString();
    }
}
