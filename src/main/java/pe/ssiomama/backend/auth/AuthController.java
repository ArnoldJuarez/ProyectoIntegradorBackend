package pe.ssiomama.backend.auth;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Inicio y cierre de sesión del personal. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder contrasenas;
    private final TokenDeSesion tokens;

    /**
     * Si la petición llega por HTTPS, la cookie se marca {@code Secure}. En
     * desarrollo se sirve por HTTP plano y marcarla la haría desaparecer.
     */
    private final boolean cookieSegura;

    public AuthController(
            UsuarioRepository usuarios,
            PasswordEncoder contrasenas,
            TokenDeSesion tokens,
            @org.springframework.beans.factory.annotation.Value("${ssiomama.cookie-segura:false}")
                    boolean cookieSegura) {
        this.usuarios = usuarios;
        this.contrasenas = contrasenas;
        this.tokens = tokens;
        this.cookieSegura = cookieSegura;
    }

    public record Credenciales(
            @NotBlank(message = "Falta el correo")
            @Email(message = "El correo no es válido")
            String email,
            @NotBlank(message = "Falta la contraseña") String password) {}

    /** Lo que se le cuenta al frontend. El hash no sale de aquí jamás. */
    public record Sesion(Long id, String nombre, String email, Rol rol) {
        static Sesion de(Usuario u) {
            return new Sesion(u.getId(), u.getNombre(), u.getEmail(), u.getRol());
        }
    }

    public record Problema(String error) {}

    private ResponseCookie cookie(String valor, long segundos) {
        return ResponseCookie.from(TokenDeSesion.COOKIE, valor)
                // httpOnly: JavaScript no puede leerla. Es lo que impide que un
                // XSS se lleve la sesión de un administrador.
                .httpOnly(true)
                .secure(cookieSegura)
                .path("/")
                .maxAge(segundos)
                // El frontend vive en otro puerto, así que la petición es de otro
                // origen y con SameSite=Strict el navegador no mandaría la cookie.
                .sameSite("Lax")
                .build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> entrar(
            @Valid @RequestBody Credenciales credenciales, HttpServletResponse respuesta) {

        var usuario = usuarios.findByEmailIgnoreCase(credenciales.email().trim());

        /*
         * Un solo mensaje para "no existe", "contraseña incorrecta" y "cuenta
         * desactivada". Distinguirlos le diría a cualquiera qué correos estan
         * dados de alta, que es media hora de trabajo para quien luego prueba
         * contraseñas contra ellos.
         */
        boolean valido = usuario
                .filter(Usuario::isActivo)
                .map(u -> contrasenas.matches(credenciales.password(), u.getHashDeContrasena()))
                .orElse(false);

        if (!valido) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new Problema("Correo o contraseña incorrectos."));
        }

        Usuario u = usuario.get();
        String token = tokens.crear(u.getId());
        respuesta.addHeader("Set-Cookie", cookie(token, TokenDeSesion.DURACION.toSeconds()).toString());

        return ResponseEntity.ok(Sesion.de(u));
    }

    /** Quién soy. El frontend lo llama al arrancar para saber si hay sesión. */
    @GetMapping("/yo")
    public ResponseEntity<?> yo(@AuthenticationPrincipal Usuario usuario) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new Problema("No hay sesión iniciada."));
        }
        return ResponseEntity.ok(Sesion.de(usuario));
    }

    /**
     * Cerrar sesión es borrar la cookie: {@code maxAge=0}.
     *
     * <p>El token sigue siendo válido hasta que caduque —es el precio de no
     * guardar sesiones en la base—, pero deja de estar en el navegador. Para
     * invalidarlo de verdad habría que llevar una lista de revocados, y eso solo
     * compensa cuando hay algo que revocar de urgencia.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> salir(HttpServletResponse respuesta) {
        respuesta.addHeader("Set-Cookie", cookie("", 0).toString());
        return ResponseEntity.noContent().build();
    }
}
