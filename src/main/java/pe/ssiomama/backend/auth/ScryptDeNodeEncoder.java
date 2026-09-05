package pe.ssiomama.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Verifica las contraseñas que ya están en la base, hasheadas por Node.
 *
 * <p>Es el port de {@code hashPassword} / {@code verifyPassword} de {@code
 * lib/auth.ts}. No se puede usar el {@code SCryptPasswordEncoder} de Spring
 * Security porque guarda en su propio formato ({@code $e0801$...}) y aquí lo
 * almacenado tiene la forma:
 *
 * <pre>scrypt:{salt}:{derivada}</pre>
 *
 * <p>Si esto no reprodujera el mismo resultado, nadie del personal podría entrar
 * y la única salida sería resetear todas las contraseñas a mano.
 *
 * <h2>El detalle que cuesta encontrar</h2>
 *
 * <p>Node llama a {@code scryptSync(pass, salt, 64)} pasando el salt como
 * <b>cadena de texto</b>. Node no la interpreta como hexadecimal: la convierte a
 * bytes en UTF-8. Así que el salt real son los <b>32 bytes ASCII de los 32
 * caracteres hex</b>, no los 16 bytes que saldrían de decodificar ese hex.
 *
 * <p>Decodificar el hex "porque es hex" da un resultado distinto y perfectamente
 * plausible, y el fallo se manifiesta como "la contraseña es incorrecta" sin más
 * pista.
 */
public class ScryptDeNodeEncoder implements PasswordEncoder {

    /*
     * Los parámetros por defecto de crypto.scryptSync en Node. No son elección
     * nuestra: son los que se usaron al hashear lo que ya está guardado.
     */
    private static final int COSTE_CPU = 16384; // N
    private static final int TAMANO_BLOQUE = 8; // r
    private static final int PARALELISMO = 1; // p
    private static final int LONGITUD_CLAVE = 64; // dkLen, en bytes

    private static final String ESQUEMA = "scrypt";
    private static final int BYTES_DE_SALT = 16;

    private final SecureRandom aleatorio = new SecureRandom();

    /**
     * Deriva la clave igual que lo haría Node.
     *
     * @param salt el salt tal y como está guardado, en texto
     */
    private static byte[] derivar(CharSequence contrasena, String salt) {
        return SCrypt.generate(
                contrasena.toString().getBytes(StandardCharsets.UTF_8),
                // El salt entra como TEXTO, no como hex decodificado. Ver arriba.
                salt.getBytes(StandardCharsets.UTF_8),
                COSTE_CPU,
                TAMANO_BLOQUE,
                PARALELISMO,
                LONGITUD_CLAVE);
    }

    @Override
    public String encode(CharSequence contrasena) {
        byte[] bytesDeSalt = new byte[BYTES_DE_SALT];
        aleatorio.nextBytes(bytesDeSalt);
        // Node genera el salt con randomBytes(16).toString("hex"): 16 bytes que
        // se vuelven 32 caracteres, y son esos 32 caracteres los que se guardan
        // y los que luego se usan como salt.
        String salt = HexFormat.of().formatHex(bytesDeSalt);
        String derivada = HexFormat.of().formatHex(derivar(contrasena, salt));
        return ESQUEMA + ":" + salt + ":" + derivada;
    }

    @Override
    public boolean matches(CharSequence contrasena, String almacenado) {
        if (contrasena == null || almacenado == null) {
            return false;
        }

        String[] partes = almacenado.split(":");
        if (partes.length != 3 || !ESQUEMA.equals(partes[0])) {
            return false;
        }

        String salt = partes[1];
        byte[] esperada;
        try {
            esperada = HexFormat.of().parseHex(partes[2]);
        } catch (IllegalArgumentException e) {
            // La columna tiene algo que no es hexadecimal: no es una contraseña
            // que podamos verificar, y desde luego no vale como correcta.
            return false;
        }
        if (salt.isEmpty() || esperada.length == 0) {
            return false;
        }

        byte[] calculada = derivar(contrasena, salt);
        // isEqual compara en tiempo constante y no se va por las ramas cuando
        // las longitudes difieren.
        return MessageDigest.isEqual(calculada, esperada);
    }
}
