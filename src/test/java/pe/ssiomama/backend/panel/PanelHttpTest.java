package pe.ssiomama.backend.panel;

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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La portada del panel y la torre de control.
 *
 * <p>Las dos cruzan cinco dominios para pintar sus cifras, así que lo que se
 * comprueba no es «devuelve 200» sino que <b>los números cuadran con la base</b>.
 * Una pantalla de resumen que suma mal es peor que no tenerla: se toman
 * decisiones mirándola sin volver a comprobar.
 *
 * <p>Las comprobaciones no fijan cifras a mano — se contrastan contra la misma
 * consulta hecha aparte — para que no caduquen al cambiar los datos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PanelHttpTest {

    private static final String CLAVE = "discoteca";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

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
        return conSesion;
    }

    private ResponseEntity<String> pedir(String ruta, HttpHeaders cabeceras) {
        return http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);
    }

    // ── Permisos ──────────────────────────────────────────────────

    @Test
    @DisplayName("sin sesion no se ve el panel")
    void sinSesion() {
        assertThat(pedir("/api/panel/resumen", new HttpHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(pedir("/api/panel/noche", new HttpHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * La portada enseña ingresos y la torre enseña taquilla. Ni el portero ni la
     * barra tienen por qué ver lo que factura el local.
     */
    @Test
    @DisplayName("puerta y barra no ven el resumen: 403, no 401")
    void soloElPanel() {
        assertThat(pedir("/api/panel/resumen", sesionDe("puerta@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pedir("/api/panel/noche", sesionDe("barra@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("direccion y gerencia si lo ven")
    void elPanelEntra() {
        assertThat(pedir("/api/panel/resumen", sesionDe("admin@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(pedir("/api/panel/resumen", sesionDe("sala@ssiomama.test")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ── Que las cifras cuadren ────────────────────────────────────

    @Test
    @DisplayName("los ingresos de 30 dias cuadran con los pedidos pagados")
    void ingresosCuadran() throws Exception {
        JsonNode r = json.readTree(
                pedir("/api/panel/resumen", sesionDe("admin@ssiomama.test")).getBody());

        // El corte se escribe en UTC a propósito. La columna «pagadoEn» es
        // «timestamp without time zone» y guarda instantes en UTC, pero now()
        // devuelve una hora con zona: al compararla con la columna, Postgres la
        // convierte a la zona de la sesión (Lima, UTC−5) y el corte se va cinco
        // horas atrás. Con los datos actuales eso metía dos pedidos de más y la
        // comprobación acusaba al código de un descuadre de S/ 122,85 que no
        // existía.
        long segunLaBase = jdbc.queryForObject(
                """
                select coalesce(sum("totalCentimos"), 0) from "Pedido"
                where estado = 'PAGADO'
                  and "pagadoEn" >= (now() at time zone 'UTC') - interval '30 days'
                """,
                Long.class);

        JsonNode ingresos = r.get("cifras").get(0);
        assertThat(ingresos.get("etiqueta").asText()).isEqualTo("Ingresos 30 días");
        assertThat(ingresos.get("valor").asLong()).isEqualTo(segunLaBase);
    }

    @Test
    @DisplayName("bajo minimo cuadra con los productos por debajo del umbral")
    void bajoMinimoCuadra() throws Exception {
        JsonNode r = json.readTree(
                pedir("/api/panel/resumen", sesionDe("admin@ssiomama.test")).getBody());

        long segunLaBase = jdbc.queryForObject(
                "select count(*) from \"Producto\" where activo = true and stock <= \"alertaStock\"",
                Long.class);

        assertThat(r.get("totalPorReponer").asLong()).isEqualTo(segunLaBase);
        // La portada enseña solo las primeras: el resto está en almacén.
        assertThat(r.get("hayQueReponer").size()).isLessThanOrEqualTo(6);
    }

    @Test
    @DisplayName("cajas sin cerrar cuadra con los arqueos abiertos")
    void cajasAbiertasCuadran() throws Exception {
        JsonNode r = json.readTree(
                pedir("/api/panel/resumen", sesionDe("admin@ssiomama.test")).getBody());

        long abiertas = jdbc.queryForObject(
                "select count(*) from \"SesionCaja\" where \"cerradaEn\" is null", Long.class);

        JsonNode cifra = r.get("cifras").get(3);
        assertThat(cifra.get("etiqueta").asText()).isEqualTo("Cajas sin cerrar");
        assertThat(cifra.get("valor").asLong()).isEqualTo(abiertas);
        // El tono lo decide el negocio, no el navegador.
        assertThat(cifra.get("tono").asText()).isEqualTo(abiertas == 0 ? "bien" : "mal");
    }

    @Test
    @DisplayName("la portada responde entera aunque no haya noche programada")
    void portadaCompleta() throws Exception {
        JsonNode r = json.readTree(
                pedir("/api/panel/resumen", sesionDe("admin@ssiomama.test")).getBody());

        // Cuatro cifras siempre, con o sin eventos por delante.
        assertThat(r.get("cifras").size()).isEqualTo(4);
        assertThat(r.get("hayQueReponer").isArray()).isTrue();
        assertThat(r.get("ultimosPedidos").isArray()).isTrue();

        for (JsonNode c : r.get("cifras")) {
            assertThat(c.get("etiqueta").asText()).isNotBlank();
            assertThat(c.get("pie").asText()).isNotBlank();
            assertThat(c.get("tono").asText()).isIn("acento", "neutro", "bien", "mal");
        }
    }

    // ── Torre de control ──────────────────────────────────────────

    @Test
    @DisplayName("la torre responde algo aunque no haya noche")
    void torreSiempreResponde() throws Exception {
        JsonNode t = json.readTree(
                pedir("/api/panel/noche", sesionDe("admin@ssiomama.test")).getBody());

        assertThat(t.has("hayNoche")).isTrue();
        assertThat(t.get("ritmoDeEntrada").isArray()).isTrue();
        assertThat(t.get("reservados").isArray()).isTrue();
    }

    /**
     * La cifra que de verdad se mira: lo que deja en barra cada persona que
     * entró. Mucha gente que no consume es aforo ocupado sin ingreso.
     */
    @Test
    @DisplayName("el gasto por asistente se divide entre quien entro, no entre quien compro")
    void gastoPorAsistente() throws Exception {
        JsonNode t = json.readTree(
                pedir("/api/panel/noche", sesionDe("admin@ssiomama.test")).getBody());

        if (!t.get("hayNoche").asBoolean()) {
            return;
        }

        long dentro = t.get("dentro").asLong();
        long barra = t.get("barraCentimos").asLong();
        long porAsistente = t.get("gastoPorAsistenteCentimos").asLong();

        assertThat(porAsistente).isEqualTo(dentro == 0 ? 0 : barra / dentro);
        // Nadie puede haber entrado más veces que entradas se vendieron.
        assertThat(dentro).isLessThanOrEqualTo(t.get("vendidas").asLong());
    }

    @Test
    @DisplayName("la taquilla de la noche cuadra con sus pedidos pagados")
    void taquillaCuadra() throws Exception {
        JsonNode t = json.readTree(
                pedir("/api/panel/noche", sesionDe("admin@ssiomama.test")).getBody());

        if (!t.get("hayNoche").asBoolean()) {
            return;
        }

        long segunLaBase = jdbc.queryForObject(
                """
                select coalesce(sum(p."totalCentimos"), 0)
                from "Pedido" p join "Evento" e on e.id = p."eventoId"
                where e.slug = ? and p.estado = 'PAGADO'
                """,
                Long.class,
                t.get("slug").asText());

        assertThat(t.get("taquillaCentimos").asLong()).isEqualTo(segunLaBase);
    }

    /**
     * El reparto por puerta tiene que sumar exactamente la gente que está
     * dentro. Si no suma, o falta una puerta o hay accesos contados dos veces,
     * y en los dos casos la cifra de aforo deja de valer.
     */
    @Test
    @DisplayName("el reparto por puerta suma el total de gente dentro")
    void elRepartoPorPuertaSuma() throws Exception {
        JsonNode t = json.readTree(
                pedir("/api/panel/noche", sesionDe("admin@ssiomama.test")).getBody());

        if (!t.get("hayNoche").asBoolean()) {
            return;
        }

        long sumado = 0;
        for (JsonNode p : t.get("porPuerta")) {
            assertThat(p.get("puerta").asText()).isNotBlank();
            sumado += p.get("personas").asLong();
        }

        assertThat(sumado).isEqualTo(t.get("dentro").asLong());
    }
}
