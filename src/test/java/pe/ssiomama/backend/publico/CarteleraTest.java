package pe.ssiomama.backend.publico;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.ssiomama.backend.NocheDePrueba;
import pe.ssiomama.backend.eventos.EstadoDeEvento;
import pe.ssiomama.backend.eventos.Evento;
import pe.ssiomama.backend.eventos.EventoRepository;

/**
 * La cartelera pública, con servidor real y <b>sin ninguna sesión</b>.
 *
 * <p>Lo que se prueba aquí no es que funcione: es que <b>no se escape nada</b>.
 * Un endpoint público es la única superficie del sistema que puede leer
 * cualquiera desde internet, así que lo que importa es lo que NO devuelve.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CarteleraTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * La agenda solo enseña noches por venir, así que la prueba se trae la suya.
     *
     * <p>Sin esto la suite caduca: las noches del seed tienen fecha fija y el día
     * que el calendario las pasa, la agenda se queda vacía con razón y estas
     * comprobaciones fallan acusando al código de no devolver nada. Ya ocurrió
     * —el 3 de octubre, con la última noche publicada el 27 de septiembre— y el
     * código estaba bien.
     */
    @BeforeEach
    void programarUnaNoche() {
        String id = NocheDePrueba.crear(jdbc, "Noche de prueba de cartelera");
        NocheDePrueba.anadirTipoDeEntrada(jdbc, id, "General", 2500, 100);
    }

    @AfterEach
    void limpiar() {
        NocheDePrueba.limpiar(jdbc);
    }

    @Test
    @DisplayName("la agenda se lee sin haber iniciado sesion")
    void seLeeSinSesion() {
        ResponseEntity<String> r = http.getForEntity("/api/publico/agenda", String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isNotNull().contains("titulo");
    }

    /**
     * Los campos que nunca pueden salir. Si alguien reutiliza el DTO del panel
     * aquí, esto lo caza.
     *
     * <p>El precio sí sale, y tiene que salir: es el «desde S/ 12» de la
     * tarjeta. Lo que no puede verse es cuánto ha ingresado la casa ni cuántas
     * entradas lleva vendidas en crudo.
     */
    @Test
    @DisplayName("NO publica lo recaudado ni las ventas en crudo")
    void noPublicaDatosInternos() {
        String agenda = http.getForObject("/api/publico/agenda", String.class);

        assertThat(agenda)
                .doesNotContain("recaudado")
                .doesNotContain("vendidas")
                .doesNotContain("aforoALaVenta")
                .doesNotContain("cantidad");

        // Y lo que sí es público, que esté.
        assertThat(agenda).contains("precioDesdeCentimos").contains("porcentajeVendido");
    }

    @Test
    @DisplayName("NO publica los borradores")
    void noPublicaBorradores() {
        String agenda = http.getForObject("/api/publico/agenda", String.class);
        assertThat(agenda).doesNotContain("BORRADOR");

        // Y por si acaso, contra la base: ninguno de los slugs en borrador
        // aparece en la respuesta.
        List<String> borradores = eventos.findAll().stream()
                .filter(e -> e.getEstado() == EstadoDeEvento.BORRADOR)
                .map(Evento::getSlug)
                .toList();
        assertThat(borradores).allSatisfy(slug -> assertThat(agenda).doesNotContain(slug));
    }

    @Test
    @DisplayName("una noche en borrador responde 404, igual que una inexistente")
    void borradorDa404() {
        var borrador = eventos.findAll().stream()
                .filter(e -> e.getEstado() == EstadoDeEvento.BORRADOR)
                .findFirst();

        // Solo tiene sentido si el seed dejó alguno.
        borrador.ifPresent(e -> assertThat(
                        http.getForEntity("/api/publico/evento/" + e.getSlug(), String.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));

        assertThat(http.getForEntity("/api/publico/evento/no-existe", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("la ficha trae lo que hace falta para decidir y comprar")
    void laFichaTraeLoNecesario() {
        var ficha = http.getForEntity("/api/publico/evento/viernes-urbano", String.class);

        assertThat(ficha.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ficha.getBody())
                .contains("Viernes Urbano")
                .contains("puertasA")
                .contains("edadMinima")
                .contains("entradas")
                .contains("disponibles")
                // El cartel, que es la mitad del reclamo de una noche.
                .contains("cartel");
    }

    /** El panel sigue cerrado: abrir lo público no ha abierto lo demás. */
    @Test
    @DisplayName("abrir la cartelera no ha abierto el panel")
    void elPanelSigueCerrado() {
        assertThat(http.getForEntity("/api/eventos", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/api/inventario/productos", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
