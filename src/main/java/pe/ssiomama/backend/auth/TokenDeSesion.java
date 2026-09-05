package pe.ssiomama.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La cookie de sesión: {@code cuerpo.firma}, donde el cuerpo es el JSON de la
 * sesión en base64url y la firma su HMAC.
 *
 * <p>Es el port de {@code createSessionToken} / {@code readSessionToken} de
 * {@code lib/auth.ts}. Un token emitido por el sistema anterior tiene que valer
 * aquí, y al revés, o cambiar de backend echaría a todo el personal.
 *
 * <h2>No confundirla con la firma de las entradas</h2>
 *
 * <p>Las dos son HMAC-SHA256 en base64url sin relleno, pero <b>la de las
 * entradas se recorta a 24 caracteres y esta no</b>: aquí van los 43 del digest
 * completo. Reutilizar el código de una para la otra produce tokens que parecen
 * correctos y no validan.
 *
 * <h2>Por qué no es un JWT</h2>
 *
 * <p>Porque lo que hay guardado no lo es. Un JWT llevaría su cabecera con el
 * algoritmo y otro esquema de firma, y ningún token de los que ahora mismo
 * tiene el personal en su navegador validaría.
 */
@Component
public class TokenDeSesion {

    /** Nombre de la cookie. Lo fija el sistema anterior. */
    public static final String COOKIE = "disco_session";

    /** Una semana, como en el original. */
    public static final Duration DURACION = Duration.ofDays(7);

    private static final String ALGORITMO = "HmacSHA256";

    private final byte[] secreto;
    private final ObjectMapper json = new ObjectMapper();

    public TokenDeSesion(@Value("${ssiomama.auth-secret}") String secreto) {
        if (secreto == null || secreto.isBlank()) {
            throw new IllegalStateException(
                    "Falta AUTH_SECRET - sin el, cualquiera podria fabricarse una sesion de ADMIN.");
        }
        this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Contenido de la sesión.
     *
     * <p>El orden de los campos importa: se serializa a JSON en el orden en que
     * están declarados, y ese texto es lo que se firma. Con {@code exp} antes
     * que {@code userId} saldría otro JSON, otra firma, y el sistema anterior
     * rechazaría el token.
     *
     * @param exp momento de caducidad en segundos desde epoch (no milisegundos)
     */
    public record Contenido(String userId, long exp) {}

    private String firmar(String cuerpo) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto, ALGORITMO));
            byte[] hash = mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8));
            // Sin recortar: son los 43 caracteres del digest completo.
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar la sesion", e);
        }
    }

    /** Emite el token de un usuario, con caducidad a una semana. */
    public String crear(String idUsuario) {
        return crear(idUsuario, Instant.now().plus(DURACION).getEpochSecond());
    }

    /** Variante con caducidad explícita: la usan los tests para no depender del reloj. */
    public String crear(String idUsuario, long exp) {
        try {
            byte[] payload = json.writeValueAsBytes(new Contenido(idUsuario, exp));
            String cuerpo = Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
            return cuerpo + "." + firmar(cuerpo);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la sesion", e);
        }
    }

    /**
     * Devuelve el contenido si la firma cuadra y no ha caducado, o {@code null}.
     *
     * <p>El orden importa: primero se verifica la firma y solo después se
     * interpreta el JSON. Al revés, estaríamos parseando datos que cualquiera
     * pudo escribir.
     */
    public Contenido leer(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        int separador = token.lastIndexOf('.');
        if (separador <= 0) {
            return null;
        }

        String cuerpo = token.substring(0, separador);
        byte[] recibida = token.substring(separador + 1).getBytes(StandardCharsets.UTF_8);
        byte[] esperada = firmar(cuerpo).getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(recibida, esperada)) {
            return null;
        }

        Contenido contenido;
        try {
            byte[] payload = Base64.getUrlDecoder().decode(cuerpo);
            contenido = json.readValue(payload, Contenido.class);
        } catch (IllegalArgumentException | java.io.IOException e) {
            // Firma válida pero cuerpo ilegible: no debería pasar nunca, salvo
            // que alguien con el secreto emita basura. Se trata como inválido.
            return null;
        }

        if (contenido.userId() == null || contenido.userId().isBlank()) {
            return null;
        }
        if (contenido.exp() < Instant.now().getEpochSecond()) {
            return null;
        }

        return contenido;
    }
}
