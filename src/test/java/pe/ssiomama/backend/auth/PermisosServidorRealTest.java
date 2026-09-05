package pe.ssiomama.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Los códigos de estado, contra un <b>servidor de verdad</b>.
 *
 * <h2>Por qué existe este test si ya hay uno con MockMvc</h2>
 *
 * <p>Porque MockMvc dio un falso verde. Cuando una petición acaba en error,
 * Spring Boot la <b>reenvía a {@code /error}</b> para construir la respuesta, y
 * ese reenvío vuelve a pasar por la cadena de seguridad. Si la configuración lo
 * deniega, el 403 legítimo que ya se había generado queda pisado por el 401 del
 * segundo paso: un usuario identificado al que le falta un permiso recibe «no sé
 * quién eres» en lugar de «te conozco, no te dejo».
 *
 * <p>MockMvc no hace ese reenvío, así que el test de MockMvc pasaba mientras el
 * navegador recibía 401. Este arranca Tomcat en un puerto real y sí lo
 * reproduce.
 *
 * <p>La diferencia importa de verdad: un frontend que ve 401 asume que la sesión
 * caducó y devuelve al login, en bucle, a alguien que sí está identificado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PermisosServidorRealTest {

    private static final String CLAVE = "discoteca";

    @Autowired
    private TestRestTemplate http;

    /** Inicia sesión y devuelve las cabeceras con la cookie ya puesta. */
    private HttpHeaders sesionDe(String email) {
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> login = http.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(
                        "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, CLAVE), json),
                String.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> cookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull().isNotEmpty();

        HttpHeaders conSesion = new HttpHeaders();
        conSesion.add(HttpHeaders.COOKIE, cookies.getFirst().split(";", 2)[0]);
        return conSesion;
    }

    private HttpStatus estadoDe(String ruta, HttpHeaders cabeceras) {
        return (HttpStatus) http.exchange(
                        ruta, HttpMethod.GET, new HttpEntity<>(cabeceras), String.class)
                .getStatusCode();
    }

    @Test
    @DisplayName("sin sesion: 401, no se quien eres")
    void sinSesion401() {
        assertThat(estadoDe("/api/inventario/productos", new HttpHeaders()))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * El caso que se escapó. Tiene que ser 403 y no 401: el portero está
     * perfectamente identificado, lo que le falta es el permiso.
     */
    @Test
    @DisplayName("portero identificado sin permiso: 403, no 401")
    void porteroSinPermiso403() {
        assertThat(estadoDe("/api/inventario/productos", sesionDe("puerta@ssiomama.test")))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("el portero si esta identificado")
    void porteroSiEstaIdentificado() {
        assertThat(estadoDe("/api/auth/yo", sesionDe("puerta@ssiomama.test")))
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("direccion y jefatura de sala ven el inventario")
    void panelVeElInventario() {
        assertThat(estadoDe("/api/inventario/productos", sesionDe("admin@ssiomama.test")))
                .isEqualTo(HttpStatus.OK);
        assertThat(estadoDe("/api/inventario/productos", sesionDe("sala@ssiomama.test")))
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("una ruta que no existe no filtra si hay sesion o no")
    void rutaInexistente() {
        // denyAll() la atrapa antes de llegar a Spring MVC, asi que responde 401
        // en vez de 404. Es lo correcto: un 404 confirmaria que la ruta no
        // existe, y por descarte, cuales si.
        assertThat(estadoDe("/api/lo-que-sea", new HttpHeaders()))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
