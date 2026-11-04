package pe.ssiomama.backend.gastos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.UsuarioRepository;
import pe.ssiomama.backend.gastos.GestionDeGastos.DatosDeGasto;

/** Apuntar, corregir y borrar gastos, y el resumen del mes. */
@SpringBootTest
@Transactional
class GastosTest {

    @Autowired
    private GestionDeGastos gestion;

    @Autowired
    private GastosController controlador;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    private String categoria(String nombre) {
        return jdbc.queryForObject("select id from \"CategoriaGasto\" where nombre = ?", String.class, nombre);
    }

    @Test
    @DisplayName("un gasto apuntado a una noche cuenta en el mes y en su categoría")
    void apuntarYResumir() {
        String noche = jdbc.queryForObject("select slug from \"Evento\" order by \"empiezaEn\" desc limit 1", String.class);
        var admin = usuarios.findByEmailIgnoreCase("admin@ssiomama.test").orElseThrow();
        // En un mes futuro sin gastos (dentro de los tres meses que se admiten), para que el total sea exactamente el nuestro.
        Instant fecha = YearMonth.of(2026, 12).atDay(15).atTime(20, 0).atZone(ZoneId.of("America/Lima")).toInstant();

        gestion.apuntar(new DatosDeGasto(categoria("Seguridad"), noche, 72000, "8 vigilantes", fecha), admin);
        gestion.apuntar(new DatosDeGasto(categoria("Limpieza"), null, 12000, "Limpieza general", fecha), admin);
        em.flush();

        var mes = controlador.delMes("2026-12");
        assertThat(mes.totalCentimos()).isEqualTo(84000);
        assertThat(mes.porCategoria()).first().satisfies(c -> {
            assertThat(c.categoria()).isEqualTo("Seguridad");
            assertThat(c.totalCentimos()).isEqualTo(72000);
        });
        assertThat(mes.gastos()).anySatisfy(g -> {
            assertThat(g.nocheSlug()).isEqualTo(noche);
            assertThat(g.apuntadoPor()).isEqualTo(admin.getNombre());
        });
    }

    @Test
    @DisplayName("un gasto se corrige y se borra")
    void corregirYBorrar() {
        var admin = usuarios.findByEmailIgnoreCase("admin@ssiomama.test").orElseThrow();
        Gasto g = gestion.apuntar(new DatosDeGasto(categoria("Publicidad"), null, 5000, "Posts", null), admin);

        gestion.corregir(g.getId(), new DatosDeGasto(categoria("Publicidad"), null, 15000, "Posts y stories", null));
        assertThat(g.getImporteCentimos()).isEqualTo(15000);

        em.flush();
        gestion.borrar(g.getId());
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from \"Gasto\" where id = ?", Long.class, g.getId())).isZero();
    }

    @Test
    @DisplayName("un gasto de cero no se apunta, ni una categoría repetida")
    void rechazos() {
        assertThatThrownBy(() -> gestion.apuntar(new DatosDeGasto(categoria("Limpieza"), null, 0, null, null), null))
                .isInstanceOf(GastoRechazado.class).hasMessageContaining("mayor que cero");
        assertThatThrownBy(() -> gestion.crearCategoria("seguridad"))
                .isInstanceOf(GastoRechazado.class).hasMessageContaining("Ya existe");
    }
}
