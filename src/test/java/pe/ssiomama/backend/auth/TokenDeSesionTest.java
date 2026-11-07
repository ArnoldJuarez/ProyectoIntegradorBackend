package pe.ssiomama.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Paridad con el token de sesión de Node.
 *
 * <p>El valor esperado sale de ejecutar {@code createSessionToken} de
 * {@code lib/auth.ts} con el mismo secreto de prueba y una caducidad fija.
 */
class TokenDeSesionTest {

    private static final String SECRETO = "secreto-auth-de-prueba";

    /** Año 2030: lo bastante lejos para que el test no caduque solo. */
    private static final long EXP_FIJO = 1_900_000_000L;

    private final TokenDeSesion sesion = new TokenDeSesion(SECRETO);

    /**
     * Generado con Node:
     *
     * <pre>
     * body = base64url({"userId":"usr_admin_001","exp":1900000000})
     * sig  = base64url(hmacSha256(AUTH_SECRET, body))
     * </pre>
     */
    private static final String TOKEN_DE_NODE =
            "eyJ1c2VySWQiOiJ1c3JfYWRtaW5fMDAxIiwiZXhwIjoxOTAwMDAwMDAwfQ"
                    + ".tN4QyVFi9SAwEbg-V0M9QEuEqhE64XwH_raxKTf57PM";

    @Test
    @DisplayName("el cuerpo es el JSON de la sesion, con el id como numero")
    void cuerpoDelToken() {
        String token = sesion.crear(7L, EXP_FIJO);
        String cuerpo = new String(
                java.util.Base64.getUrlDecoder().decode(token.substring(0, token.lastIndexOf('.'))),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(cuerpo).isEqualTo("{\"userId\":7,\"exp\":1900000000}");
    }

    /**
     * La firma es buena (mismo secreto), pero el usuario era un texto que ya no
     * existe: quien tuviera esa cookie vuelve a iniciar sesión.
     */
    @Test
    @DisplayName("un token con id de texto del sistema anterior ya no vale")
    void rechazaTokenConIdDeTexto() {
        assertThat(sesion.leer(TOKEN_DE_NODE)).isNull();
    }

    /**
     * La firma de sesión NO se recorta: son los 43 caracteres del digest
     * completo, al contrario que la de las entradas, que se queda en 24.
     */
    @Test
    @DisplayName("la firma va completa, sin recortar a 24")
    void laFirmaVaCompleta() {
        String token = sesion.crear(7L, EXP_FIJO);
        String firma = token.substring(token.lastIndexOf('.') + 1);
        assertThat(firma).hasSize(43);
    }

    @Test
    @DisplayName("un token recien emitido se lee")
    void idaYVuelta() {
        TokenDeSesion.Contenido c = sesion.leer(sesion.crear(12L));
        assertThat(c).isNotNull();
        assertThat(c.userId()).isEqualTo(12L);
        assertThat(c.exp()).isGreaterThan(Instant.now().getEpochSecond());
    }

    @Test
    @DisplayName("rechaza una firma manipulada")
    void rechazaFirmaManipulada() {
        String token = sesion.crear(7L, EXP_FIJO);
        String manipulado = token.substring(0, token.length() - 1)
                + (token.endsWith("M") ? "N" : "M");
        assertThat(sesion.leer(manipulado)).isNull();
    }

    /**
     * Lo que de verdad protege el HMAC: cambiar el userId del cuerpo para
     * hacerse pasar por otro. Sin el secreto no se puede recalcular la firma.
     */
    @Test
    @DisplayName("rechaza un cuerpo reescrito para suplantar a otro usuario")
    void rechazaSuplantacion() {
        String cuerpoFalso = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"userId\":2,\"exp\":1900000000}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String firmaDeOtro = TOKEN_DE_NODE.substring(TOKEN_DE_NODE.lastIndexOf('.') + 1);
        assertThat(sesion.leer(cuerpoFalso + "." + firmaDeOtro)).isNull();
    }

    @Test
    @DisplayName("rechaza un token caducado aunque la firma sea buena")
    void rechazaCaducado() {
        // Firmado con el secreto correcto, pero con caducidad en 2001.
        String caducado = sesion.crear(7L, 1_000_000_000L);
        assertThat(sesion.leer(caducado)).isNull();
    }

    @Test
    @DisplayName("rechaza tokens malformados sin reventar")
    void rechazaMalformados() {
        assertThat(sesion.leer(null)).isNull();
        assertThat(sesion.leer("")).isNull();
        assertThat(sesion.leer("sinpunto")).isNull();
        assertThat(sesion.leer(".soloFirma")).isNull();
        assertThat(sesion.leer("no-es-base64.tN4QyVFi9SAwEbg-V0M9QEuEqhE64XwH_raxKTf57PM")).isNull();
    }

    @Test
    @DisplayName("un token firmado con otro secreto no vale")
    void rechazaOtroSecreto() {
        String ajeno = new TokenDeSesion("otro-secreto-distinto").crear(7L, EXP_FIJO);
        assertThat(sesion.leer(ajeno)).isNull();
    }
}
