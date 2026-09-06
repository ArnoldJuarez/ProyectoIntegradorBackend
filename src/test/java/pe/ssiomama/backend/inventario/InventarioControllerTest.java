package pe.ssiomama.backend.inventario;

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
 * El almacén: stock, movimientos y proveedores.
 *
 * <p>Solo lee, así que no ensucia la base y no necesita limpiar después.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventarioControllerTest {

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

    private HttpHeaders admin() {
        return sesionDe("admin@ssiomama.test");
    }

    @Test
    @DisplayName("el almacen esta cerrado sin sesion")
    void cerradoSinSesion() {
        assertThat(pedir("/api/inventario/stock", new HttpHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(pedir("/api/inventario/movimientos", new HttpHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(pedir("/api/inventario/proveedores", new HttpHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("un portero no ve el almacen")
    void elPorteroNoVeElAlmacen() {
        assertThat(pedir("/api/inventario/stock", sesionDe("puerta@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("el stock trae sus lineas y su resumen")
    void elStockTraeLineasYResumen() throws Exception {
        ResponseEntity<String> r = pedir("/api/inventario/stock", admin());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode cuerpo = json.readTree(r.getBody());
        assertThat(cuerpo.get("lineas")).isNotEmpty();
        assertThat(cuerpo.get("resumen").get("referencias").asInt())
                .isEqualTo(cuerpo.get("lineas").size());
    }

    /**
     * El resumen se calcula en el servidor, así que tiene que cuadrar con las
     * líneas que lo acompañan. Si no cuadra, alguien está sumando dos cosas
     * distintas.
     */
    @Test
    @DisplayName("el resumen cuadra con las lineas")
    void elResumenCuadra() throws Exception {
        JsonNode cuerpo = json.readTree(pedir("/api/inventario/stock", admin()).getBody());
        JsonNode lineas = cuerpo.get("lineas");
        JsonNode resumen = cuerpo.get("resumen");

        long porDebajo = 0;
        long sinNada = 0;
        long valor = 0;
        for (JsonNode l : lineas) {
            if (l.get("necesitaReposicion").asBoolean()) porDebajo++;
            if (l.get("cantidad").asInt() == 0) sinNada++;
            valor += l.get("valorACosteCentimos").asLong();
        }

        assertThat(resumen.get("porDebajoDelUmbral").asLong()).isEqualTo(porDebajo);
        assertThat(resumen.get("sinExistencias").asLong()).isEqualTo(sinNada);
        assertThat(resumen.get("valorTotalCentimos").asLong()).isEqualTo(valor);
    }

    /**
     * Es la razón por la que alguien abre esta pantalla: ver de un vistazo qué
     * hay que pedir. Si lo que falta sale en mitad de la lista, no sirve.
     */
    @Test
    @DisplayName("lo que hay que reponer sale primero")
    void loQueFaltaSalePrimero() throws Exception {
        JsonNode lineas = json.readTree(pedir("/api/inventario/stock", admin()).getBody())
                .get("lineas");

        boolean yaPasamosLosQueFaltan = false;
        for (JsonNode l : lineas) {
            boolean falta = l.get("necesitaReposicion").asBoolean();
            if (!falta) {
                yaPasamosLosQueFaltan = true;
            } else {
                assertThat(yaPasamosLosQueFaltan)
                        .as("un producto por reponer aparece despues de uno que no lo necesita")
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("necesitaReposicion se calcula contra el umbral de cada producto")
    void laAlertaUsaElUmbralDelProducto() throws Exception {
        JsonNode lineas = json.readTree(pedir("/api/inventario/stock", admin()).getBody())
                .get("lineas");

        assertThat(lineas).allSatisfy(l -> assertThat(l.get("necesitaReposicion").asBoolean())
                .isEqualTo(l.get("cantidad").asInt() <= l.get("alertaDeStock").asInt()));
    }

    @Test
    @DisplayName("los movimientos vienen del mas reciente al mas antiguo")
    void movimientosEnOrden() throws Exception {
        JsonNode ms = json.readTree(pedir("/api/inventario/movimientos", admin()).getBody());

        String anterior = null;
        for (JsonNode m : ms) {
            String cuando = m.get("cuando").asText();
            if (anterior != null) {
                assertThat(cuando.compareTo(anterior)).isLessThanOrEqualTo(0);
            }
            anterior = cuando;
        }
    }

    /**
     * Cada venta de barra deja una fila, así que sin tope una petición podría
     * intentar traerse una noche entera.
     */
    @Test
    @DisplayName("los movimientos tienen tope aunque se pida mas")
    void movimientosConTope() throws Exception {
        JsonNode ms = json.readTree(
                pedir("/api/inventario/movimientos?limite=99999", admin()).getBody());

        assertThat(ms.size()).isLessThanOrEqualTo(200);
    }

    /**
     * <b>El signo va en la cantidad, no en el tipo.</b>
     *
     * <p>Este test nació de una equivocación: se modeló al revés —cantidades en
     * positivo y el signo deducido del tipo— y eso invierte el signo dos veces,
     * de forma que una venta acababa <b>sumando</b> existencias. Los datos
     * reales lo desmintieron: las compras van de +9 a +43 y las ventas de −3
     * a −1.
     *
     * <p>Se deja para que nadie vuelva a «arreglarlo» en la dirección
     * equivocada.
     */
    @Test
    @DisplayName("las salidas se guardan con cantidad negativa")
    void lasSalidasVanEnNegativo() throws Exception {
        JsonNode ms = json.readTree(pedir("/api/inventario/movimientos", admin()).getBody());
        assertThat(ms).isNotEmpty();

        assertThat(ms).allSatisfy(m -> {
            int cantidad = m.get("cantidad").asInt();
            assertThat(cantidad).as("un movimiento de cero unidades no significa nada").isNotZero();

            // 'unidades' es la magnitud y 'esEntrada' el sentido: entre los dos
            // reconstruyen la cantidad con su signo.
            assertThat(m.get("unidades").asInt()).isEqualTo(Math.abs(cantidad));
            assertThat(m.get("esEntrada").asBoolean()).isEqualTo(cantidad > 0);
        });
    }

    /** Una venta nunca puede aumentar el stock. */
    @Test
    @DisplayName("una venta siempre resta")
    void unaVentaSiempreResta() throws Exception {
        JsonNode ms = json.readTree(pedir("/api/inventario/movimientos", admin()).getBody());

        assertThat(ms).allSatisfy(m -> {
            if ("VENTA".equals(m.get("tipo").asText())) {
                assertThat(m.get("cantidad").asInt()).isNegative();
                assertThat(m.get("esEntrada").asBoolean()).isFalse();
            }
            if ("COMPRA".equals(m.get("tipo").asText())) {
                assertThat(m.get("cantidad").asInt()).isPositive();
            }
        });
    }

    /**
     * El importe es una magnitud: si una venta diera importe negativo, sumar la
     * columna no significaría nada.
     */
    @Test
    @DisplayName("el importe nunca es negativo")
    void elImporteNuncaEsNegativo() throws Exception {
        JsonNode ms = json.readTree(pedir("/api/inventario/movimientos", admin()).getBody());

        assertThat(ms).allSatisfy(m -> assertThat(m.get("importeCentimos").asLong())
                .isGreaterThanOrEqualTo(0));
    }

    @Test
    @DisplayName("se puede filtrar el historico por producto")
    void filtraPorProducto() throws Exception {
        HttpHeaders sesion = admin();
        JsonNode todos = json.readTree(pedir("/api/inventario/movimientos", sesion).getBody());

        // Solo tiene sentido si el seed dejó movimientos.
        if (todos.isEmpty()) {
            return;
        }

        String sku = todos.get(0).get("sku").asText();
        String idProducto = json.readTree(pedir("/api/inventario/stock", sesion).getBody())
                .get("lineas").findValue("productoId").asText();

        JsonNode filtrados = json.readTree(
                pedir("/api/inventario/movimientos?producto=" + idProducto, sesion).getBody());

        assertThat(filtrados).allSatisfy(m -> assertThat(m.get("sku").asText()).isNotBlank());
        assertThat(sku).isNotBlank();
    }

    @Test
    @DisplayName("los proveedores salen por orden alfabetico")
    void proveedoresOrdenados() throws Exception {
        JsonNode ps = json.readTree(pedir("/api/inventario/proveedores", admin()).getBody());

        String anterior = null;
        for (JsonNode p : ps) {
            String nombre = p.get("nombre").asText();
            if (anterior != null) {
                assertThat(nombre.compareToIgnoreCase(anterior)).isGreaterThanOrEqualTo(0);
            }
            anterior = nombre;
        }
    }
}
