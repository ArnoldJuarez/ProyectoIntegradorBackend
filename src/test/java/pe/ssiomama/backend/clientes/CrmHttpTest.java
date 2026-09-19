package pe.ssiomama.backend.clientes;

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
 * El CRM por HTTP: quién puede verlo y qué sale por la API.
 *
 * <p>Los permisos importan más aquí que en otras secciones. Un segmento no es
 * una cifra agregada: es <b>la lista con nombre y correo</b> de a quién se le
 * puede escribir, así que es de las respuestas con más datos personales de todo
 * el sistema.
 *
 * <p>Servidor real y no MockMvc por lo mismo que el resto: el reenvío interno a
 * {@code /error} vuelve a pasar por la cadena de seguridad y convierte un 403
 * legítimo en un 401 si la configuración no lo contempla.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CrmHttpTest {

    private static final String CLAVE = "discoteca";

    /** Marca en el nombre, para borrar exactamente lo que crea este test. */
    private static final String MARCA = "Prueba HTTP · ";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void limpiar() {
        jdbc.update("delete from \"Campania\" where nombre like ?", MARCA + "%");
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

    private ResponseEntity<String> segmentos(HttpHeaders cabeceras) {
        return http.exchange(
                "/api/clientes/segmentos",
                HttpMethod.GET,
                new HttpEntity<>(cabeceras),
                String.class);
    }

    private String unSegmentoId(HttpHeaders sesion) throws Exception {
        return json.readTree(segmentos(sesion).getBody()).get(0).get("id").asText();
    }

    // ── Permisos ──────────────────────────────────────────────────

    @Test
    @DisplayName("sin sesion no se ven los segmentos")
    void sinSesion() {
        assertThat(segmentos(new HttpHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * El caso que importa: un segmento es una lista de correos. Ni el portero ni
     * la barra tienen por qué poder descargársela.
     */
    @Test
    @DisplayName("puerta y barra no ven los segmentos: 403, no 401")
    void soloElPanel() {
        assertThat(segmentos(sesionDe("puerta@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(segmentos(sesionDe("barra@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("direccion y gerencia si los ven")
    void elPanelEntra() {
        assertThat(segmentos(sesionDe("admin@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(segmentos(sesionDe("sala@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ── La forma de la respuesta ──────────────────────────────────

    @Test
    @DisplayName("cada segmento llega con su regla explicada y sus cifras")
    void formaDelSegmento() throws Exception {
        JsonNode lista = json.readTree(segmentos(sesionDe("admin@ssiomama.test")).getBody());

        assertThat(lista.isArray()).isTrue();
        assertThat(lista).isNotEmpty();

        for (JsonNode s : lista) {
            assertThat(s.get("nombre").asText()).isNotBlank();
            assertThat(s.get("regla").asText()).isNotBlank();
            assertThat(s.get("reglaReconocida").asBoolean())
                    .as("«%s» tiene una regla que no se entiende", s.get("nombre").asText())
                    .isTrue();
            // A cuánta gente se le puede escribir nunca pasa del total.
            assertThat(s.get("contactables").asLong())
                    .isLessThanOrEqualTo(s.get("miembros").asLong());
        }
    }

    @Test
    @DisplayName("la ficha de un segmento trae a su gente")
    void fichaDeSegmento() throws Exception {
        HttpHeaders sesion = sesionDe("admin@ssiomama.test");

        var r = http.exchange(
                "/api/clientes/segmentos/" + unSegmentoId(sesion),
                HttpMethod.GET,
                new HttpEntity<>(sesion),
                String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode ficha = json.readTree(r.getBody());

        assertThat(ficha.get("segmento").get("nombre").asText()).isNotBlank();
        assertThat(ficha.get("miembros").isArray()).isTrue();
        assertThat(ficha.get("miembros").size())
                .isEqualTo(ficha.get("segmento").get("miembros").asInt());
    }

    @Test
    @DisplayName("un segmento que no existe da 404")
    void segmentoInexistente() {
        assertThat(http.exchange(
                                "/api/clientes/segmentos/no-existe",
                                HttpMethod.GET,
                                new HttpEntity<>(sesionDe("admin@ssiomama.test")),
                                String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Campañas ──────────────────────────────────────────────────

    @Test
    @DisplayName("se redacta una campana y queda en borrador")
    void redactarCampania() throws Exception {
        HttpHeaders sesion = sesionDe("admin@ssiomama.test");

        var r = http.postForEntity(
                "/api/clientes/campanias",
                new HttpEntity<>(
                        """
                        {"nombre":"%sVuelve este viernes","canal":"CORREO","segmentoId":"%s",
                         "asunto":"Te echamos de menos","cuerpo":"Este viernes tenemos reguetón."}
                        """
                                .formatted(MARCA, unSegmentoId(sesion)),
                        sesion),
                String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode c = json.readTree(r.getBody());

        assertThat(c.get("estado").asText()).isEqualTo("BORRADOR");
        assertThat(c.get("sePuedeTocar").asBoolean()).isTrue();
        // El sistema no manda nada: el contador se queda a cero.
        assertThat(c.get("enviados").asInt()).isZero();
    }

    @Test
    @DisplayName("una campana por correo sin asunto es 409 con su motivo")
    void correoSinAsunto() throws Exception {
        HttpHeaders sesion = sesionDe("admin@ssiomama.test");

        var r = http.postForEntity(
                "/api/clientes/campanias",
                new HttpEntity<>(
                        """
                        {"nombre":"%sSin asunto","canal":"CORREO","segmentoId":"%s",
                         "asunto":"","cuerpo":"El cuerpo sí está."}
                        """
                                .formatted(MARCA, unSegmentoId(sesion)),
                        sesion),
                String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(r.getBody()).get("error").asText()).contains("asunto");
    }

    @Test
    @DisplayName("una campana sin cuerpo es 400, no 409")
    void sinCuerpo() throws Exception {
        HttpHeaders sesion = sesionDe("admin@ssiomama.test");

        var r = http.postForEntity(
                "/api/clientes/campanias",
                new HttpEntity<>(
                        """
                        {"nombre":"%sVacía","canal":"SMS","segmentoId":"%s","cuerpo":""}
                        """
                                .formatted(MARCA, unSegmentoId(sesion)),
                        sesion),
                String.class);

        // 400 porque la petición está mal formada, no porque el mundo lo impida.
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("cancelar marca la campana, no la borra")
    void cancelar() throws Exception {
        HttpHeaders sesion = sesionDe("admin@ssiomama.test");

        String id = json.readTree(http.postForEntity(
                                "/api/clientes/campanias",
                                new HttpEntity<>(
                                        """
                                        {"nombre":"%sSe cancela","canal":"SMS","segmentoId":"%s",
                                         "cuerpo":"Texto corto."}
                                        """
                                                .formatted(MARCA, unSegmentoId(sesion)),
                                        sesion),
                                String.class)
                        .getBody())
                .get("id")
                .asText();

        var r = http.exchange(
                "/api/clientes/campanias/" + id,
                HttpMethod.DELETE,
                new HttpEntity<>(sesion),
                String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(r.getBody()).get("estado").asText()).isEqualTo("CANCELADO");

        // Sigue en la lista, marcada.
        String lista = http.exchange(
                        "/api/clientes/campanias",
                        HttpMethod.GET,
                        new HttpEntity<>(sesion),
                        String.class)
                .getBody();
        assertThat(lista).contains(id);
    }

    @Test
    @DisplayName("la barra tampoco redacta campanas")
    void laBarraNoRedacta() {
        var r = http.postForEntity(
                "/api/clientes/campanias",
                new HttpEntity<>(
                        "{\"nombre\":\"x\",\"canal\":\"SMS\",\"segmentoId\":\"x\",\"cuerpo\":\"y\"}",
                        sesionDe("barra@ssiomama.test")),
                String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
