package pe.ssiomama.backend.noches;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * El expediente de una noche cuadra con las tablas de donde sale.
 *
 * <p>No se comprueban cifras fijas —los datos cambian— sino que cada total del
 * expediente coincide con su suma directa en la base y que sus partes suman el
 * todo. Si un join duplicara filas, aquí saltaría.
 */
@SpringBootTest
@Transactional
class ExpedienteDeNocheTest {

    @Autowired
    private ExpedienteDeNoche expedientes;

    @Autowired
    private JdbcTemplate jdbc;

    /** La noche celebrada más reciente con gente dentro y barra. */
    private String unaNocheConDatos() {
        return jdbc.queryForObject("""
                select e.slug from "Evento" e
                where exists (select 1 from "Acceso" a where a."eventoId" = e.id)
                  and exists (select 1 from "VentaBarra" v where v."eventoId" = e.id)
                order by e."empiezaEn" desc limit 1
                """, String.class);
    }

    @Test
    @DisplayName("la barra, la taquilla y la puerta cuadran con la base, y las partes suman el todo")
    void cuadraConLaBase() {
        String slug = unaNocheConDatos();
        var x = expedientes.de(slug).orElseThrow();
        Long id = x.noche().id();

        long barra = jdbc.queryForObject(
                "select coalesce(sum(\"totalCentimos\"), 0) from \"VentaBarra\" where \"eventoId\" = ?", Long.class, id);
        long taquilla = jdbc.queryForObject(
                "select coalesce(sum(\"totalCentimos\"), 0) from \"Pedido\" where \"eventoId\" = ? and estado = 'PAGADO'",
                Long.class, id);
        long accesos = jdbc.queryForObject("select count(*) from \"Acceso\" where \"eventoId\" = ?", Long.class, id);

        assertThat(x.barra().totalCentimos()).isEqualTo(barra);
        assertThat(x.taquilla().brutoCentimos()).isEqualTo(taquilla);
        assertThat(x.asistencia().entraronConEntrada()).isEqualTo(accesos);

        assertThat(x.barra().porBarra().stream().mapToLong(ExpedienteDeNoche.PorBarra::centimos).sum()).isEqualTo(barra);
        assertThat(x.barra().cajas().stream().mapToLong(ExpedienteDeNoche.CajaDeLaNoche::vendidoCentimos).sum())
                .isEqualTo(barra);
        assertThat(x.asistencia().porHora().stream().mapToLong(ExpedienteDeNoche.Tramo::personas).sum()).isEqualTo(accesos);
        assertThat(x.taquilla().porCanal().stream().mapToLong(ExpedienteDeNoche.PorCanal::centimos).sum()).isEqualTo(taquilla);
        assertThat(x.resultado().ingresosCentimos()).isEqualTo(taquilla + barra);
    }

    @Test
    @DisplayName("el texto para la IA no lleva los nombres de los clientes de los reservados")
    void elTextoNoLlevaClientes() {
        var x = expedientes.de(unaNocheConDatos()).orElseThrow();
        String texto = TextoDeExpediente.de(x);

        assertThat(texto).contains("## Asistencia", "## Barra", "## Costes y resultado");
        x.reservas().lista().forEach(r -> assertThat(texto).doesNotContain(r.cliente()));
    }

    @Test
    @DisplayName("una noche que no existe no tiene expediente")
    void nocheQueNoExiste() {
        assertThat(expedientes.de("no-existe-esta-noche")).isEmpty();
    }
}
