package pe.ssiomama.backend.config;

import jakarta.servlet.DispatcherType;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import pe.ssiomama.backend.auth.FiltroDeSesion;
import pe.ssiomama.backend.auth.Rol;
import pe.ssiomama.backend.auth.ScryptDeNodeEncoder;
import pe.ssiomama.backend.auth.TokenDeSesion;
import pe.ssiomama.backend.auth.UsuarioRepository;

/**
 * Quién puede llamar a qué.
 *
 * <p>La regla de fondo es cerrar por defecto: {@code anyRequest().denyAll()} al
 * final, y cada ruta que se abre se abre a mano. Un {@code permitAll()} general
 * «mientras tanto» es de esas cosas que sobreviven hasta producción.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final List<String> origenesPermitidos;
    private final FiltroDeSesion filtroDeSesion;

    /**
     * El filtro se construye aquí con {@code new} y no se inyecta como bean a
     * propósito: Spring Boot registra automáticamente todo bean que sea un
     * {@code Filter}, y correría dos veces. Ver el porqué en
     * {@link FiltroDeSesion}.
     */
    public SecurityConfig(
            @Value("${ssiomama.cors-origins}") String origenes,
            TokenDeSesion tokens,
            UsuarioRepository usuarios) {
        this.origenesPermitidos = List.of(origenes.split("\\s*,\\s*"));
        this.filtroDeSesion = new FiltroDeSesion(tokens, usuarios);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new ScryptDeNodeEncoder();
    }

    /**
     * El navegador solo puede llamar a esta API desde los orígenes declarados.
     *
     * <p>{@code allowCredentials(true)} hace falta porque la sesión viaja en una
     * cookie; y con credenciales el navegador rechaza el comodín {@code *}, así
     * que los orígenes van enumerados de verdad.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origenesPermitidos);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "Accept"));
        cors.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", cors);
        return fuente;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                /*
                 * CSRF desactivado. La cookie va SameSite=Lax, que impide que
                 * otro sitio dispare un POST con ella, y la API no sirve
                 * formularios HTML. Si algun dia se abre a terceros, hay que
                 * volver a activarlo con CookieCsrfTokenRepository.
                 */
                .csrf(csrf -> csrf.disable())
                // Sin sesion de servlet: quien manda es la cookie firmada.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        /*
                         * Los reenvios internos de Spring Boot, permitidos.
                         *
                         * Cuando una peticion acaba en error, Spring Boot la
                         * reenvia a /error para construir la respuesta. Ese
                         * reenvio vuelve a pasar por la cadena de seguridad, y
                         * si lo atrapa el denyAll() del final ocurre algo que
                         * cuesta mucho de encontrar: el 403 legitimo YA se ha
                         * generado, pero el reenvio a /error se deniega otra vez
                         * —y para entonces el usuario ya es anonimo—, asi que la
                         * respuesta final que ve el cliente es un 401.
                         *
                         * Es decir: un usuario perfectamente identificado al que
                         * le falta un permiso recibe "no se quien eres" en lugar
                         * de "te conozco, no te dejo". Con MockMvc no se
                         * reproduce, porque alli no hay reenvio de verdad.
                         */
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
                        .permitAll()

                        // El preflight de CORS viaja sin cookie: si se exigiera
                        // sesion aqui, el navegador ni llegaria a mandar la
                        // peticion de verdad.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        /*
                         * La cartelera es publica de verdad: la lee cualquiera
                         * sin cuenta, que es justo lo que hace una web de
                         * discoteca. Va en su propio prefijo /api/publico y con
                         * sus propios DTO, para que sea imposible publicar sin
                         * querer un campo interno al tocar el panel.
                         */
                        .requestMatchers(HttpMethod.GET, "/api/publico/**").permitAll()
                        /*
                         * El alta en el boletin escribe, y aun asi es publica:
                         * pedir cuenta para dejar un correo no lo haria nadie.
                         * Va enumerada una a una y no con /api/publico/**, para
                         * que anadir manana otro POST ahi no quede abierto sin
                         * que nadie lo decida.
                         */
                        .requestMatchers(HttpMethod.POST, "/api/publico/boletin").permitAll()
                        // Pedir un reservado tampoco exige cuenta: no aparta la
                        // mesa ni cobra nada, deja una solicitud que alguien
                        // llama para confirmar.
                        .requestMatchers(HttpMethod.POST, "/api/publico/reservas").permitAll()

                        /*
                         * Comprar entradas: sin cuenta, como el resto de la web
                         * publica. Se enumeran una a una y no con un comodin,
                         * para que anadir manana otro POST bajo /pedidos no
                         * quede abierto sin que nadie lo decida.
                         *
                         * El GET de un pedido tambien es publico: quien compro
                         * no tiene cuenta, y lo unico que tiene es el enlace con
                         * su codigo. Por eso la respuesta no lleva ni su correo
                         * ni su telefono — ver CompraController.
                         */
                        .requestMatchers(HttpMethod.POST, "/api/publico/pedidos").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/publico/pedidos/*/pagar").permitAll()

                        // Publicas: entrar y salir.
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        // Contesta 401 por si sola cuando no hay sesion: es como
                        // el frontend pregunta si toca enseñar el login.
                        .requestMatchers(HttpMethod.GET, "/api/auth/yo").permitAll()

                        /*
                         * El panel. Los clientes van aqui con mas motivo que el
                         * resto: son datos personales de gente real —nombres,
                         * correos, telefonos, cuanto se gastan— y no salen de
                         * direccion y jefatura de sala.
                         */
                        .requestMatchers(
                                "/api/inventario/**",
                                "/api/eventos/**",
                                "/api/clientes/**",
                                "/api/caja/**",
                                "/api/pedidos/**")
                        .hasAnyAuthority(autoridades(Rol.PANEL))


                        /*
                         * La puerta es el unico sitio donde entra un rol que no
                         * es de panel, y va antes del denyAll por eso mismo.
                         *
                         * Un portero ve esto y nada mas: ni clientes, ni caja,
                         * ni almacen. Es la razon de que exista el rol PUERTA en
                         * vez de dar una cuenta de gerencia a quien esta en la
                         * entrada, que es lo que se acaba haciendo cuando el
                         * sistema no lo contempla.
                         */
                        .requestMatchers("/api/puerta/**")
                        .hasAnyAuthority(autoridades(Rol.EN_LA_PUERTA))

                        /*
                         * La barra, como la puerta: un rol que no es de panel y
                         * una pantalla propia. Quien sirve copas cobra aqui y no
                         * ve nada mas — ni clientes, ni pedidos, ni el arqueo
                         * historico, que se mira en /api/caja desde el panel.
                         */
                        .requestMatchers("/api/barra/**")
                        .hasAnyAuthority(autoridades(Rol.EN_LA_BARRA))

                        .anyRequest().denyAll())
                /*
                 * El filtro va antes del de usuario y contraseña de Spring, que
                 * es donde el resto de la cadena espera encontrar ya resuelta la
                 * identidad.
                 */
                .addFilterBefore(filtroDeSesion, UsernamePasswordAuthenticationFilter.class)
                /*
                 * Sin esto, Spring responde a la falta de sesion con una
                 * redireccion al formulario de login. Un cliente que espera JSON
                 * recibiria un 302 hacia una pagina HTML, que es de los errores
                 * mas desconcertantes de depurar desde el frontend.
                 */
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable());

        return http.build();
    }

    private static String[] autoridades(Set<Rol> roles) {
        return roles.stream().map(Rol::comoAutoridad).toArray(String[]::new);
    }
}
