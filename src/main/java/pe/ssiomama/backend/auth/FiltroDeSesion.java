package pe.ssiomama.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Identifica al usuario en cada petición a partir de la cookie de sesión.
 *
 * <p>Es el equivalente de {@code getSession()} del sistema anterior, y como
 * aquel, <b>consulta la base en cada petición</b> en lugar de fiarse solo de lo
 * que dice el token.
 *
 * <p>Eso cuesta una consulta, y es deliberado: el token es válido una semana. Si
 * la sesión se resolviera únicamente con lo que lleva firmado dentro,
 * desactivar una cuenta no tendría efecto hasta que caducara, y despedir a
 * alguien un viernes le dejaría el panel abierto hasta el viernes siguiente.
 *
 * <h2>Por qué NO lleva {@code @Component}</h2>
 *
 * <p>Porque Spring Boot registra automáticamente como filtro de servlet
 * cualquier bean que sea un {@code Filter}, y entonces este correría <b>dos
 * veces</b>: una fuera de la cadena de seguridad y otra dentro. Al extender
 * {@code OncePerRequestFilter}, la de dentro se salta por «ya filtrado».
 *
 * <p>Correr fuera de la cadena no sirve de nada —el contexto que deja ahí lo
 * pisa la propia cadena— y además aplicaría el filtro a rutas que la seguridad
 * ni siquiera gestiona. Por eso lo construye {@code SecurityConfig} con
 * {@code new}: al no ser bean, no hay registro automático que lo duplique.
 */
public class FiltroDeSesion extends OncePerRequestFilter {

    private final TokenDeSesion tokens;
    private final UsuarioRepository usuarios;

    public FiltroDeSesion(TokenDeSesion tokens, UsuarioRepository usuarios) {
        this.tokens = tokens;
        this.usuarios = usuarios;
    }

    private static String cookieDeSesion(HttpServletRequest peticion) {
        Cookie[] cookies = peticion.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (TokenDeSesion.COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest peticion,
            @NonNull HttpServletResponse respuesta,
            @NonNull FilterChain cadena)
            throws ServletException, IOException {

        String token = cookieDeSesion(peticion);
        TokenDeSesion.Contenido contenido = token == null ? null : tokens.leer(token);

        if (contenido != null) {
            usuarios.findById(contenido.userId())
                    // Cuenta desactivada: el token sigue siendo criptograficamente
                    // valido, pero la persona ya no entra.
                    .filter(Usuario::isActivo)
                    .ifPresent(usuario -> {
                        var autenticacion = new UsernamePasswordAuthenticationToken(
                                usuario,
                                null,
                                List.of(new SimpleGrantedAuthority(usuario.getRol().comoAutoridad())));

                        /*
                         * Se crea un contexto NUEVO y se instala, en vez de
                         * mutar el que devuelve getContext(). Es lo que
                         * recomienda Spring Security 6: el contexto es
                         * diferido, y reemplazarlo entero deja claro a toda la
                         * cadena cual es el bueno.
                         */
                        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
                        contexto.setAuthentication(autenticacion);
                        SecurityContextHolder.setContext(contexto);
                    });
        }

        /*
         * Sin cookie, con una manipulada o con un usuario desactivado, la
         * peticion sigue adelante SIN autenticar. No se responde 401 aqui: de
         * eso se encarga la configuracion de rutas, que es la que sabe cuales
         * son publicas. Cortar en el filtro dejaria sin acceso tambien al propio
         * login.
         */
        cadena.doFilter(peticion, respuesta);
    }
}
