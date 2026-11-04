package pe.ssiomama.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.jdbc.core.JdbcTemplate;
import pe.ssiomama.backend.auth.GestionDePersonal.DatosDePersona;

/**
 * Dar de baja a alguien le cierra la sesión que ya tiene abierta, en la
 * siguiente petición, y no cuando caduque su cookie dentro de una semana.
 *
 * <p>Contra un servidor de verdad porque lo que se prueba es el filtro de
 * sesión, no el servicio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DarDeBajaCierraLaSesionHttpTest {

    private static final String CORREO = "prueba.baja@ssiomama.test";
    private static final String CLAVE = "una-clave-de-prueba";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private GestionDePersonal personal;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("delete from \"Usuario\" where correo = ?", CORREO);
    }

    private HttpStatus yo(HttpHeaders cabeceras) {
        return (HttpStatus) http.exchange("/api/auth/yo", HttpMethod.GET, new HttpEntity<>(cabeceras), String.class)
                .getStatusCode();
    }

    @Test
    @DisplayName("una cuenta dada de baja deja de valer con la cookie que ya tenía")
    void bajaCierraLaSesion() {
        Usuario nuevo = personal.alta(
                new DatosDePersona("Prueba Baja", CORREO, Rol.BARRA, true, null, 0, null, null), CLAVE);

        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = http.postForEntity("/api/auth/login",
                new HttpEntity<>("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(CORREO, CLAVE), json), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> cookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        HttpHeaders conSesion = new HttpHeaders();
        conSesion.add(HttpHeaders.COOKIE, cookies.getFirst().split(";", 2)[0]);

        assertThat(yo(conSesion)).isEqualTo(HttpStatus.OK);

        Usuario admin = usuarios.findByEmailIgnoreCase("admin@ssiomama.test").orElseThrow();
        personal.editar(nuevo.getId(),
                new DatosDePersona("Prueba Baja", CORREO, Rol.BARRA, false, null, 0, null, null), admin);

        assertThat(yo(conSesion)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
