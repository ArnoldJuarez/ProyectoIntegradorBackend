package pe.ssiomama.backend.ia;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * El programador de noches sin llamar al modelo: lo que recibe (el contexto) y
 * lo que pasa cuando una persona aprueba lo que propone (el aplicador).
 *
 * <p>La salida del modelo se escribe a mano aquí, con los mismos errores que
 * comete de verdad: un artista que no está, una fecha pasada, un día ya ocupado,
 * cupos que pasan del aforo.
 */
@SpringBootTest
@Transactional
class ProgramadorDeNochesTest {

    @Autowired
    private Recolectores recolectores;

    @Autowired
    private AplicadorDePropuestas aplicador;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    private final ObjectMapper json = new ObjectMapper();

    /** Un día sin noches, lejos de los datos de muestra. */
    private static final LocalDate LIBRE = LocalDate.of(2027, 5, 14);

    private JsonNode propuesta(String nochesJson) throws Exception {
        return json.readTree("{\"razonamiento\": \"Prueba.\", \"noches\": [" + nochesJson + "]}");
    }

    private String noche(String titulo, LocalDate fecha, String artistas, String entradas) {
        return """
                {"titulo": "%s", "fecha": "%s", "puertas": "21:00", "cierre": "03:00",
                 "descripcion": "Una noche de prueba.", "vestimenta": "Casual", "edadMinima": 16,
                 "artistas": [%s], "entradas": [%s], "motivo": "Porque sí."}
                """.formatted(titulo, fecha, artistas, entradas);
    }

    private String artistaReal() {
        return jdbc.queryForObject("select nombre from \"Artista\" order by nombre limit 1", String.class);
    }

    // ── Lo que recibe ─────────────────────────────────────────────

    @Test
    @DisplayName("el contexto trae fechas libres, lo ya programado y las plantillas reales")
    void contexto() {
        String contexto = recolectores.programacion().orElseThrow();

        assertThat(contexto)
                .contains("Fechas libres en las próximas")
                .contains("Ya programado en ese periodo")
                .contains("Cómo funciona cada día de la semana")
                .contains("Artistas del catálogo")
                .contains("Entradas que se suelen poner")
                .contains("Promociones de la casa")
                // «1 noches», pero no «41 noches».
                .doesNotContainPattern("(?<!\\d)1 noches");
    }

    @Test
    @DisplayName("un día que ya tiene noche no sale como libre")
    void noOfreceDiasOcupados() {
        String contexto = recolectores.programacion().orElseThrow();
        String libres = contexto.substring(contexto.indexOf("Fechas libres"), contexto.indexOf("Ya programado"));

        jdbc.queryForList("""
                select "empiezaEn" from "Evento"
                where estado <> 'CANCELADO' and "empiezaEn" > now() at time zone 'utc'
                  and "empiezaEn" < (now() at time zone 'utc') + interval '30 days'
                """, LocalDateTime.class)
                .forEach(t -> {
                    String dia = Club.nocheDe(t.toInstant(ZoneOffset.UTC)).toString();
                    assertThat(libres).as(dia).doesNotContain("- " + dia + " ");
                });
    }

    // ── Lo que pasa al aprobar ────────────────────────────────────

    @Test
    @DisplayName("aprobar crea la noche en borrador, con su cartel y sus entradas")
    void creaLaNocheEnBorrador() throws Exception {
        var r = aplicador.aplicar(TipoDePropuesta.EVENTO, propuesta(noche("Prueba Programador", LIBRE,
                "\"" + artistaReal() + "\"",
                """
                {"nombre": "General", "descripcion": "Con una consumición.", "precioCentimos": 2500,
                 "cantidad": 200, "maximoPorPedido": 6},
                {"nombre": "Lista mujeres", "descripcion": "Hasta las 11:30 p. m.", "precioCentimos": 0,
                 "cantidad": 100, "maximoPorPedido": 5}
                """)), null);
        em.flush();

        assertThat(r.aplicada()).isTrue();
        assertThat(r.enlaces()).singleElement().satisfies(e -> {
            assertThat(e.texto()).isEqualTo("Prueba Programador");
            assertThat(e.ruta()).startsWith("/panel/eventos/");
        });

        var fila = jdbc.queryForMap("""
                select e.id, e.estado::text as estado, e."edadMinima", e."imagenPortada"
                from "Evento" e where e.titulo = 'Prueba Programador'
                """);
        assertThat(fila.get("estado")).isEqualTo("BORRADOR");
        assertThat(fila.get("imagenPortada")).isNull();
        // La propuesta pedía 16: la casa es para mayores de edad.
        assertThat(fila.get("edadMinima")).isEqualTo(18);
        assertThat(jdbc.queryForObject("select count(*) from \"TipoEntrada\" where \"eventoId\" = ?",
                Long.class, fila.get("id"))).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from \"EventoArtista\" where \"eventoId\" = ? and \"cabezaCartel\"",
                Long.class, fila.get("id"))).isEqualTo(1);
    }

    @Test
    @DisplayName("lo que no cuadra se salta con un aviso y el resto se crea")
    void avisaDeLoQueNoCuadra() throws Exception {
        String ocupado = jdbc.queryForObject("""
                select to_char(("empiezaEn" at time zone 'UTC') at time zone 'America/Lima' - interval '6 hours', 'YYYY-MM-DD')
                from "Evento" where estado = 'PUBLICADO' and "empiezaEn" > now() at time zone 'utc'
                order by "empiezaEn" limit 1
                """, String.class);

        var r = aplicador.aplicar(TipoDePropuesta.EVENTO, propuesta(String.join(",",
                noche("Prueba Pasada", LocalDate.of(2025, 1, 10), "", ""),
                noche("Prueba Ocupada", LocalDate.parse(ocupado), "", ""),
                noche("Prueba Valida", LIBRE, "\"DJ Que No Existe\"",
                        """
                        {"nombre": "General", "descripcion": "", "precioCentimos": 2000, "cantidad": 450, "maximoPorPedido": 6},
                        {"nombre": "VIP", "descripcion": "", "precioCentimos": 5000, "cantidad": 300, "maximoPorPedido": 4}
                        """))), null);
        em.flush();

        assertThat(r.aplicada()).isTrue();
        assertThat(r.enlaces()).extracting(AplicadorDePropuestas.Enlace::texto).containsExactly("Prueba Valida");
        assertThat(r.avisos())
                .anySatisfy(a -> assertThat(a).contains("Prueba Pasada").contains("ya pasó"))
                .anySatisfy(a -> assertThat(a).contains("Prueba Ocupada").contains("ya tiene"))
                .anySatisfy(a -> assertThat(a).contains("DJ Que No Existe").contains("catálogo"))
                .anySatisfy(a -> assertThat(a).contains("VIP").contains("aforo"));

        // 450 + lo que quedaba del aforo, nunca más que la sala.
        Integer aforo = jdbc.queryForObject("select max(aforo) from \"Sala\"", Integer.class);
        Integer ofertadas = jdbc.queryForObject("""
                select sum(t.cantidad) from "TipoEntrada" t join "Evento" e on e.id = t."eventoId"
                where e.titulo = 'Prueba Valida'
                """, Integer.class);
        assertThat(ofertadas).isEqualTo(aforo);
    }

    @Test
    @DisplayName("si nada se puede crear, la propuesta no cuenta como aplicada")
    void nadaQueCrear() throws Exception {
        var r = aplicador.aplicar(TipoDePropuesta.EVENTO,
                propuesta(noche("Prueba Pasada", LocalDate.of(2025, 1, 10), "", "")), null);

        assertThat(r.aplicada()).isFalse();
        assertThat(r.enlaces()).isEmpty();
    }
}
