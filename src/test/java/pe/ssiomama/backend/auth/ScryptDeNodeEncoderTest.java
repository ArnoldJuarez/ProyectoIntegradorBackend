package pe.ssiomama.backend.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Paridad con el {@code scrypt} de Node.
 *
 * <p>El valor esperado sale de ejecutar {@code crypto.scryptSync("discoteca",
 * salt, 64)} con el mismo salt. Mientras este test pase, las contraseñas que ya
 * están en la base siguen sirviendo para entrar por el backend Java.
 */
class ScryptDeNodeEncoderTest {

    private final ScryptDeNodeEncoder encoder = new ScryptDeNodeEncoder();

    /**
     * Generado con la implementación de Node:
     *
     * <pre>
     * scryptSync("discoteca", "a1b2c3d4e5f60718293a4b5c6d7e8f90", 64)
     * </pre>
     */
    private static final String ALMACENADO_POR_NODE =
            "scrypt:a1b2c3d4e5f60718293a4b5c6d7e8f90:"
                    + "2da997a5017a6f7ca601ebeb7d3656d2990b71d9941d3b1ccb3ad4604cd8a779"
                    + "2bd13d280732001f9035c11dd6ce533a275e1ae34ad7b7a41df3230a87e1238b";

    @Test
    @DisplayName("acepta una contrasena hasheada por Node")
    void aceptaHashDeNode() {
        assertTrue(encoder.matches("discoteca", ALMACENADO_POR_NODE));
    }

    @Test
    @DisplayName("rechaza la contrasena equivocada contra un hash de Node")
    void rechazaContrasenaIncorrecta() {
        assertFalse(encoder.matches("Discoteca", ALMACENADO_POR_NODE));
        assertFalse(encoder.matches("discotec", ALMACENADO_POR_NODE));
        assertFalse(encoder.matches("", ALMACENADO_POR_NODE));
    }

    @Test
    @DisplayName("lo que genera Java lo puede verificar Java")
    void idaYVuelta() {
        String hash = encoder.encode("otra-clave-cualquiera");
        assertTrue(hash.startsWith("scrypt:"));
        assertTrue(encoder.matches("otra-clave-cualquiera", hash));
        assertFalse(encoder.matches("otra-clave-cualquier", hash));
    }

    /**
     * Dos hashes de la misma contraseña tienen que salir distintos, o el salt no
     * está haciendo su trabajo.
     */
    @Test
    @DisplayName("cada hash lleva su propio salt")
    void cadaHashLlevaSuSalt() {
        assertFalse(encoder.encode("misma").equals(encoder.encode("misma")));
    }

    @Test
    @DisplayName("no revienta con valores corruptos en la columna")
    void toleraValoresCorruptos() {
        assertFalse(encoder.matches("x", "sin-formato"));
        assertFalse(encoder.matches("x", "bcrypt:abc:def"));
        assertFalse(encoder.matches("x", "scrypt:solo-dos-partes"));
        assertFalse(encoder.matches("x", "scrypt:abc:no-es-hexadecimal"));
        assertFalse(encoder.matches("x", "scrypt::"));
        assertFalse(encoder.matches("x", null));
        assertFalse(encoder.matches(null, ALMACENADO_POR_NODE));
    }
}
