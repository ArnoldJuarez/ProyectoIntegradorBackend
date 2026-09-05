package pe.ssiomama.backend.clientes;

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
 * Las fichas de cliente del panel, con servidor real.
 *
 * <p>Solo lee: no crea ni borra nada, así que no necesita limpiar después.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClienteControllerTest {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private TestRestTemplate http;

    private HttpHeaders sesionDe(String email) {
        HttpHeaders tipo = new HttpHeaders();
        tipo.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> login = http.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(
                        "{\"email\":\"%s\",\"password\":\"discoteca\"}".formatted(email), tipo),
                String.class);

        List<String> cookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull().isNotEmpty();

        HttpHeaders conSesion = new HttpHeaders();
        conSesion.add(HttpHeaders.COOKIE, cookies.getFirst().split(";", 2)[0]);
        return conSesion;
    }

    private ResponseEntity<String> pedir(String ruta, HttpHeaders cabeceras) {
        return http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);
    }

    /**
     * Lo primero que hay que comprobar de una pantalla con datos personales: que
     * no la abre cualquiera.
     */
    @Test
    @DisplayName("los clientes NO se ven sin sesion")
    void cerradoSinSesion() {
        assertThat(pedir("/api/clientes", new HttpHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("un portero tampoco ve los clientes")
    void elPorteroNoVeClientes() {
        assertThat(pedir("/api/clientes", sesionDe("puerta@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("direccion ve el listado con sus contadores")
    void direccionVeElListado() throws Exception {
        ResponseEntity<String> r = pedir("/api/clientes", sesionDe("admin@ssiomama.test"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode lista = json.readTree(r.getBody());
        assertThat(lista).isNotEmpty();
        assertThat(lista.get(0).has("visitas")).isTrue();
        assertThat(lista.get(0).has("gastadoCentimos")).isTrue();
    }

    /** Sin tope, pedir la lista entera crece con la base hasta reventar. */
    @Test
    @DisplayName("el listado tiene tope aunque se pida mas")
    void elListadoTieneTope() throws Exception {
        JsonNode lista = json.readTree(
                pedir("/api/clientes?limite=99999", sesionDe("admin@ssiomama.test")).getBody());

        assertThat(lista.size()).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("se puede buscar por nombre o correo")
    void seBuscaPorNombreOCorreo() throws Exception {
        HttpHeaders sesion = sesionDe("admin@ssiomama.test");

        JsonNode todos = json.readTree(pedir("/api/clientes?limite=100", sesion).getBody());
        String correo = todos.get(0).get("email").asText();
        // Un trozo del correo, para que la búsqueda tenga que ser parcial.
        String trozo = correo.substring(0, Math.min(5, correo.length()));

        JsonNode encontrados =
                json.readTree(pedir("/api/clientes?buscar=" + trozo, sesion).getBody());

        assertThat(encontrados).isNotEmpty();
        assertThat(encontrados).allSatisfy(c -> assertThat(
                        c.get("email").asText().toLowerCase()
                                + c.get("nombre").asText().toLowerCase())
                .contains(trozo.toLowerCase()));
    }

    @Test
    @DisplayName("la busqueda ignora mayusculas")
    void laBusquedaIgnoraMayusculas() throws Exception {
        HttpHeaders sesion = sesionDe("admin@ssiomama.test");
        JsonNode todos = json.readTree(pedir("/api/clientes?limite=100", sesion).getBody());
        String trozo = todos.get(0).get("email").asText().substring(0, 5);

        JsonNode enMinusculas = json.readTree(pedir("/api/clientes?buscar=" + trozo, sesion).getBody());
        JsonNode enMayusculas =
                json.readTree(pedir("/api/clientes?buscar=" + trozo.toUpperCase(), sesion).getBody());

        assertThat(enMayusculas.size()).isEqualTo(enMinusculas.size());
    }

    @Test
    @DisplayName("la ficha trae el historial y la media por visita")
    void laFichaTraeHistorialYMedia() throws Exception {
        HttpHeaders sesion = sesionDe("admin@ssiomama.test");
        JsonNode lista = json.readTree(pedir("/api/clientes?limite=100", sesion).getBody());

        // Alguien que ya haya venido, para que la media diga algo.
        String id = null;
        for (JsonNode c : lista) {
            if (c.get("visitas").asInt() > 0) {
                id = c.get("id").asText();
                break;
            }
        }
        assertThat(id).as("el seed tiene clientes con visitas").isNotNull();

        JsonNode ficha = json.readTree(pedir("/api/clientes/" + id, sesion).getBody());

        assertThat(ficha.get("historial").isArray()).isTrue();
        assertThat(ficha.get("gastoMedioCentimos").asInt())
                .isEqualTo(ficha.get("gastadoCentimos").asInt() / ficha.get("visitas").asInt());
    }

    /** Quien no ha venido nunca no debe dar una división por cero. */
    @Test
    @DisplayName("la media es cero para quien nunca ha venido")
    void mediaCeroSinVisitas() throws Exception {
        HttpHeaders sesion = sesionDe("admin@ssiomama.test");
        JsonNode lista = json.readTree(pedir("/api/clientes?limite=100", sesion).getBody());

        for (JsonNode c : lista) {
            if (c.get("visitas").asInt() == 0) {
                JsonNode ficha =
                        json.readTree(pedir("/api/clientes/" + c.get("id").asText(), sesion).getBody());
                assertThat(ficha.get("gastoMedioCentimos").asInt()).isZero();
                return;
            }
        }
        // Si el seed no tiene ninguno sin visitas, no hay nada que comprobar.
    }

    @Test
    @DisplayName("un cliente que no existe da 404 con su mensaje")
    void clienteInexistente() throws Exception {
        ResponseEntity<String> r =
                pedir("/api/clientes/no-existe-este-id", sesionDe("admin@ssiomama.test"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(r.getBody()).get("error").asText()).isNotBlank();
    }

    /**
     * El hash de la contraseña de un cliente con cuenta no puede salir por aquí
     * ni por descuido.
     */
    @Test
    @DisplayName("no se publica ningun hash")
    void noSePublicaNingunHash() {
        String lista = pedir("/api/clientes?limite=100", sesionDe("admin@ssiomama.test")).getBody();

        assertThat(lista).doesNotContain("scrypt").doesNotContain("passwordHash");
    }
}
