package pe.ssiomama.backend.ia;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Aprobar una propuesta de noches retocándola antes, por HTTP y sin llamar al
 * modelo: la propuesta se siembra a mano como la dejaría el programador.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AprobarConCambiosHttpTest {

    private static final String MARCA = "Prueba Aprobada";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private String ejecucion;
    private String propuesta;

    @AfterEach
    void limpiar() {
        jdbc.update("delete from \"EventoArtista\" where \"eventoId\" in (select id from \"Evento\" where titulo like ?)", MARCA + "%");
        jdbc.update("delete from \"TipoEntrada\" where \"eventoId\" in (select id from \"Evento\" where titulo like ?)", MARCA + "%");
        jdbc.update("delete from \"Evento\" where titulo like ?", MARCA + "%");
        if (propuesta != null) jdbc.update("delete from \"PropuestaAgente\" where id = ?", propuesta);
        if (ejecucion != null) jdbc.update("delete from \"EjecucionAgente\" where id = ?", ejecucion);
    }

    private HttpHeaders sesionDe(String email) {
        HttpHeaders cabecera = new HttpHeaders();
        cabecera.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = http.postForEntity("/api/auth/login",
                new HttpEntity<>("{\"email\":\"%s\",\"password\":\"discoteca\"}".formatted(email), cabecera), String.class);
        List<String> cookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        HttpHeaders conSesion = new HttpHeaders();
        conSesion.add(HttpHeaders.COOKIE, cookies.getFirst().split(";", 2)[0]);
        conSesion.setContentType(MediaType.APPLICATION_JSON);
        return conSesion;
    }

    /** Siembra una propuesta pendiente del programador con una noche. */
    private ObjectNode sembrar() throws Exception {
        ObjectNode datos = (ObjectNode) json.readTree("""
                {"razonamiento": "Los viernes llenan.",
                 "noches": [{"titulo": "Prueba Aprobada Original", "fecha": "2027-05-21", "puertas": "21:00",
                   "cierre": "03:00", "descripcion": "Tal cual la propuso.", "vestimenta": "Casual", "edadMinima": 18,
                   "artistas": [], "entradas": [{"nombre": "General", "descripcion": "", "precioCentimos": 2500,
                   "cantidad": 100, "maximoPorPedido": 6}], "motivo": "Porque sí."}]}
                """);
        ejecucion = UUID.randomUUID().toString();
        propuesta = UUID.randomUUID().toString();
        jdbc.update("""
                insert into "EjecucionAgente" (id, "claveAgente", disparador, estado, salida, "iniciadaEn")
                values (?, 'programmer', 'manual', 'COMPLETADA', cast(? as jsonb), now() at time zone 'utc')
                """, ejecucion, datos.toString());
        jdbc.update("""
                insert into "PropuestaAgente" (id, "ejecucionId", tipo, datos, estado, "creadoEn")
                values (?, ?, 'EVENTO', cast(? as jsonb), 'PENDIENTE', now() at time zone 'utc')
                """, propuesta, ejecucion, datos.toString());
        return datos;
    }

    @Test
    @DisplayName("lo retocado es lo que se crea; lo que dijo la IA queda en su ejecución")
    void creaLoRetocado() throws Exception {
        ObjectNode datos = sembrar();
        ObjectNode noche = (ObjectNode) datos.withArray("noches").get(0);
        noche.put("titulo", "Prueba Aprobada Retocada");
        ((ObjectNode) noche.withArray("entradas").get(0)).put("precioCentimos", 3000);

        ResponseEntity<String> r = http.postForEntity("/api/ia/propuestas/" + propuesta,
                new HttpEntity<>(json.createObjectNode().put("decision", "APROBAR").set("datos", datos).toString(),
                        sesionDe("admin@ssiomama.test")),
                String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode desenlace = json.readTree(r.getBody());
        assertThat(desenlace.path("aplicada").asBoolean()).isTrue();
        assertThat(desenlace.path("enlaces").get(0).path("texto").asText()).isEqualTo("Prueba Aprobada Retocada");

        assertThat(jdbc.queryForObject("""
                select t."precioCentimos" from "TipoEntrada" t join "Evento" e on e.id = t."eventoId"
                where e.titulo = 'Prueba Aprobada Retocada'
                """, Integer.class)).isEqualTo(3000);
        assertThat(jdbc.queryForObject("select count(*) from \"Evento\" where titulo = 'Prueba Aprobada Original'",
                Long.class)).isZero();
        // La propuesta guarda lo aprobado; la ejecución, lo que propuso el modelo.
        assertThat(jdbc.queryForObject("select datos -> 'noches' -> 0 ->> 'titulo' from \"PropuestaAgente\" where id = ?",
                String.class, propuesta)).isEqualTo("Prueba Aprobada Retocada");
        assertThat(jdbc.queryForObject("select salida -> 'noches' -> 0 ->> 'titulo' from \"EjecucionAgente\" where id = ?",
                String.class, ejecucion)).isEqualTo("Prueba Aprobada Original");
    }

    @Test
    @DisplayName("se pueden cambiar los valores, no la forma: lo que no cumple el esquema se rechaza")
    void rechazaOtraForma() throws Exception {
        sembrar();
        ResponseEntity<String> r = http.postForEntity("/api/ia/propuestas/" + propuesta,
                new HttpEntity<>("{\"decision\":\"APROBAR\",\"datos\":{\"noches\":\"no es una lista\"}}",
                        sesionDe("admin@ssiomama.test")),
                String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).contains("forma de la propuesta");
        // Sigue pendiente: un retoque mal hecho no gasta la decisión.
        assertThat(jdbc.queryForObject("select estado::text from \"PropuestaAgente\" where id = ?", String.class, propuesta))
                .isEqualTo("PENDIENTE");
    }
}
