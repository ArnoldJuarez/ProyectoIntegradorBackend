package pe.ssiomama.backend.caja;

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
 * Los arqueos de caja. Solo lee, así que no ensucia la base.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CajaControllerTest {

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
    @DisplayName("la caja esta cerrada sin sesion")
    void cerradaSinSesion() {
        assertThat(pedir("/api/caja/arqueos", new HttpHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("un portero no ve la caja")
    void elPorteroNoVeLaCaja() {
        assertThat(pedir("/api/caja/arqueos", sesionDe("puerta@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("el listado trae los arqueos con su responsable")
    void elListadoTraeArqueos() throws Exception {
        ResponseEntity<String> r = pedir("/api/caja/arqueos", admin());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode arqueos = json.readTree(r.getBody());
        assertThat(arqueos).isNotEmpty();
        assertThat(arqueos.get(0).get("responsable").asText()).isNotBlank();
        assertThat(arqueos.get(0).get("sala").asText()).isNotBlank();
    }

    @Test
    @DisplayName("vienen del mas reciente al mas antiguo")
    void enOrden() throws Exception {
        JsonNode arqueos = json.readTree(pedir("/api/caja/arqueos", admin()).getBody());

        String anterior = null;
        for (JsonNode a : arqueos) {
            String cuando = a.get("abiertaEn").asText();
            if (anterior != null) {
                assertThat(cuando.compareTo(anterior)).isLessThanOrEqualTo(0);
            }
            anterior = cuando;
        }
    }

    /**
     * El efectivo cobrado se deriva de dos importes guardados. Si no cuadra,
     * alguien está restando cosas distintas.
     */
    @Test
    @DisplayName("el efectivo cobrado es lo esperado menos el fondo de apertura")
    void elEfectivoCobradoCuadra() throws Exception {
        JsonNode arqueos = json.readTree(pedir("/api/caja/arqueos", admin()).getBody());

        assertThat(arqueos).allSatisfy(a -> assertThat(a.get("efectivoCobradoCentimos").asInt())
                .isEqualTo(a.get("esperadoCentimos").asInt() - a.get("aperturaCentimos").asInt()));
    }

    /**
     * <b>El error que se coló y que se detectó mirando los datos.</b>
     *
     * <p>Esa resta se llamó «vendido» en un primer momento. En un turno real con
     * apertura de 200, esperado 1130 y 4671 cobrados con tarjeta, daba 930 —solo
     * el efectivo— mientras que lo vendido de verdad eran 5601. El panel habría
     * dicho que ese turno facturó seis veces menos.
     *
     * <p>Este test fija que lo esperado en caja solo cuenta el efectivo, que es
     * lo correcto: una tarjeta no llega al cajón.
     */
    @Test
    @DisplayName("lo esperado en caja solo cuenta el efectivo, no las tarjetas")
    void loEsperadoSoloCuentaElEfectivo() throws Exception {
        HttpHeaders sesion = admin();
        JsonNode arqueos = json.readTree(pedir("/api/caja/arqueos", sesion).getBody());

        for (JsonNode a : arqueos) {
            if (a.get("estaAbierta").asBoolean()) {
                continue;
            }
            JsonNode ficha = json.readTree(
                    pedir("/api/caja/arqueos/" + a.get("id").asText(), sesion).getBody());

            // Lo que se espera en el cajon = fondo + efectivo cobrado, y nada mas.
            assertThat((long) a.get("aperturaCentimos").asInt() + ficha.get("efectivoCentimos").asLong())
                    .as("lo esperado en caja incluye medios de pago que no llegan al cajon")
                    .isEqualTo(a.get("esperadoCentimos").asInt());
        }
    }

    /**
     * Una caja abierta todavía no se ha contado, así que no puede tener
     * diferencia ni «cuadrar». Confundir «cuadró» con «aún no se sabe» es la
     * forma de dar por buena una caja que nadie revisó.
     */
    @Test
    @DisplayName("una caja abierta no tiene recuento ni cuadra")
    void unaCajaAbiertaNoCuadra() throws Exception {
        JsonNode arqueos = json.readTree(pedir("/api/caja/arqueos", admin()).getBody());

        assertThat(arqueos).allSatisfy(a -> {
            if (a.get("estaAbierta").asBoolean()) {
                assertThat(a.get("cerradaEn").isNull()).isTrue();
                assertThat(a.get("contadoCentimos").isNull()).isTrue();
                assertThat(a.get("cuadro").asBoolean()).isFalse();
            } else {
                assertThat(a.get("cerradaEn").isNull()).isFalse();
            }
        });
    }

    /** La diferencia guardada tiene que ser contado − esperado, tal cual. */
    @Test
    @DisplayName("la diferencia es lo contado menos lo esperado")
    void laDiferenciaEsContadoMenosEsperado() throws Exception {
        JsonNode arqueos = json.readTree(pedir("/api/caja/arqueos", admin()).getBody());

        assertThat(arqueos).allSatisfy(a -> {
            if (!a.get("contadoCentimos").isNull()) {
                int esperado = a.get("esperadoCentimos").asInt();
                int contado = a.get("contadoCentimos").asInt();
                assertThat(a.get("diferenciaCentimos").asInt()).isEqualTo(contado - esperado);
                assertThat(a.get("cuadro").asBoolean()).isEqualTo(contado - esperado == 0);
            }
        });
    }

    @Test
    @DisplayName("la ficha desglosa lo cobrado por metodo de pago")
    void laFichaDesglosaPorMetodo() throws Exception {
        HttpHeaders sesion = admin();
        String id = json.readTree(pedir("/api/caja/arqueos", sesion).getBody())
                .get(0).get("id").asText();

        JsonNode ficha = json.readTree(pedir("/api/caja/arqueos/" + id, sesion).getBody());

        assertThat(ficha.get("arqueo").get("id").asText()).isEqualTo(id);
        assertThat(ficha.get("porMetodoDePago").isArray()).isTrue();
        assertThat(ficha.get("cobros").asInt()).isGreaterThanOrEqualTo(0);
    }

    /**
     * Separar el efectivo del resto es lo que explica un descuadre: si faltan
     * cien soles pero casi todo se cobró con tarjeta, el problema no está en el
     * cajón.
     */
    @Test
    @DisplayName("el efectivo y los otros medios suman el desglose entero")
    void efectivoYOtrosSumanElTotal() throws Exception {
        HttpHeaders sesion = admin();
        String id = json.readTree(pedir("/api/caja/arqueos", sesion).getBody())
                .get(0).get("id").asText();

        JsonNode ficha = json.readTree(pedir("/api/caja/arqueos/" + id, sesion).getBody());

        long delDesglose = 0;
        for (JsonNode t : ficha.get("porMetodoDePago")) {
            delDesglose += t.get("totalCentimos").asLong();
            // El efectivo es el único que llega al cajón.
            assertThat(t.get("esEfectivo").asBoolean()).isEqualTo("EFECTIVO".equals(t.get("metodo").asText()));
        }

        assertThat(ficha.get("efectivoCentimos").asLong() + ficha.get("otrosMediosCentimos").asLong())
                .isEqualTo(delDesglose);
    }

    @Test
    @DisplayName("un arqueo que no existe da 404 con su mensaje")
    void arqueoInexistente() throws Exception {
        ResponseEntity<String> r = pedir("/api/caja/arqueos/no-existe", admin());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(r.getBody()).get("error").asText()).isNotBlank();
    }

    @Test
    @DisplayName("el listado tiene tope aunque se pida mas")
    void elListadoTieneTope() throws Exception {
        JsonNode arqueos = json.readTree(
                pedir("/api/caja/arqueos?limite=99999", admin()).getBody());

        assertThat(arqueos.size()).isLessThanOrEqualTo(100);
    }
}
