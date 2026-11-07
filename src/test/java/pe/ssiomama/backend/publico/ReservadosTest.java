package pe.ssiomama.backend.publico;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
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
import pe.ssiomama.backend.clientes.Cliente;
import pe.ssiomama.backend.clientes.ClienteRepository;
import pe.ssiomama.backend.clientes.InteraccionRepository;
import pe.ssiomama.backend.reservados.Reserva;
import pe.ssiomama.backend.reservados.ReservaRepository;

/**
 * Solicitud de reservado, con servidor real y sin sesión.
 *
 * <p>Como {@code BoletinTest}, escribe en la base de verdad y recoge lo que
 * ensucia: los clientes bajo {@code @ejemplo.test} y las reservas que crearon.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservadosTest {

    /**
     * Marca de las filas de esta clase. Ver la nota en {@code BoletinTest}: cada
     * archivo recoge solo lo suyo, porque el orden en que corren no está
     * garantizado y aquí quedan reservas apuntando a los clientes.
     */
    private static final String MARCA = "reserva-";

    private static final String DOMINIO_DE_PRUEBA = "@ejemplo.test";

    /**
     * La noche contra la que se reserva, creada por esta prueba.
     *
     * <p>No se usa una del seed. El endpoint rechaza reservas de una noche que
     * ya empezó —con razón—, así que atarse a una fecha fija le pone caducidad
     * a la prueba: el día que el calendario pasó {@code viernes-urbano}, seis
     * comprobaciones se pusieron en rojo sin que nadie tocara el código.
     *
     * <p>Esta va por HTTP y no puede controlar el reloj como hace
     * {@code CompraTest}, así que se fabrica su propia noche con fecha futura y
     * se borra al terminar.
     */
    private static String NOCHE;

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ClienteRepository clientes;

    @BeforeAll
    static void crearLaNoche(@Autowired JdbcTemplate jdbc) {
        // Por si una ejecución anterior se cortó a mitad.
        NocheDePrueba.limpiar(jdbc);
        NOCHE = NocheDePrueba.slugDe(jdbc, NocheDePrueba.crear(jdbc, "Noche de prueba · reservados"));
    }

    @AfterAll
    static void limpiar(
            @Autowired ClienteRepository clientes,
            @Autowired ReservaRepository reservas,
            @Autowired InteraccionRepository interacciones) {

        List<Cliente> deMentira = clientes.findAll().stream()
                .filter(c -> c.getEmail().startsWith(MARCA)
                        && c.getEmail().endsWith(DOMINIO_DE_PRUEBA))
                .toList();

        for (Cliente cliente : deMentira) {
            // Las reservas primero: apuntan al cliente.
            reservas.deleteAll(reservas.findAll().stream()
                    .filter(r -> r.getCliente().getId().equals(cliente.getId()))
                    .toList());
            interacciones.deleteAll(interacciones.findAll().stream()
                    .filter(i -> i.getCliente().getId().equals(cliente.getId()))
                    .toList());
        }
        clientes.deleteAll(deMentira);
    }

    @AfterAll
    static void borrarLaNoche(@Autowired JdbcTemplate jdbc) {
        NocheDePrueba.limpiar(jdbc);
    }

    private static String correoNuevo() {
        return MARCA + UUID.randomUUID() + DOMINIO_DE_PRUEBA;
    }

    private ResponseEntity<String> pedir(Long mesaId, int personas, String email) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        String cuerpo =
                """
                {"slug":"%s","mesaId":%s,"personas":%d,"notas":"",
                 "nombre":"Ana","apellido":"Prueba","email":"%s","telefono":"951866748"}
                """
                        .formatted(NOCHE, mesaId, personas, email);
        return http.postForEntity(
                "/api/publico/reservas", new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    /** Una mesa libre de esa noche, con sus plazas. */
    private JsonNode unaMesaLibre() throws Exception {
        JsonNode mesas = json.readTree(
                http.getForObject("/api/publico/reservados/" + NOCHE, String.class));
        for (JsonNode m : mesas) {
            if (m.get("libre").asBoolean()) {
                return m;
            }
        }
        throw new IllegalStateException("El seed no dejó ninguna mesa libre para " + NOCHE);
    }

    @Test
    @DisplayName("los reservados de una noche se consultan sin sesion")
    void seConsultanSinSesion() throws Exception {
        ResponseEntity<String> r =
                http.getForEntity("/api/publico/reservados/" + NOCHE, String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode mesas = json.readTree(r.getBody());
        assertThat(mesas).isNotEmpty();
        assertThat(mesas.get(0).has("libre")).isTrue();
        assertThat(mesas.get(0).has("plazas")).isTrue();
    }

    @Test
    @DisplayName("una noche que no existe da 404")
    void nocheInexistente() {
        assertThat(http.getForEntity("/api/publico/reservados/no-existe", String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("se pide un reservado y queda como solicitud, no confirmado")
    void sePideYQuedaComoSolicitud() throws Exception {
        JsonNode mesa = unaMesaLibre();

        ResponseEntity<String> r = pedir(mesa.get("id").asLong(), 2, correoNuevo());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody()).contains("Te llamamos");
    }

    /**
     * La mesa deja de aparecer como libre en cuanto alguien la pide, aunque la
     * solicitud no esté confirmada: quien va a llamar para cerrarla no puede
     * encontrarse con que la pidió otro entretanto.
     */
    @Test
    @DisplayName("una mesa pedida deja de estar libre")
    void mesaPedidaDejaDeEstarLibre() throws Exception {
        JsonNode mesa = unaMesaLibre();
        Long id = mesa.get("id").asLong();

        pedir(id, 2, correoNuevo());

        JsonNode mesas = json.readTree(
                http.getForObject("/api/publico/reservados/" + NOCHE, String.class));
        for (JsonNode m : mesas) {
            if (m.get("id").asLong() == id) {
                assertThat(m.get("libre").asBoolean()).isFalse();
                return;
            }
        }
        throw new AssertionError("La mesa desapareció del listado en vez de marcarse ocupada");
    }

    /**
     * El caso que en el sistema anterior devolvía un 500: la restricción de la
     * base salta y hay que convertirla en un mensaje entendible.
     */
    @Test
    @DisplayName("pedir dos veces la misma mesa da 409, no un error del servidor")
    void mesaYaPedidaDa409() throws Exception {
        JsonNode mesa = unaMesaLibre();
        Long id = mesa.get("id").asLong();

        assertThat(pedir(id, 2, correoNuevo()).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> segunda = pedir(id, 2, correoNuevo());
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(segunda.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    @DisplayName("no se puede pedir una mesa para mas gente de la que cabe")
    void masGenteDeLaQueCabe() throws Exception {
        JsonNode mesa = unaMesaLibre();
        int plazas = mesa.get("plazas").asInt();

        ResponseEntity<String> r = pedir(mesa.get("id").asLong(), plazas + 1, correoNuevo());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).contains("máximo");
    }

    /**
     * Los mensajes escritos para una persona tienen que llegar a la persona.
     *
     * <p>Spring, por su cuenta, responde a un {@code ResponseStatusException}
     * con un cuerpo genérico y tira el motivo: el cliente recibía «Conflict» en
     * lugar de saber qué hacer. Lo arregla {@code ManejadorDeErrores}.
     */
    @Test
    @DisplayName("los errores traen un mensaje que se puede enseñar")
    void losErroresTraenMensaje() throws Exception {
        JsonNode mesa = unaMesaLibre();
        String cuerpo = pedir(mesa.get("id").asLong(), mesa.get("plazas").asInt() + 1, correoNuevo())
                .getBody();

        assertThat(json.readTree(cuerpo).get("error").asText())
                .isNotBlank()
                .doesNotContain("Conflict");
    }

    @Test
    @DisplayName("hace falta telefono para confirmar la mesa")
    void hacenFaltaLosDatos() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        String sinTelefono =
                """
                {"slug":"%s","mesaId":"x","personas":2,"notas":"",
                 "nombre":"Ana","apellido":"","email":"a%s","telefono":""}
                """
                        .formatted(NOCHE, DOMINIO_DE_PRUEBA);

        assertThat(http.postForEntity(
                                "/api/publico/reservas",
                                new HttpEntity<>(sinTelefono, cabeceras),
                                String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("la solicitud queda apuntada en el historial del cliente")
    void quedaEnElHistorial() throws Exception {
        String email = correoNuevo();
        JsonNode mesa = unaMesaLibre();

        pedir(mesa.get("id").asLong(), 3, email);

        Cliente cliente = clientes.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(cliente.getTelefono()).isEqualTo("951866748");
        assertThat(cliente.getNombreCompleto()).isEqualTo("Ana Prueba");
    }

    /** Una solicitud de la web nunca nace confirmada: la mesa no queda apartada. */
    @Test
    @DisplayName("la reserva nace en SOLICITADA y sin deposito")
    void naceSinConfirmar(@Autowired ReservaRepository reservas) throws Exception {
        String email = correoNuevo();
        JsonNode mesa = unaMesaLibre();

        Long id = json.readTree(pedir(mesa.get("id").asLong(), 2, email).getBody())
                .get("id")
                .asLong();

        Reserva creada = reservas.findById(id).orElseThrow();
        assertThat(creada.getEstado().name()).isEqualTo("SOLICITADA");
        assertThat(creada.getDepositoCentimos()).isZero();
    }
}
