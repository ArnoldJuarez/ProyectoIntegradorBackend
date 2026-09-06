package pe.ssiomama.backend.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Mapeo de eventos contra la base real, con las fechas bajo vigilancia.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventoRepositoryTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    private EventoRepository eventos;

    /** Para leer la columna en crudo, sin que Hibernate la interprete. */
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("carga las noches con su sala y sus tipos de entrada")
    void cargaLasNoches() {
        var todas = eventos.buscarTodosConDetalle();

        assertThat(todas).isNotEmpty();
        assertThat(todas).allSatisfy(e -> {
            assertThat(e.getSlug()).isNotBlank();
            assertThat(e.getTitulo()).isNotBlank();
            assertThat(e.getEstado()).isNotNull();
            // El join fetch tiene que haberlas traído: sin él, tocar esto
            // fuera de la transacción reventaría.
            assertThat(e.getSala().getNombre()).isNotBlank();
        });
    }

    /**
     * <b>El test que importa de este archivo.</b>
     *
     * <p>Las fechas se guardan en columnas {@code timestamp without time zone},
     * es decir texto sin zona, y el acuerdo es que ese texto está en UTC. Quien
     * lo lee tiene que interpretarlo igual, o las horas se corren sin que salte
     * ningún error.
     *
     * <p>Si {@code hibernate.jdbc.time_zone} no estuviera en UTC, Hibernate
     * leería «2026-09-19 01:00:00» en la zona del sistema operativo y en una
     * máquina en Lima devolvería las 06:00Z: cinco horas de más, en silencio, y
     * con todas las horas de la web corridas.
     *
     * <p>Por eso el test no compara contra una fecha escrita a mano —que se
     * rompería al volver a sembrar la base— sino contra <b>lo que pone la
     * columna</b>, leído con JDBC en crudo. Compara las dos lecturas de la misma
     * fila: si coinciden, no hubo desplazamiento.
     */
    @Test
    @DisplayName("las puertas se leen en el instante exacto, sin correrse de zona")
    void lasFechasNoSeCorren() {
        var todas = eventos.buscarTodosConDetalle();
        assertThat(todas).as("la base tiene eventos que comprobar").isNotEmpty();

        for (var noche : todas) {
            // La columna en crudo, forzada a formato ISO y marcada como UTC,
            // que es la zona en la que se acordó guardarla.
            String textoCrudo = jdbc.queryForObject(
                    """
                    select to_char("aperturaPuertas", 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
                    from "Evento" where slug = ?
                    """,
                    String.class,
                    noche.getSlug());
            Instant enLaColumna = Instant.parse(textoCrudo);

            assertThat(noche.getPuertasA())
                    .as("«%s»: el instante UTC tal y como está en la columna", noche.getSlug())
                    .isEqualTo(enLaColumna);
        }
    }

    /**
     * Lo anterior visto desde la sala: Lima va cinco horas por detrás de UTC y
     * no tiene horario de verano, así que el desfase es siempre el mismo.
     *
     * <p>Es la comprobación que traduce el instante a lo que lee una persona en
     * el cartel de la puerta.
     */
    @Test
    @DisplayName("en Lima la misma noche se ve cinco horas antes")
    void enLimaSonCincoHorasMenos() {
        var noche = eventos.buscarTodosConDetalle().get(0);

        ZonedDateTime enLima = noche.getPuertasA().atZone(LIMA);

        assertThat(enLima.getOffset().getTotalSeconds()).isEqualTo(-5 * 3600);
        assertThat(enLima.toInstant()).isEqualTo(noche.getPuertasA());
    }

    @Test
    @DisplayName("el orden de los tipos de entrada es por precio")
    void tiposOrdenadosPorPrecio() {
        var noche = eventos.buscarPorSlugConDetalle("viernes-urbano").orElseThrow();
        var precios = noche.getTiposDeEntrada().stream()
                .map(TipoDeEntrada::getPrecioCentimos)
                .toList();

        assertThat(precios).isNotEmpty().isSorted();
    }

    @Test
    @DisplayName("los agregados de aforo cuadran con los tipos de entrada")
    void agregadosCuadran() {
        var noche = eventos.buscarPorSlugConDetalle("viernes-urbano").orElseThrow();

        int sumaVendidas = noche.getTiposDeEntrada().stream()
                .mapToInt(TipoDeEntrada::getVendidas)
                .sum();
        assertThat(noche.getVendidas()).isEqualTo(sumaVendidas);

        long sumaRecaudado = noche.getTiposDeEntrada().stream()
                .mapToLong(t -> (long) t.getPrecioCentimos() * t.getVendidas())
                .sum();
        assertThat(noche.getRecaudadoCentimos()).isEqualTo(sumaRecaudado);
    }

    /** Nunca se venden más de las que hay, así que disponibles no baja de cero. */
    @Test
    @DisplayName("las disponibles nunca son negativas")
    void disponiblesNuncaNegativas() {
        assertThat(eventos.buscarTodosConDetalle())
                .flatMap(Evento::getTiposDeEntrada)
                .allSatisfy(t -> assertThat(t.getDisponibles()).isGreaterThanOrEqualTo(0));
    }

    @Test
    @DisplayName("un slug que no existe devuelve vacio")
    void slugInexistente() {
        assertThat(eventos.buscarPorSlugConDetalle("no-existe-esta-noche")).isEmpty();
    }
}
