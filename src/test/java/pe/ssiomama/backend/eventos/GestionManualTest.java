package pe.ssiomama.backend.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.eventos.GestionDeArtistas.DatosDeArtista;
import pe.ssiomama.backend.eventos.GestionDeEventos.DatosDeNoche;
import pe.ssiomama.backend.eventos.GestionDeEventos.DatosDeTipo;

/**
 * Lo que se gestiona a mano además de crear y editar: el video de una noche,
 * borrar las que no llegaron a nada y el catálogo de artistas.
 *
 * <p>Contra la base real y en una transacción que se deshace al terminar.
 */
@SpringBootTest
@Transactional
class GestionManualTest {

    @Autowired
    private GestionDeEventos gestion;

    @Autowired
    private GestionDeArtistas catalogo;

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private ArtistaRepository artistas;

    @Autowired
    private SalaRepository salas;

    @Autowired
    private JdbcTemplate jdbc;

    private DatosDeNoche noche(String titulo, EstadoDeEvento estado, String video, List<String> artistas) {
        Instant puertas = Instant.parse("2027-04-09T02:00:00Z");
        return new DatosDeNoche(titulo, "De prueba.", salas.findAllByOrderByAforoDesc().getFirst().getId(),
                puertas, puertas.plus(Duration.ofHours(6)), 18, null, estado, null, video, artistas);
    }

    // ── Video ─────────────────────────────────────────────────────

    @Test
    @DisplayName("una noche guarda su video: un enlace https o un archivo subido aquí")
    void guardaElVideo() {
        var conYoutube = gestion.crear(noche("Prueba video YouTube", EstadoDeEvento.BORRADOR,
                "https://www.youtube.com/watch?v=abc123", List.of()));
        var conArchivo = gestion.crear(noche("Prueba video archivo", EstadoDeEvento.BORRADOR,
                "/api/publico/videos/prueba-123.mp4", List.of()));

        assertThat(eventos.findBySlug(conYoutube.getSlug()).orElseThrow().getVideo())
                .isEqualTo("https://www.youtube.com/watch?v=abc123");
        assertThat(eventos.findBySlug(conArchivo.getSlug()).orElseThrow().getVideo())
                .isEqualTo("/api/publico/videos/prueba-123.mp4");
    }

    @Test
    @DisplayName("lo que no es https ni un archivo nuestro no entra: acabaría en el src de la web")
    void rechazaVideosRaros() {
        for (String raro : List.of("javascript:alert(1)", "http://inseguro.com/v.mp4", "/api/publico/../secreto")) {
            assertThatThrownBy(() -> gestion.crear(noche("Prueba video raro", EstadoDeEvento.BORRADOR, raro, List.of())))
                    .as(raro)
                    .isInstanceOf(EventoRechazado.class)
                    .hasMessageContaining("https");
        }
    }

    // ── Borrar ────────────────────────────────────────────────────

    @Test
    @DisplayName("un borrador sin historia se borra, con sus entradas y su cartel")
    void borraUnBorrador() {
        String artista = jdbc.queryForObject("select id from \"Artista\" order by nombre limit 1", String.class);
        var creada = gestion.crear(noche("Prueba para borrar", EstadoDeEvento.BORRADOR, null, List.of(artista)));
        gestion.anadirTipo(creada.getSlug(), new DatosDeTipo("General", null, 2000, 50, 4, 0, true));
        String id = creada.getId();

        gestion.borrar(creada.getSlug());
        eventos.flush();

        assertThat(eventos.findBySlug(creada.getSlug())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from \"TipoEntrada\" where \"eventoId\" = ?", Long.class, id))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from \"EventoArtista\" where \"eventoId\" = ?", Long.class, id))
                .isZero();
    }

    @Test
    @DisplayName("una publicada no se borra aunque no haya vendido: su enlace puede estar compartido")
    void noBorraUnaPublicada() {
        var publicada = gestion.crear(noche("Prueba publicada", EstadoDeEvento.PUBLICADO, null, List.of()));

        assertThatThrownBy(() -> gestion.borrar(publicada.getSlug()))
                .isInstanceOf(EventoRechazado.class)
                .hasMessageContaining("cancelada");
    }

    @Test
    @DisplayName("una noche con pedidos no se borra ni cancelada: se queda como historia")
    void noBorraLoQueTieneHistoria() {
        String conPedidos = jdbc.queryForObject("""
                select e.slug from "Evento" e
                where exists (select 1 from "Pedido" p where p."eventoId" = e.id)
                limit 1
                """, String.class);
        jdbc.update("update \"Evento\" set estado = 'CANCELADO' where slug = ?", conPedidos);

        assertThatThrownBy(() -> gestion.borrar(conPedidos))
                .isInstanceOf(EventoRechazado.class)
                .hasMessageContaining("pedidos");
    }

    // ── Artistas ──────────────────────────────────────────────────

    @Test
    @DisplayName("un artista nuevo entra al catálogo y se puede editar")
    void altaDeArtista() {
        Artista nuevo = catalogo.crear(new DatosDeArtista("Prueba Sonido Andino", "Cumbia", "@sonido.andino", null));
        catalogo.editar(nuevo.getId(), new DatosDeArtista("Prueba Sonido Andino", "Cumbia andina", "sonido.andino",
                "Banda de Cayma."));

        artistas.flush();

        var fila = jdbc.queryForMap("select genero, instagram, bio from \"Artista\" where id = ?", nuevo.getId());
        assertThat(fila.get("genero")).isEqualTo("Cumbia andina");
        assertThat(fila.get("instagram")).isEqualTo("sonido.andino");
        assertThat(fila.get("bio")).isEqualTo("Banda de Cayma.");
    }

    @Test
    @DisplayName("el nombre no se repite, sin distinguir mayúsculas")
    void nombreRepetido() {
        String existente = jdbc.queryForObject("select nombre from \"Artista\" order by nombre limit 1", String.class);

        assertThatThrownBy(() -> catalogo.crear(new DatosDeArtista(existente.toUpperCase(), null, null, null)))
                .isInstanceOf(EventoRechazado.class)
                .hasMessageContaining("Ya hay");
    }

    @Test
    @DisplayName("quien ya tocó no se borra; quien no, sí")
    void borrarArtistas() {
        String conNoches = jdbc.queryForObject("select \"artistaId\" from \"EventoArtista\" limit 1", String.class);
        assertThatThrownBy(() -> catalogo.borrar(conNoches))
                .isInstanceOf(EventoRechazado.class)
                .hasMessageContaining("cartel");

        Artista nuevo = catalogo.crear(new DatosDeArtista("Prueba Sin Noches", null, null, null));
        artistas.flush();
        catalogo.borrar(nuevo.getId());
        artistas.flush();
        assertThat(jdbc.queryForObject("select count(*) from \"Artista\" where nombre = 'Prueba Sin Noches'", Long.class))
                .isZero();
    }
}
