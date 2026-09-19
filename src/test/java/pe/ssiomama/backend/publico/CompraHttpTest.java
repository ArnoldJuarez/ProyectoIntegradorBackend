package pe.ssiomama.backend.publico;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import pe.ssiomama.backend.NocheDePrueba;

/**
 * Comprar desde fuera: sin cuenta, por HTTP, como lo hace el navegador.
 *
 * <p>Escribe de verdad —un pedido creado y pagado por HTTP no se puede
 * deshacer con un {@code @Transactional} de test, porque el servidor corre en
 * otro hilo y con su propia transacción—, así que {@link #limpiar()} borra lo
 * que crea. Los correos llevan una marca propia para poder hacerlo con
 * precisión.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompraHttpTest {

    /**
     * La noche contra la que se compra, creada por esta prueba.
     *
     * <p>No se usa una del seed. La venta de un tipo de entrada se corta al
     * empezar la noche, así que atarse a una fecha fija le pone caducidad a la
     * prueba. Esta va por HTTP y no puede controlar el reloj como hace
     * {@code CompraTest}, así que se fabrica su propia noche futura.
     */
    private static String NOCHE;
    private static final String MARCA = "compra-http-%d@ejemplo.test";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void crearLaNoche(@Autowired JdbcTemplate jdbc) {
        // Por si una ejecución anterior se cortó a mitad.
        NocheDePrueba.limpiar(jdbc);
        String eventoId = NocheDePrueba.crear(jdbc, "Noche de prueba · compra por HTTP");
        NocheDePrueba.anadirTipoDeEntrada(jdbc, eventoId, "General", 1800, 400);
        NocheDePrueba.anadirTipoDeEntrada(jdbc, eventoId, "VIP", 3500, 60);
        NOCHE = NocheDePrueba.slugDe(jdbc, eventoId);
    }

    @AfterAll
    static void borrarLaNoche(@Autowired JdbcTemplate jdbc) {
        NocheDePrueba.limpiar(jdbc);
    }

    @AfterEach
    void limpiar() {
        String comoLosMios = "compra-http-%@ejemplo.test";

        /*
         * Lo primero, devolver el cupo. Borrar las lineas sin esto deja
         * «vendidas» contando entradas que ya no existen, y el aforo del evento
         * se va encogiendo un poco en cada ejecucion del test — un descuadre
         * silencioso que solo se nota cuando alguien pregunta por que quedan
         * menos entradas de las que deberia.
         */
        jdbc.update(
                """
                update "TipoEntrada" t set vendidas = t.vendidas - x.cuantas
                from (
                  select l."tipoEntradaId" as tipo, sum(l.cantidad) as cuantas
                  from "LineaPedido" l
                  join "Pedido" p on p.id = l."pedidoId"
                  join "Cliente" c on c.id = p."clienteId"
                  where c.correo like ?
                  group by l."tipoEntradaId"
                ) x
                where t.id = x.tipo
                """,
                comoLosMios);
        jdbc.update(
                """
                delete from "Entrada" where "pedidoId" in (
                  select p.id from "Pedido" p join "Cliente" c on c.id = p."clienteId"
                  where c.correo like ?)
                """,
                comoLosMios);
        jdbc.update(
                """
                delete from "LineaPedido" where "pedidoId" in (
                  select p.id from "Pedido" p join "Cliente" c on c.id = p."clienteId"
                  where c.correo like ?)
                """,
                comoLosMios);
        jdbc.update(
                """
                delete from "Pedido" where "clienteId" in (
                  select id from "Cliente" where correo like ?)
                """,
                comoLosMios);
        jdbc.update(
                """
                delete from "MovimientoFidelidad" where "clienteId" in (
                  select id from "Cliente" where correo like ?)
                """,
                comoLosMios);
        jdbc.update(
                """
                delete from "Interaccion" where "clienteId" in (
                  select id from "Cliente" where correo like ?)
                """,
                comoLosMios);
        jdbc.update("delete from \"Cliente\" where correo like ?", comoLosMios);
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    private String idDeTipo(String slug, String nombre) {
        return jdbc.queryForObject(
                """
                select t.id from "TipoEntrada" t
                join "Evento" v on v.id = t."eventoId"
                where v.slug = ? and t.nombre = ?
                """,
                String.class,
                slug,
                nombre);
    }

    private ResponseEntity<String> comprar(String cuerpo) {
        HttpHeaders cabecera = new HttpHeaders();
        cabecera.setContentType(MediaType.APPLICATION_JSON);
        // Sin cookie a propósito: comprar no exige cuenta.
        return http.postForEntity(
                "/api/publico/pedidos", new HttpEntity<>(cuerpo, cabecera), String.class);
    }

    private String peticionDe(String tipoId, int cantidad) {
        return """
                {"slug":"%s","nombre":"Ana","apellido":"Prueba","email":"%s",
                 "telefono":"999888777","lineas":[{"tipoDeEntradaId":"%s","cantidad":%d}]}
                """
                .formatted(NOCHE, MARCA.formatted(System.nanoTime()), tipoId, cantidad);
    }

    // ── El camino bueno ───────────────────────────────────────────

    @Test
    @DisplayName("se compra sin cuenta y se paga, y salen las entradas con su QR")
    void compraCompleta() throws Exception {
        var creado = comprar(peticionDe(idDeTipo(NOCHE, "General"), 2));
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode resguardo = json.readTree(creado.getBody());
        String codigo = resguardo.get("codigo").asText();
        assertThat(codigo).startsWith("DSC-");
        assertThat(resguardo.get("totalCentimos").asInt())
                .isEqualTo(
                        resguardo.get("subtotalCentimos").asInt()
                                + resguardo.get("comisionCentimos").asInt());

        // Antes de pagar no hay entradas, y la respuesta lo dice sin rodeos.
        JsonNode sinPagar = json.readTree(
                http.getForObject("/api/publico/pedidos/" + codigo, String.class));
        assertThat(sinPagar.get("pagado").asBoolean()).isFalse();
        assertThat(sinPagar.get("entradas")).isEmpty();

        var pagado = http.postForEntity(
                "/api/publico/pedidos/" + codigo + "/pagar", null, String.class);
        assertThat(pagado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(pagado.getBody()).get("entradas").asInt()).isEqualTo(2);

        JsonNode conEntradas = json.readTree(
                http.getForObject("/api/publico/pedidos/" + codigo, String.class));
        assertThat(conEntradas.get("pagado").asBoolean()).isTrue();
        assertThat(conEntradas.get("entradas")).hasSize(2);
        for (JsonNode e : conEntradas.get("entradas")) {
            // El código completo: es el QR que se enseña en la puerta.
            assertThat(e.get("codigo").asText()).contains(".");
            assertThat(e.get("yaSeUso").asBoolean()).isFalse();
        }
    }

    /**
     * El código del pedido es lo único que hace falta para abrir esta página, y
     * son seis caracteres. Por eso la respuesta no puede llevar datos personales:
     * quien acierte un código ajeno no debe llevarse el correo ni el teléfono de
     * nadie.
     */
    @Test
    @DisplayName("la pagina publica de un pedido no filtra datos del comprador")
    void noFiltraDatosPersonales() throws Exception {
        String correo = MARCA.formatted(System.nanoTime());
        String cuerpo = """
                {"slug":"%s","nombre":"Ana","apellido":"Prueba","email":"%s",
                 "telefono":"999888777","lineas":[{"tipoDeEntradaId":"%s","cantidad":1}]}
                """
                .formatted(NOCHE, correo, idDeTipo(NOCHE, "General"));

        String codigo = json.readTree(comprar(cuerpo).getBody()).get("codigo").asText();
        http.postForEntity("/api/publico/pedidos/" + codigo + "/pagar", null, String.class);

        String publico = http.getForObject("/api/publico/pedidos/" + codigo, String.class);

        assertThat(publico).doesNotContain(correo);
        assertThat(publico).doesNotContain("999888777");
        assertThat(publico).doesNotContain("metodoDePago");
        // El nombre sí sale: va impreso en la entrada y es de quien la mira.
        assertThat(publico).contains("Ana");
    }

    // ── Lo que se rechaza ─────────────────────────────────────────

    @Test
    @DisplayName("un rechazo del negocio es 409 y trae el motivo escrito")
    void rechazoLegible() throws Exception {
        String vip = idDeTipo(NOCHE, "VIP");
        int maximo = jdbc.queryForObject(
                "select \"maxPorPedido\" from \"TipoEntrada\" where id = ?", Integer.class, vip);

        var r = comprar(peticionDe(vip, maximo + 1));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(r.getBody()).get("error").asText())
                .contains("Máximo %d".formatted(maximo));
    }

    @Test
    @DisplayName("un pedido sin lineas es 400, no 409")
    void sinLineas() throws Exception {
        String cuerpo = """
                {"slug":"%s","nombre":"Ana","email":"%s","telefono":"999888777","lineas":[]}
                """
                .formatted(NOCHE, MARCA.formatted(System.nanoTime()));

        var r = comprar(cuerpo);

        // 400 porque la petición está mal formada, no porque el mundo cambió.
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(r.getBody()).get("error").asText()).contains("al menos una");
    }

    @Test
    @DisplayName("un correo que no lo es se rechaza con su mensaje")
    void correoMalo() throws Exception {
        String cuerpo = """
                {"slug":"%s","nombre":"Ana","email":"esto-no-es-un-correo",
                 "telefono":"999888777","lineas":[{"tipoDeEntradaId":"%s","cantidad":1}]}
                """
                .formatted(NOCHE, idDeTipo(NOCHE, "General"));

        var r = comprar(cuerpo);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(r.getBody()).get("error").asText()).contains("correo");
    }

    @Test
    @DisplayName("un pedido que no existe da 404")
    void pedidoInexistente() {
        assertThat(http.getForEntity("/api/publico/pedidos/DSC-NADA", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
