package pe.ssiomama.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * El login de punta a punta, contra los usuarios <b>reales</b> de la base.
 *
 * <p>Es la prueba de que la cadena entera funciona con lo que ya existe: el hash
 * {@code scrypt} que escribió Node se verifica desde Java, se emite una cookie
 * firmada, y esa cookie abre una ruta protegida.
 *
 * <p>Necesita PostgreSQL arriba con la base poblada.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    /** Cuenta del seed. La contraseña es la misma para todas. */
    private static final String ADMIN = "admin@ssiomama.test";

    private static final String CLAVE = "discoteca";

    @Autowired
    private MockMvc mvc;

    private static String credenciales(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private Cookie entrarComo(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(email, CLAVE)))
                .andExpect(status().isOk())
                .andReturn();
        return r.getResponse().getCookie(TokenDeSesion.COOKIE);
    }

    @Test
    @DisplayName("entra con una contrasena hasheada por el sistema anterior")
    void entraConHashDeNode() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(ADMIN, CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.nombre").isNotEmpty())
                // El hash NUNCA sale en la respuesta.
                .andExpect(jsonPath("$.hashDeContrasena").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    /**
     * httpOnly es lo que impide que un XSS se lleve la sesión de un
     * administrador leyéndola con JavaScript.
     */
    @Test
    @DisplayName("la cookie va httpOnly")
    void laCookieVaHttpOnly() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(ADMIN, CLAVE)))
                .andExpect(cookie().exists(TokenDeSesion.COOKIE))
                .andExpect(cookie().httpOnly(TokenDeSesion.COOKIE, true));
    }

    @Test
    @DisplayName("rechaza la contrasena incorrecta")
    void rechazaContrasenaIncorrecta() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(ADMIN, "la-que-no-es")))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(TokenDeSesion.COOKIE));
    }

    /**
     * El mensaje de "no existe" tiene que ser idéntico al de "contraseña
     * incorrecta": si se distinguieran, cualquiera podría averiguar qué correos
     * están dados de alta antes de ponerse a probar contraseñas.
     */
    @Test
    @DisplayName("no delata si un correo existe o no")
    void noDelataQueCorreosExisten() throws Exception {
        String conCorreoReal = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(ADMIN, "mal")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String conCorreoInventado = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales("nadie@ssiomama.test", "mal")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(conCorreoReal).isEqualTo(conCorreoInventado);
    }

    @Test
    @DisplayName("sin sesion, /yo responde 401")
    void sinSesionYoDa401() throws Exception {
        mvc.perform(get("/api/auth/yo")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("con la cookie del login, /yo dice quien eres")
    void conSesionYoDiceQuienEres() throws Exception {
        mvc.perform(get("/api/auth/yo").cookie(entrarComo(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN));
    }

    @Test
    @DisplayName("el inventario esta cerrado sin sesion")
    void inventarioCerradoSinSesion() throws Exception {
        mvc.perform(get("/api/inventario/productos")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un ADMIN si ve el inventario")
    void inventarioAbiertoParaAdmin() throws Exception {
        mvc.perform(get("/api/inventario/productos").cookie(entrarComo(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").isNotEmpty());
    }

    /**
     * El portero entra al sistema, pero el inventario no es suyo: 403 (te
     * conozco, no te dejo) y no 401 (no sé quién eres).
     */
    @Test
    @DisplayName("el portero no entra al inventario")
    void elPorteroNoEntraAlInventario() throws Exception {
        mvc.perform(get("/api/inventario/productos").cookie(entrarComo("puerta@ssiomama.test")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("una cookie manipulada no vale")
    void cookieManipuladaNoVale() throws Exception {
        Cookie buena = entrarComo(ADMIN);
        Cookie falsa = new Cookie(TokenDeSesion.COOKIE, buena.getValue().substring(0, buena.getValue().length() - 2) + "xy");
        mvc.perform(get("/api/auth/yo").cookie(falsa)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("cerrar sesion borra la cookie")
    void cerrarSesionBorraLaCookie() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(TokenDeSesion.COOKIE, 0));
    }

    @Test
    @DisplayName("valida el cuerpo de la peticion")
    void validaElCuerpo() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-un-correo\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ADMIN + "\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
