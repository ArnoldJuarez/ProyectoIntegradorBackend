package pe.ssiomama.backend.entradas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Firma y verificación de los códigos QR de las entradas.
 *
 * <p>Es el port de {@code lib/tickets.ts}, y es el trozo de la migración donde
 * un detalle de codificación cuesta caro: el QR no lleva el id de la entrada a
 * secas, lleva {@code id.firma}. Si esta clase produce una firma distinta a la
 * que produce Node —aunque sea por el padding del base64—, <b>todas las entradas
 * ya vendidas dejan de validar en la puerta</b>.
 *
 * <p>Los tres puntos donde Java se desvía de Node si no se le obliga:
 *
 * <ol>
 *   <li>{@code digest("base64url")} en Node es base64url <b>sin relleno</b>. En
 *       Java hay que pedir {@code withoutPadding()} explícitamente, o aparecen
 *       los {@code =} del final.
 *   <li>{@code .update(texto)} en Node codifica en UTF-8 por defecto. Aquí se
 *       pasa {@code StandardCharsets.UTF_8} a mano, porque el charset por
 *       defecto de la JVM en Windows no es UTF-8 y un id con acentos firmaría
 *       distinto.
 *   <li>El truncado a 24 caracteres se hace sobre el texto ya codificado, no
 *       sobre los bytes del hash.
 * </ol>
 *
 * <p>Hay un test que compara contra vectores generados con la implementación de
 * Node. Si alguien toca algo de aquí, ese test lo caza antes de que llegue a una
 * puerta un viernes por la noche.
 */
@Component
public class FirmaDeEntrada {

    /** Cuántos caracteres del HMAC viajan en el QR. Fijado por la implementación original. */
    private static final int LONGITUD_FIRMA = 24;

    private static final String ALGORITMO = "HmacSHA256";

    private final byte[] secreto;

    public FirmaDeEntrada(@Value("${ssiomama.ticket-secret}") String secreto) {
        if (secreto == null || secreto.isBlank()) {
            throw new IllegalStateException(
                    "Falta TICKET_SECRET - sin el cualquiera podria fabricar entradas validas.");
        }
        this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * El HMAC del texto, en base64url sin relleno y recortado.
     *
     * <p>Visible en el paquete para que el test compruebe el algoritmo contra
     * vectores calculados con la implementación de Node.
     */
    String firmar(String idEntrada) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto, ALGORITMO));
            byte[] hash = mac.doFinal(idEntrada.getBytes(StandardCharsets.UTF_8));
            String codificado = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return codificado.substring(0, LONGITUD_FIRMA);
        } catch (java.security.GeneralSecurityException e) {
            // HmacSHA256 es obligatorio en toda JVM: si falla aquí, el entorno
            // está roto y no hay recuperación sensata.
            throw new IllegalStateException("No se pudo firmar la entrada", e);
        }
    }

    /**
     * Convierte el id de una entrada en el código que va dentro del QR.
     *
     * @return {@code id.firma}
     */
    public String codificar(Long idEntrada) {
        String id = idEntrada.toString();
        return id + "." + firmar(id);
    }

    /**
     * Devuelve el id de la entrada si la firma cuadra, o {@code null} si el
     * código está manipulado.
     *
     * <p>Se parte por el <b>último</b> punto y no por el primero: nada impide que
     * un id contenga puntos, y partir por el primero rompería esos códigos.
     */
    public Long decodificar(String codigo) {
        if (codigo == null) {
            return null;
        }
        String limpio = codigo.trim();
        int separador = limpio.lastIndexOf('.');
        if (separador <= 0) {
            return null;
        }

        String idEntrada = limpio.substring(0, separador);
        String recibida = limpio.substring(separador + 1);
        String esperada = firmar(idEntrada);

        // Comparación en tiempo constante: comparar con equals() filtra por el
        // tiempo de respuesta cuántos caracteres iniciales acertó quien lo
        // intenta, y con eso se reconstruye la firma a base de sondeos.
        byte[] a = recibida.getBytes(StandardCharsets.UTF_8);
        byte[] b = esperada.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(a, b)) {
            return null;
        }

        // Un QR emitido antes de los ids numéricos (con un cuid o un UUID)
        // tiene la firma bien hecha, pero su id ya no existe: se rechaza aquí.
        try {
            return Long.valueOf(idEntrada);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
