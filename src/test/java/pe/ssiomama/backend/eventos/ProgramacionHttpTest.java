package pe.ssiomama.backend.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Programar noches por HTTP: quién puede, y qué forma tiene el rechazo.
 *
 * <p>Lo que más importa aquí es lo primero: {@code /api/eventos/**} estaba
 * abierto solo al panel, pero esa regla se escribió cuando la sección era de
 * <b>solo lectura</b>. Al añadirle verbos que escriben había que comprobar que
 * el comodín los cubre — un {@code POST} que se colara sería que cualquiera con
 * cuenta puede programar noches.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProgramacionHttpTest {

    private static final String CLAVE = "discoteca";

    /** Marca en el título, para borrar exactamente lo que crea este test. */
    private static final String MARCA = "Prueba HTTP";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void limpiar() {
        // El cartel primero, por la clave ajena.
        jdbc.update(
                """
                delete from "EventoArtista" where "eventoId" in (
                  select id from "Evento" where titulo like ?)
                """,
                MARCA + "%");
        jdbc.update(
                """
                delete from "TipoEntrada" where "eventoId" in (
                  select id from "Evento" where titulo like ?)
                """,
                MARCA + "%");
        jdbc.update("delete from \"Evento\" where titulo like ?", MARCA + "%");
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    private HttpHeaders sesionDe(String email) {
        HttpHeaders cabecera = new HttpHeaders();
        cabecera.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> login = http.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(
                        "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, CLAVE), cabecera),
                String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> cookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull().isNotEmpty();

        HttpHeaders conSesion = new HttpHeaders();
        conSesion.add(HttpHeaders.COOKIE, cookies.getFirst().split(";", 2)[0]);
        conSesion.setContentType(MediaType.APPLICATION_JSON);
        return conSesion;
    }

    private String salaPrincipal() {
        return jdbc.queryForObject(
                "select id from \"Sala\" order by aforo desc limit 1", String.class);
    }

    private String cuerpoDeNoche(String titulo) {
        return """
                {"titulo":"%s","descripcion":"Creada por un test.","salaId":"%s",
                 "puertasA":"2027-04-02T01:00:00Z","terminaA":"2027-04-02T07:00:00Z",
                 "edadMinima":18,"vestimenta":"Casual","estado":"BORRADOR","artistaIds":[]}
                """
                .formatted(titulo, salaPrincipal());
    }

    private ResponseEntity<String> crear(HttpHeaders sesion, String cuerpo) {
        return http.postForEntity("/api/eventos", new HttpEntity<>(cuerpo, sesion), String.class);
    }

    // ── Permisos ──────────────────────────────────────────────────

    @Test
    @DisplayName("sin sesion no se programa nada")
    void sinSesion() {
        HttpHeaders soloJson = new HttpHeaders();
        soloJson.setContentType(MediaType.APPLICATION_JSON);

        assertThat(crear(soloJson, cuerpoDeNoche(MARCA + " sin sesion")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * El caso que había que comprobar al abrir la sección a escritura: el rol de
     * puerta entra al sistema pero no programa noches.
     */
    @Test
    @DisplayName("el portero no crea eventos: 403, no 401")
    void elPorteroNoPrograma() {
        var r = crear(sesionDe("puerta@ssiomama.test"), cuerpoDeNoche(MARCA + " portero"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("la barra tampoco programa")
    void laBarraNoPrograma() {
        assertThat(crear(sesionDe("barra@ssiomama.test"), cuerpoDeNoche(MARCA + " barra"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── El camino bueno ───────────────────────────────────────────

    @Test
    @DisplayName("direccion crea una noche, le pone entradas y la edita")
    void programarUnaNoche() throws Exception {
        HttpHeaders sesion = sesionDe("admin@ssiomama.test");

        var creada = crear(sesion, cuerpoDeNoche(MARCA + " Noche Nueva"));
        assertThat(creada.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode noche = json.readTree(creada.getBody());
        String slug = noche.get("slug").asText();
        assertThat(slug).isEqualTo("prueba-http-noche-nueva");

        // Se le añade un tipo de entrada.
        var tipo = http.postForEntity(
                "/api/eventos/" + slug + "/entradas",
                new HttpEntity<>(
                        """
                        {"nombre":"General","descripcion":"Con una consumición.",
                         "precioCentimos":2500,"cantidad":120,"maximoPorPedido":6,
                         "consumicionesIncluidas":1,"activo":true}
                        """,
                        sesion),
                String.class);
        assertThat(tipo.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String tipoId = json.readTree(tipo.getBody()).get("id").asText();

        // Y se cambia el título: la dirección tiene que seguir siendo la misma.
        var editada = http.exchange(
                "/api/eventos/" + slug,
                HttpMethod.PUT,
                new HttpEntity<>(cuerpoDeNoche(MARCA + " Noche Retocada"), sesion),
                String.class);
        assertThat(editada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(editada.getBody()).get("slug").asText()).isEqualTo(slug);

        // El tipo se borra de verdad porque no tiene ventas.
        var quitado = http.exchange(
                "/api/eventos/entradas/" + tipoId,
                HttpMethod.DELETE,
                new HttpEntity<>(sesion),
                String.class);
        assertThat(quitado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(quitado.getBody()).get("retirado").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("las opciones del formulario traen salas con su aforo")
    void opcionesDelFormulario() throws Exception {
        var r = http.exchange(
                "/api/eventos/opciones",
                HttpMethod.GET,
                new HttpEntity<>(sesionDe("admin@ssiomama.test")),
                String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode opciones = json.readTree(r.getBody());

        assertThat(opciones.get("salas")).isNotEmpty();
        assertThat(opciones.get("salas").get(0).get("aforo").asInt()).isPositive();
        assertThat(opciones.get("artistas")).isNotEmpty();
    }

    // ── Los rechazos ──────────────────────────────────────────────

    @Test
    @DisplayName("un titulo corto es 400, no 409")
    void tituloCorto() throws Exception {
        String cuerpo = cuerpoDeNoche("ab");
        var r = crear(sesionDe("admin@ssiomama.test"), cuerpo);

        // 400 porque la petición está mal formada, no porque el mundo lo impida.
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(r.getBody()).get("error").asText()).contains("título");
    }

    @Test
    @DisplayName("mover una noche con entradas es 409 con el motivo escrito")
    void moverConEntradas() throws Exception {
        HttpHeaders sesion = sesionDe("admin@ssiomama.test");

        var r = http.exchange(
                "/api/eventos/viernes-urbano",
                HttpMethod.PUT,
                new HttpEntity<>(
                        """
                        {"titulo":"Viernes Urbano","descripcion":"","salaId":"%s",
                         "puertasA":"2027-12-01T01:00:00Z","terminaA":"2027-12-01T07:00:00Z",
                         "edadMinima":18,"vestimenta":"","estado":"PUBLICADO","artistaIds":[]}
                        """
                                .formatted(salaPrincipal()),
                        sesion),
                String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(r.getBody()).get("error").asText())
                .contains("entradas emitidas")
                .contains("Cancela");
    }
}
