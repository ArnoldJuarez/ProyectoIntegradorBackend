package pe.ssiomama.backend.promotores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.auth.UsuarioRepository;
import pe.ssiomama.backend.caja.MetodoDePago;

/**
 * Lo que se le paga a un promotor, contra los datos reales.
 *
 * <p>En una transacción que se deshace al terminar: ningún pago de prueba queda
 * en la base.
 */
@SpringBootTest
@Transactional
class ComisionesTest {

    @Autowired
    private Comisiones comisiones;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    private Usuario promotor() {
        return usuarios.findByCodigoDePromotor("PIERO").orElseThrow();
    }

    private Usuario quienPaga() {
        return usuarios.findByEmailIgnoreCase("admin@ssiomama.test").orElseThrow();
    }

    @Test
    @DisplayName("el pago cubre exactamente lo que estaba pendiente y lo deja a cero")
    void elPagoCubreLoPendiente() {
        var antes = comisiones.pendiente(promotor(), Instant.now());
        assertThat(antes.totalCentimos()).isPositive();

        Liquidacion l = comisiones.liquidar(promotor().getId(), null, MetodoDePago.YAPE, "OP-1", null, 0, quienPaga());

        assertThat(l.getPedidos()).isEqualTo((int) antes.pedidos());
        assertThat(l.getVentasCentimos()).isEqualTo((int) antes.ventasCentimos());
        assertThat(l.getTotalCentimos()).isEqualTo((int) antes.totalCentimos());
        assertThat(comisiones.pendiente(promotor(), Instant.now()).totalCentimos()).isZero();
    }

    @Test
    @DisplayName("no se puede pagar dos veces lo mismo")
    void noSePagaDosVeces() {
        comisiones.liquidar(promotor().getId(), null, MetodoDePago.EFECTIVO, null, null, 0, quienPaga());

        assertThatThrownBy(() ->
                comisiones.liquidar(promotor().getId(), null, MetodoDePago.EFECTIVO, null, null, 0, quienPaga()))
                .isInstanceOf(ComisionRechazada.class)
                .hasMessageContaining("nada pendiente");
    }

    @Test
    @DisplayName("anular un pago devuelve lo que cubría a pendiente")
    void anularDevuelveAPendiente() {
        long antes = comisiones.pendiente(promotor(), Instant.now()).totalCentimos();
        Liquidacion l = comisiones.liquidar(promotor().getId(), null, MetodoDePago.YAPE, null, null, 0, quienPaga());

        comisiones.anular(l.getId());

        assertThat(comisiones.pendiente(promotor(), Instant.now()).totalCentimos()).isEqualTo(antes);
        assertThat(jdbc.queryForObject(
                "select count(*) from \"Pedido\" where \"liquidacionId\" = ?", Integer.class, l.getId())).isZero();
    }

    @Test
    @DisplayName("un ajuste a mano sin nota se rechaza")
    void ajusteSinNota() {
        assertThatThrownBy(() ->
                comisiones.liquidar(promotor().getId(), null, MetodoDePago.YAPE, null, " ", -500, quienPaga()))
                .isInstanceOf(ComisionRechazada.class)
                .hasMessageContaining("nota");
    }
}
