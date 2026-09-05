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

/**
 * Mapeo de eventos contra la base real, con las fechas bajo vigilancia.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventoRepositoryTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    private EventoRepository eventos;

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
     * <p>«Viernes Urbano» abre puertas a las 23:30 del viernes 11 de septiembre,
     * hora de Lima. En la base eso está guardado como {@code 2026-09-12
     * 04:30:00} en una columna <b>sin zona horaria</b>, porque el instante va en
     * UTC.
     *
     * <p>Si {@code hibernate.jdbc.time_zone} no estuviera en UTC, Hibernate
     * leería ese texto en la zona del sistema operativo y en una máquina en Lima
     * devolvería las 09:30Z: cinco horas de más, sin ningún error de por medio,
     * y con todas las horas de la web corridas.
     */
    @Test
    @DisplayName("las puertas se leen en el instante exacto, sin correrse de zona")
    void lasFechasNoSeCorren() {
        var noche = eventos.buscarPorSlugConDetalle("viernes-urbano").orElseThrow();

        assertThat(noche.getPuertasA())
                .as("el instante UTC tal y como está en la columna")
                .isEqualTo(Instant.parse("2026-09-12T04:30:00Z"));

        // Y visto desde la sala, que es como lo lee una persona:
        ZonedDateTime enLima = noche.getPuertasA().atZone(LIMA);
        assertThat(enLima.getHour()).isEqualTo(23);
        assertThat(enLima.getMinute()).isEqualTo(30);
        assertThat(enLima.getDayOfMonth()).isEqualTo(11);
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
