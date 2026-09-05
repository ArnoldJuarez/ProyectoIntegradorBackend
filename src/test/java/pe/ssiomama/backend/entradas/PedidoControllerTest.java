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
 * Pedidos y entradas, contra los datos reales.
 *
 * <p>Solo lectura: la compra y el escaneo van aparte. Lo que se comprueba aquí
 * es que el modelo cuadra con las 139 entradas que ya existen, antes de escribir
 * nada sobre él.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PedidoControllerTest {

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

    private HttpHeaders admin() {
        return sesionDe("admin@ssiomama.test");
    }

    private ResponseEntity<String> pedir(String ruta, HttpHeaders cabeceras) {
        return http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);
    }

    @Test
    @DisplayName("los pedidos estan cerrados sin sesion")
    void cerradoSinSesion() {
        assertThat(pedir("/api/pedidos", new HttpHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("un portero no ve los pedidos")
    void elPorteroNoVeLosPedidos() {
        assertThat(pedir("/api/pedidos", sesionDe("puerta@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("el listado trae los pedidos con su cliente y su noche")
    void elListadoTraePedidos() throws Exception {
        JsonNode ps = json.readTree(pedir("/api/pedidos", admin()).getBody());

        assertThat(ps).isNotEmpty();
        assertThat(ps.get(0).get("codigo").asText()).isNotBlank();
        assertThat(ps.get(0).get("cliente").asText()).isNotBlank();
        assertThat(ps.get(0).get("noche").asText()).isNotBlank();
    }

    /**
     * El total guardado tiene que ser subtotal más comisión. Si no cuadra,
     * alguien calculó el importe de dos formas distintas.
     */
    @Test
    @DisplayName("el total es el subtotal mas la comision")
    void elTotalCuadra() throws Exception {
        HttpHeaders sesion = admin();
        JsonNode ps = json.readTree(pedir("/api/pedidos?limite=20", sesion).getBody());

        for (JsonNode p : ps) {
            JsonNode f = json.readTree(
                    pedir("/api/pedidos/" + p.get("codigo").asText(), sesion).getBody());

            assertThat(f.get("subtotalCentimos").asInt() + f.get("comisionCentimos").asInt())
                    .isEqualTo(f.get("pedido").get("totalCentimos").asInt());
        }
    }

    /** Las líneas tienen que sumar el subtotal, o falta o sobra una. */
    @Test
    @DisplayName("las lineas suman el subtotal")
    void lasLineasSumanElSubtotal() throws Exception {
        HttpHeaders sesion = admin();
        JsonNode ps = json.readTree(pedir("/api/pedidos?limite=20", sesion).getBody());

        for (JsonNode p : ps) {
            JsonNode f = json.readTree(
                    pedir("/api/pedidos/" + p.get("codigo").asText(), sesion).getBody());

            long suma = 0;
            for (JsonNode l : f.get("lineas")) {
                assertThat(l.get("importeCentimos").asLong())
                        .isEqualTo((long) l.get("cantidad").asInt()
                                * l.get("precioUnitarioCentimos").asInt());
                suma += l.get("importeCentimos").asLong();
            }
            assertThat(suma).isEqualTo(f.get("subtotalCentimos").asInt());
        }
    }

    /**
     * Un pedido pagado tiene que tener sus entradas emitidas: es lo que el
     * cliente enseña en la puerta. Uno pendiente todavía no.
     */
    @Test
    @DisplayName("un pedido pagado tiene entradas emitidas")
    void unPedidoPagadoTieneEntradas() throws Exception {
        HttpHeaders sesion = admin();
        JsonNode ps = json.readTree(pedir("/api/pedidos?limite=30", sesion).getBody());

        boolean seComproboAlguno = false;
        for (JsonNode p : ps) {
            if (!"PAID".equals(p.get("estado").asText())) {
                continue;
            }
            JsonNode f = json.readTree(
                    pedir("/api/pedidos/" + p.get("codigo").asText(), sesion).getBody());

            assertThat(f.get("entradas")).as("un pedido pagado sin entradas").isNotEmpty();
            seComproboAlguno = true;
        }
        assertThat(seComproboAlguno).as("el seed tiene pedidos pagados").isTrue();
    }

    /**
     * Una entrada marcada como usada tiene que tener su escaneo, y al revés. Si
     * se desincronizan, el portero ve «válida» algo que ya pasó.
     */
    @Test
    @DisplayName("usada y escaneada van de la mano")
    void usadaYEscaneadaVanJuntas() throws Exception {
        HttpHeaders sesion = admin();
        JsonNode ps = json.readTree(pedir("/api/pedidos?limite=30", sesion).getBody());

        for (JsonNode p : ps) {
            JsonNode f = json.readTree(
                    pedir("/api/pedidos/" + p.get("codigo").asText(), sesion).getBody());

            for (JsonNode e : f.get("entradas")) {
                boolean usada = "USED".equals(e.get("estado").asText());
                boolean escaneada = e.get("yaSeUso").asBoolean();
                assertThat(escaneada)
                        .as("entrada %s: estado y escaneo no coinciden", e.get("referencia").asText())
                        .isEqualTo(usada);
                if (escaneada) {
                    assertThat(e.get("escaneadaEn").isNull()).isFalse();
                }
            }
        }
    }

    @Test
    @DisplayName("el aforo cuenta las emitidas y las que ya entraron")
    void elAforoCuenta() throws Exception {
        JsonNode aforo = json.readTree(
                pedir("/api/pedidos/aforo/aniversario-ssiomama", admin()).getBody());

        assertThat(aforo.get("emitidas").asInt()).isPositive();
        // No puede haber entrado más gente de la que tiene entrada.
        assertThat(aforo.get("dentro").asLong())
                .isLessThanOrEqualTo(aforo.get("emitidas").asInt());
    }

    @Test
    @DisplayName("un pedido que no existe da 404 con su mensaje")
    void pedidoInexistente() throws Exception {
        ResponseEntity<String> r = pedir("/api/pedidos/NO-EXISTE", admin());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(r.getBody()).get("error").asText()).isNotBlank();
    }
}
