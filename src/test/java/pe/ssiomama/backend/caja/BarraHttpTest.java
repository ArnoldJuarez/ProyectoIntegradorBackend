package pe.ssiomama.backend.caja;

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
 * La barra por HTTP: quién entra, y que cobrar de verdad deja todo cuadrado.
 *
 * <p>Arranca un servidor real y no MockMvc por lo mismo que el resto: el reenvío
 * interno a {@code /error} vuelve a pasar por la cadena de seguridad y convierte
 * un 403 legítimo en un 401 si la configuración no lo contempla.
 *
 * <p>Escribe de verdad, así que {@link #limpiar()} deshace lo que crea: borra
 * las ventas y sus movimientos, devuelve el stock y cierra las cajas. Es más
 * trabajo que un {@code @Transactional}, pero es la única forma de probar la
 * ruta entera pasando por Tomcat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BarraHttpTest {

    private static final String CLAVE = "discoteca";

    /** Marca en la referencia del movimiento, para poder limpiar con precisión. */
    private static final String MARCA = "Venta ";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

    /** Las ventas creadas por este test, para borrarlas al terminar. */
    private final List<Long> creadas = new java.util.ArrayList<>();

    @AfterEach
    void limpiar() {
        for (Long ventaId : creadas) {
            // Primero devolver el stock, leyendo lo que descontó cada movimiento.
            jdbc.update(
                    """
                    update "Producto" p set stock = p.stock - m.cantidad
                    from "MovimientoStock" m
                    where m."productoId" = p.id and m.referencia = ?
                    """,
                    MARCA + ventaId);
            jdbc.update("delete from \"MovimientoStock\" where referencia = ?", MARCA + ventaId);
            jdbc.update("delete from \"LineaVentaBarra\" where \"ventaBarraId\" = ?", ventaId);
            jdbc.update("delete from \"VentaBarra\" where id = ?", ventaId);
        }
        creadas.clear();

        // Y las cajas que quedaron abiertas. Se borran en vez de cerrarse: son
        // turnos que nunca existieron, y dejarlos cerrados ensuciaría el
        // histórico de arqueos del panel con cajas de prueba.
        jdbc.update(
                """
                delete from "SesionCaja" where "cerradaEn" is null and "usuarioId" in (
                  select id from "Usuario" where correo in ('barra@ssiomama.test', 'admin@ssiomama.test'))
                """);
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

    private ResponseEntity<String> pantalla(HttpHeaders cabeceras) {
        return http.exchange(
                "/api/barra", HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);
    }

    private Long unProducto() {
        return jdbc.queryForObject(
                "select id from \"Producto\" where activo = true order by nombre limit 1",
                Long.class);
    }

    // ── Permisos ──────────────────────────────────────────────────

    @Test
    @DisplayName("sin sesion no se ve la barra")
    void sinSesion() {
        assertThat(pantalla(new HttpHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("quien sirve copas si entra")
    void laBarraEntra() {
        assertThat(pantalla(sesionDe("barra@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("direccion y gerencia cubren la barra")
    void elPanelTambien() {
        assertThat(pantalla(sesionDe("admin@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(pantalla(sesionDe("sala@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * El portero no cobra, y quien sirve copas no escanea. Son dos pantallas y
     * dos permisos: si uno abriera el otro, el rol no serviría de nada.
     */
    @Test
    @DisplayName("el portero no cobra, y la barra no escanea")
    void cadaUnoLoSuyo() {
        assertThat(pantalla(sesionDe("puerta@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(http.exchange(
                                "/api/puerta/noche",
                                HttpMethod.GET,
                                new HttpEntity<>(sesionDe("barra@ssiomama.test")),
                                String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** La barra cobra, pero no ve los datos personales de los clientes. */
    @Test
    @DisplayName("la barra no ve el panel de clientes")
    void laBarraNoVeClientes() {
        assertThat(http.exchange(
                                "/api/clientes",
                                HttpMethod.GET,
                                new HttpEntity<>(sesionDe("barra@ssiomama.test")),
                                String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── La pantalla ───────────────────────────────────────────────

    @Test
    @DisplayName("la pantalla trae el catalogo agrupado y en orden fijo")
    void elCatalogoLlegaAgrupado() throws Exception {
        JsonNode p = json.readTree(pantalla(sesionDe("barra@ssiomama.test")).getBody());

        assertThat(p.get("catalogo").isArray()).isTrue();
        assertThat(p.get("catalogo")).isNotEmpty();

        String anterior = "";
        for (JsonNode grupo : p.get("catalogo")) {
            String categoria = grupo.get("categoria").asText();
            // Orden alfabético estable: el de barra pulsa donde estaba, sin mirar.
            assertThat(categoria).isGreaterThan(anterior);
            anterior = categoria;

            assertThat(grupo.get("productos")).isNotEmpty();
            for (JsonNode producto : grupo.get("productos")) {
                assertThat(producto.get("precioCentimos").asInt()).isPositive();
                assertThat(producto.get("nombre").asText()).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("sin caja abierta la pantalla lo dice, no falla")
    void sinCajaAbierta() throws Exception {
        JsonNode p = json.readTree(pantalla(sesionDe("barra@ssiomama.test")).getBody());

        // El turno va a nulo y la pantalla enseña el formulario de apertura.
        assertThat(p.get("turno").isNull()).isTrue();
        assertThat(p.get("catalogo")).isNotEmpty();
    }

    // ── Cobrar de punta a punta ───────────────────────────────────

    @Test
    @DisplayName("abrir, cobrar y cerrar deja el arqueo cuadrado")
    void elTurnoCompleto() throws Exception {
        HttpHeaders sesion = sesionDe("barra@ssiomama.test");

        var abierta = http.postForEntity(
                "/api/barra/turno",
                new HttpEntity<>("{\"fondoCentimos\":20000}", sesion),
                String.class);
        assertThat(abierta.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode turno = json.readTree(abierta.getBody());
        Long arqueoId = turno.get("id").asLong();
        assertThat(turno.get("esperadoCentimos").asInt()).isEqualTo(20_000);

        Long producto = unProducto();
        var cobro = http.postForEntity(
                "/api/barra/ventas",
                new HttpEntity<>(
                        """
                        {"arqueoId":%s,"metodoDePago":"EFECTIVO",
                         "lineas":[{"productoId":%s,"cantidad":2}]}
                        """
                                .formatted(arqueoId, producto),
                        sesion),
                String.class);
        assertThat(cobro.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode venta = json.readTree(cobro.getBody());
        creadas.add(venta.get("id").asLong());

        int total = venta.get("totalCentimos").asInt();
        assertThat(total).isPositive();
        assertThat(venta.get("esperadoEnCajon").asInt()).isEqualTo(20_000 + total);

        // Se cuenta justo lo esperado: la caja cuadra.
        var cierre = http.postForEntity(
                "/api/barra/cierre",
                new HttpEntity<>(
                        "{\"arqueoId\":\"%s\",\"contadoCentimos\":%d}"
                                .formatted(arqueoId, 20_000 + total),
                        sesion),
                String.class);

        assertThat(cierre.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode cerrado = json.readTree(cierre.getBody());
        assertThat(cerrado.get("diferenciaCentimos").asInt()).isZero();
        assertThat(cerrado.get("cuadro").asBoolean()).isTrue();

        // Y como quedó cerrada, hay que borrarla a mano: limpiar() solo se lleva
        // las abiertas.
        jdbc.update("delete from \"VentaBarra\" where \"sesionCajaId\" = ?", arqueoId);
        jdbc.update("delete from \"SesionCaja\" where id = ?", arqueoId);
    }

    @Test
    @DisplayName("un rechazo de caja es 409 con el motivo escrito")
    void rechazoLegible() throws Exception {
        HttpHeaders sesion = sesionDe("barra@ssiomama.test");

        var r = http.postForEntity(
                "/api/barra/ventas",
                new HttpEntity<>(
                        """
                        {"arqueoId":999999999,"metodoDePago":"EFECTIVO",
                         "lineas":[{"productoId":%d,"cantidad":1}]}
                        """
                                .formatted(unProducto()),
                        sesion),
                String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(r.getBody()).get("error").asText()).contains("no existe");
    }

    @Test
    @DisplayName("una venta sin lineas es 400, no 409")
    void ventaVacia() throws Exception {
        var r = http.postForEntity(
                "/api/barra/ventas",
                new HttpEntity<>(
                        "{\"arqueoId\":999999999,\"metodoDePago\":\"EFECTIVO\",\"lineas\":[]}",
                        sesionDe("barra@ssiomama.test")),
                String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(r.getBody()).get("error").asText()).contains("vacía");
    }
}
