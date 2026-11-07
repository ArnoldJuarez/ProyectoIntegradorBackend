package pe.ssiomama.backend.entradas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El aforo cuadra: {@code vendidas} dice lo que dicen los pedidos.
 *
 * <h2>Por qué existe</h2>
 *
 * <p>{@code TipoEntrada.vendidas} es un contador precalculado: se podría sumar
 * las líneas de pedido cada vez, pero eso es una consulta pesada en la pantalla
 * que más se abre. El precio de tenerlo es que se puede desincronizar, y cuando
 * lo hace <b>no da ningún error</b>: la web sigue funcionando y vendiendo, solo
 * que con el aforo equivocado.
 *
 * <p>Este archivo comprueba el invariante contra la base entera. Cuesta una
 * consulta y caza dos familias de fallos:
 *
 * <ul>
 *   <li>Vender de más: dos compras simultáneas que se pisan y dejan
 *       {@code vendidas} por debajo de lo que se vendió de verdad.
 *   <li>Fugas de los propios tests: uno que crea pedidos, borra las filas al
 *       terminar y se olvida de devolver el cupo. Pasó — {@code CompraHttpTest}
 *       dejaba tres entradas fantasma en cada ejecución, y el aforo del sábado
 *       se iba encogiendo sin que nada lo dijera.
 * </ul>
 *
 * <p>No lleva {@code @Transactional} a propósito: lo que comprueba es el estado
 * <b>ya confirmado</b> de la base, que es donde vive el descuadre.
 */
@SpringBootTest
class CuadreDeAforoTest {

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Solo cuentan los pedidos vivos (pendientes o pagados): uno cancelado o
     * devuelto devuelve su cupo ({@code TipoDeEntrada.liberarCupo}), así que
     * sus líneas ya no ocupan sitio aunque sigan en la base como historia.
     */
    @Test
    @DisplayName("vendidas coincide con la suma de las lineas de los pedidos vivos")
    void elContadorCuadraConLosPedidos() {
        List<Map<String, Object>> filas = jdbc.queryForList(
                """
                select v.slug            as noche,
                       t.nombre          as tipo,
                       t.vendidas        as contador,
                       coalesce(x.suma, 0) as segunLosPedidos
                from "TipoEntrada" t
                join "Evento" v on v.id = t."eventoId"
                left join (
                  select l."tipoEntradaId" as tipo, sum(l.cantidad) as suma
                  from "LineaPedido" l
                  join "Pedido" p on p.id = l."pedidoId"
                  where p.estado not in ('CANCELADO', 'DEVUELTO')
                  group by l."tipoEntradaId"
                ) x on x.tipo = t.id
                order by v.slug, t.nombre
                """);

        assertThat(filas).as("la base tiene tipos de entrada que comprobar").isNotEmpty();

        assertThat(filas).allSatisfy(f -> assertThat(((Number) f.get("contador")).intValue())
                .as(
                        "«%s / %s»: el contador de vendidas no coincide con sus pedidos",
                        f.get("noche"), f.get("tipo"))
                .isEqualTo(((Number) f.get("segunLosPedidos")).intValue()));
    }

    /**
     * La otra mitad del invariante, y la que se nota en la puerta: no se puede
     * haber vendido más de lo que cabe.
     */
    @Test
    @DisplayName("ningun tipo de entrada esta sobrevendido")
    void nadieVendeDeMas() {
        List<Map<String, Object>> pasados = jdbc.queryForList(
                """
                select v.slug as noche, t.nombre as tipo, t.vendidas, t.cantidad
                from "TipoEntrada" t
                join "Evento" v on v.id = t."eventoId"
                where t.vendidas > t.cantidad
                """);

        assertThat(pasados)
                .as("tipos de entrada con más vendidas que aforo: %s", pasados)
                .isEmpty();
    }

    /**
     * Una entrada emitida siempre pertenece a un pedido pagado.
     *
     * <p>Si aparece una entrada colgando de un pedido pendiente o cancelado, es
     * que algo emitió sin cobrar — y esa entrada abriría la puerta.
     */
    @Test
    @DisplayName("no hay entradas emitidas de pedidos sin pagar")
    void nadieEntraSinPagar() {
        List<Map<String, Object>> sueltas = jdbc.queryForList(
                """
                select e.codigo, p.codigo as pedido, p.estado
                from "Entrada" e
                join "Pedido" p on p.id = e."pedidoId"
                where p.estado not in ('PAGADO', 'DEVUELTO')
                """);

        assertThat(sueltas).as("entradas emitidas sin pago: %s", sueltas).isEmpty();
    }
}
