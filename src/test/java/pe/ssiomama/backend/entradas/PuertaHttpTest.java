package pe.ssiomama.backend.entradas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Quién puede usar la puerta, y qué forma tiene la respuesta.
 *
 * <p>Arranca un servidor de verdad y no MockMvc por lo mismo que
 * {@code PermisosServidorRealTest}: el reenvío interno a {@code /error} vuelve a
 * pasar por la cadena de seguridad y convierte un 403 legítimo en un 401 si la
 * configuración no lo contempla. Con MockMvc ese fallo no se reproduce.
 *
 * <p>Aquí importa especialmente, porque la puerta es la única ruta abierta a un
 * rol que no es de panel: si esa regla estuviera mal puesta, o el portero se
 * quedaría fuera de su propia pantalla, o el resto del sistema se abriría a
 * quien solo tiene que escanear.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PuertaHttpTest {

    private static final String CLAVE = "discoteca";

    @Autowired
    private TestRestTemplate http;

    private final ObjectMapper json = new ObjectMapper();

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

    private ResponseEntity<String> noche(HttpHeaders cabeceras) {
        return http.exchange(
                "/api/puerta/noche", HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);
    }

    private ResponseEntity<String> escanear(HttpHeaders cabeceras, String codigo) {
        String cuerpo = "{\"codigo\":\"%s\"}".formatted(codigo);
        return http.postForEntity(
                "/api/puerta/escanear", new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    // ── Permisos ──────────────────────────────────────────────────

    @Test
    @DisplayName("sin sesion no se ve la puerta")
    void sinSesion() {
        assertThat(noche(new HttpHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("el portero si ve su pantalla")
    void elPorteroEntra() {
        assertThat(noche(sesionDe("puerta@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * Que dirección y gerencia entren no es un extra: son quienes cubren la
     * puerta cuando el portero se va a cenar.
     */
    @Test
    @DisplayName("direccion y gerencia tambien pueden escanear")
    void elPanelTambien() {
        assertThat(noche(sesionDe("admin@ssiomama.test")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(noche(sesionDe("sala@ssiomama.test")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Y que la barra no, tampoco. Quien sirve copas no tiene por qué poder
     * marcar entradas como usadas.
     */
    @Test
    @DisplayName("la barra no escanea: 403, que no es 401")
    void laBarraNo() {
        HttpHeaders barra = sesionDe("barra@ssiomama.test");

        assertThat(noche(barra).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(escanear(barra, "da-igual.loquesea").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── La forma de la respuesta ──────────────────────────────────

    /**
     * Un rechazo es una respuesta correcta, no un error. Si esto devolviera 400,
     * el frontend tendría que distinguir «entrada de otra noche» de «se cayó el
     * servidor» leyendo el cuerpo, y las dos cosas se pintarían igual de rojo.
     */
    @Test
    @DisplayName("una entrada falsa responde 200 con ok en falso")
    void elRechazoNoEsUnError() throws Exception {
        var r = escanear(sesionDe("puerta@ssiomama.test"), "inventado.nofirmado");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode cuerpo = json.readTree(r.getBody());
        assertThat(cuerpo.get("ok").asBoolean()).isFalse();
        assertThat(cuerpo.get("motivo").asText()).isEqualTo("FALSA");
        assertThat(cuerpo.get("mensaje").asText()).isNotBlank();
    }

    /** Un código vacío sí es un error de la petición: falta el dato. */
    @Test
    @DisplayName("sin codigo la peticion esta mal formada")
    void sinCodigo() {
        var r = escanear(sesionDe("puerta@ssiomama.test"), "");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * La pantalla nunca se queda sin respuesta. Con evento o sin él, devuelve
     * una estructura completa: en blanco no se distingue «hoy no hay nada» de
     * «algo se rompió».
     */
    @Test
    @DisplayName("el estado de la noche siempre trae una respuesta entera")
    void laNocheSiempreResponde() throws Exception {
        JsonNode n = json.readTree(noche(sesionDe("puerta@ssiomama.test")).getBody());

        assertThat(n.has("hayNoche")).isTrue();
        assertThat(n.get("porPuerta").isArray()).isTrue();
        assertThat(n.get("ultimos").isArray()).isTrue();

        if (n.get("hayNoche").asBoolean()) {
            assertThat(n.get("titulo").asText()).isNotBlank();
            assertThat(n.get("sala").asText()).isNotBlank();
            assertThat(n.get("aforoDeLaSala").asInt()).isPositive();
            // Nadie puede haber entrado más veces que entradas se emitieron.
            assertThat(n.get("dentro").asLong()).isLessThanOrEqualTo(n.get("emitidas").asLong());
        }
    }
}
