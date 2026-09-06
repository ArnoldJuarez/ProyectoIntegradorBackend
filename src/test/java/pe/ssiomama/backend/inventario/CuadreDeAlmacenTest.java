package pe.ssiomama.backend.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El almacén cuadra: cada tipo de movimiento va en su sentido.
 *
 * <h2>Qué comprueba y qué no</h2>
 *
 * <p>Lo que <b>no</b> comprueba, y conviene saberlo: que
 * {@code Producto.stock} sea la suma de sus movimientos. No lo es, y no por un
 * fallo del código — los datos que vienen del sistema anterior fijaron un stock
 * inicial sin escribir un movimiento de apertura, así que el histórico arranca
 * ya desfasado. Poner esa comprobación aquí daría un rojo permanente que nadie
 * podría arreglar sin inventarse movimientos.
 *
 * <p>Lo que sí comprueba es el invariante que el código controla de verdad y que
 * <b>sí</b> se cumple en toda la base: el <b>signo</b>. Una compra suma, una
 * venta y una merma restan. Si eso se rompiera —y romperlo es un cambio de una
 * línea en {@code GestionDeAlmacen}— el almacén empezaría a mentir sin dar
 * ningún error, y se pediría mercancía contra un saldo inventado.
 *
 * <p>Sin {@code @Transactional} a propósito: se mira el estado ya confirmado.
 */
@SpringBootTest
class CuadreDeAlmacenTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("las compras nunca restan")
    void lasComprasSuman() {
        var raras = movimientosRaros("COMPRA", "cantidad <= 0");
        assertThat(raras).as("compras que no suman: %s", raras).isEmpty();
    }

    @Test
    @DisplayName("las ventas nunca suman")
    void lasVentasRestan() {
        var raras = movimientosRaros("VENTA", "cantidad >= 0");
        assertThat(raras).as("ventas que no restan: %s", raras).isEmpty();
    }

    @Test
    @DisplayName("las mermas nunca suman")
    void lasMermasRestan() {
        var raras = movimientosRaros("MERMA", "cantidad >= 0");
        assertThat(raras).as("mermas que no restan: %s", raras).isEmpty();
    }

    /**
     * Una venta siempre apunta a la venta de barra que la originó.
     *
     * <p>Es lo que permite ir de un descuadre de inventario al ticket concreto,
     * y de ahí al turno de caja y a la persona. Un movimiento de venta sin
     * referencia es una bajada de stock que nadie puede explicar.
     */
    @Test
    @DisplayName("ninguna venta baja el stock sin decir de donde viene")
    void lasVentasSeExplican() {
        List<Map<String, Object>> sinRastro = jdbc.queryForList(
                """
                select m.id, m.cantidad, m."creadoEn"
                from "MovimientoStock" m
                where m.tipo = 'VENTA' and (m.referencia is null or m.referencia = '')
                """);

        assertThat(sinRastro).as("ventas sin referencia: %s", sinRastro).isEmpty();
    }

    /** Todo movimiento pertenece a un producto y a una sala que existen. */
    @Test
    @DisplayName("ningun movimiento cuelga del aire")
    void nadaCuelgaDelAire() {
        Integer sueltos = jdbc.queryForObject(
                """
                select count(*) from "MovimientoStock" m
                left join "Producto" p on p.id = m."productoId"
                left join "Sala" s on s.id = m."salaId"
                where p.id is null or s.id is null
                """,
                Integer.class);

        assertThat(sueltos).isZero();
    }

    /**
     * Solo una compra lleva precio. En una merma o un ajuste no salió dinero, y
     * un coste ahí infla el valor movido con euros que nunca se pagaron.
     */
    @Test
    @DisplayName("solo las compras llevan coste")
    void soloLasComprasCuestan() {
        List<Map<String, Object>> conCoste = jdbc.queryForList(
                """
                select id, tipo::text as tipo, "costeUnitarioCentimos"
                from "MovimientoStock"
                where tipo in ('MERMA', 'AJUSTE') and "costeUnitarioCentimos" <> 0
                """);

        assertThat(conCoste).as("mermas o ajustes con coste: %s", conCoste).isEmpty();
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    /**
     * Los movimientos de un tipo que cumplen una condición que no deberían.
     *
     * <p>{@code tipo::text} y no {@code tipo} a secas: la columna es un enum
     * nativo de PostgreSQL, y comparar un enum con un parámetro de texto da
     * «el operador no existe». El cast lo resuelve sin tener que declarar el
     * tipo del parámetro.
     */
    private List<Map<String, Object>> movimientosRaros(String tipo, String condicion) {
        return jdbc.queryForList(
                """
                select id, cantidad, referencia from "MovimientoStock"
                where tipo::text = ? and %s
                """
                        .formatted(condicion),
                tipo);
    }
}
