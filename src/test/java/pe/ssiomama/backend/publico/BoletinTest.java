package pe.ssiomama.backend.publico;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
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
import pe.ssiomama.backend.clientes.Cliente;
import pe.ssiomama.backend.clientes.ClienteRepository;
import pe.ssiomama.backend.clientes.InteraccionRepository;

/**
 * El alta en el boletín, contra un servidor real y sin sesión.
 *
 * <h2>Estos tests escriben en la base de verdad</h2>
 *
 * <p>Es la misma {@code discoteca} que usa todo el mundo, así que se recoge lo
 * que se ensucia. Cada caso usa un correo único bajo el dominio
 * {@code @ejemplo.test} —reservado por la RFC 2606 justo para esto, nunca va a
 * ser de nadie— y al terminar la clase se borran todas las filas creadas.
 *
 * <p>Sin ese borrado, cada ejecución dejaría cuatro clientes de mentira dentro,
 * y en un mes los segmentos del CRM estarían contando fantasmas.
 *
 * <p>Lo correcto de verdad sería una base aparte para tests. Mientras no la
 * haya, limpiar es el mínimo aceptable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BoletinTest {

    /**
     * Marca de las filas de esta clase.
     *
     * <p>El dominio {@code ejemplo.test} está reservado por la RFC 2606, y el
     * prefijo distingue lo que crea <b>este</b> archivo de lo que crean los
     * demás.
     *
     * <p>El prefijo no es cosmético: cuando la limpieza borraba todo lo que
     * acabara en {@code @ejemplo.test}, esta clase intentaba borrar clientes de
     * {@code ReservadosTest} que tenían reservas apuntándoles, y la clave ajena
     * —{@code RESTRICT}— lo impedía. El orden en que corren las clases no está
     * garantizado, así que cada una recoge únicamente lo suyo.
     */
    private static final String MARCA = "boletin-";

    private static final String DOMINIO_DE_PRUEBA = "@ejemplo.test";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private InteraccionRepository interacciones;

    private static String correoNuevo() {
        return MARCA + UUID.randomUUID() + DOMINIO_DE_PRUEBA;
    }

    /**
     * Borra lo que dejó esta clase, y solo lo suyo.
     *
     * <p>Las interacciones van primero: son hijas del cliente, y aunque su clave
     * ajena tenga {@code CASCADE}, borrarlas a mano deja claro qué se está
     * tocando en vez de confiar en un efecto lateral del esquema.
     */
    @AfterAll
    static void limpiar(
            @Autowired ClienteRepository clientes,
            @Autowired InteraccionRepository interacciones) {

        List<Cliente> deMentira = clientes.findAll().stream()
                .filter(c -> c.getEmail().startsWith(MARCA)
                        && c.getEmail().endsWith(DOMINIO_DE_PRUEBA))
                .toList();

        for (Cliente cliente : deMentira) {
            interacciones.deleteAll(
                    interacciones.findAll().stream()
                            .filter(i -> i.getCliente().getId().equals(cliente.getId()))
                            .toList());
        }
        clientes.deleteAll(deMentira);
    }

    private ResponseEntity<String> apuntar(String nombre, String email) {
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity(
                "/api/publico/boletin",
                new HttpEntity<>(
                        "{\"nombre\":\"%s\",\"email\":\"%s\"}".formatted(nombre, email), json),
                String.class);
    }

    @Test
    @DisplayName("alguien nuevo se apunta sin tener cuenta")
    void alguienNuevoSeApunta() {
        String email = correoNuevo();

        assertThat(apuntar("Ana", email).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Cliente creado = clientes.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(creado.getNombre()).isEqualTo("Ana");
        assertThat(creado.isAceptaMarketing()).isTrue();
        // Un consentimiento sin fecha no vale.
        assertThat(creado.getConsintioEn()).isNotNull();
    }

    /**
     * El correo se guarda en minúsculas para que la misma persona no entre dos
     * veces por escribirlo distinto.
     */
    @Test
    @DisplayName("el correo se normaliza a minusculas")
    void normalizaElCorreo() {
        String email = correoNuevo();

        apuntar("Ana", email.toUpperCase());

        assertThat(clientes.findByEmailIgnoreCase(email)).isPresent();
    }

    /**
     * Apuntarse dos veces no crea dos clientes ni pisa el nombre: quien ya
     * compró puede tener nombre y apellido correctos de entonces.
     */
    @Test
    @DisplayName("apuntarse dos veces no duplica ni pisa el nombre")
    void apuntarseDosVecesNoDuplica() {
        String email = correoNuevo();

        apuntar("Ana", email);
        assertThat(apuntar("XXX", email).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Cliente cliente = clientes.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(cliente.getNombre()).isEqualTo("Ana");
    }

    /**
     * La respuesta tiene que ser idéntica esté o no ya apuntado: distinguirlo
     * dejaría sondear desde fuera qué correos están en la base.
     */
    @Test
    @DisplayName("no delata si un correo ya estaba")
    void noDelataSiYaEstaba() {
        String email = correoNuevo();

        String primera = apuntar("Ana", email).getBody();
        String segunda = apuntar("Ana", email).getBody();

        assertThat(primera).isEqualTo(segunda);
    }

    @Test
    @DisplayName("rechaza correos y nombres invalidos")
    void rechazaDatosInvalidos() {
        assertThat(apuntar("Ana", "no-es-un-correo").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(apuntar("", correoNuevo()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Sin tope, cualquiera puede mandar un nombre de un megabyte y quedaría
     * guardado tal cual. Es un endpoint que puede llamar todo internet.
     */
    @Test
    @DisplayName("rechaza un nombre absurdamente largo")
    void rechazaNombreEnorme() {
        assertThat(apuntar("A".repeat(5000), correoNuevo()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
