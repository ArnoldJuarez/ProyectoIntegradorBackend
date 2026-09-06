package pe.ssiomama.backend.caja;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La caja cuadra: lo esperado en el cajón es el fondo más el efectivo cobrado.
 *
 * <h2>Por qué existe</h2>
 *
 * <p>{@code SesionCaja.esperadoCentimos} es un contador que se va incrementando
 * con cada venta en efectivo. Como todo contador precalculado, se puede
 * desincronizar, y cuando lo hace <b>no da ningún error</b>: simplemente el
 * arqueo empieza a mentir. Y un arqueo que miente es peor que no tenerlo, porque
 * se le pide explicaciones a una persona por un descuadre que no cometió.
 *
 * <p>Es el mismo tipo de comprobación que {@code CuadreDeAforoTest} hace con las
 * entradas: una consulta contra la base entera, que caza dos errores distintos
 * —un cobro que suma mal, y un test que crea ventas y no las deshace bien—.
 *
 * <p>Sin {@code @Transactional} a propósito: lo que se comprueba es el estado ya
 * confirmado de la base, que es donde vive el descuadre.
 */
@SpringBootTest
class CuadreDeCajaTest {

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * La regla que hace que el arqueo signifique algo.
     *
     * <p>Al cajón solo entra el efectivo. Las tarjetas, el Yape y las
     * transferencias van al banco: si sumaran aquí, el arqueo saldría siempre
     * corto por el importe cobrado por otras vías y el descuadre dejaría de
     * distinguir un error de un cobro con tarjeta.
     */
    @Test
    @DisplayName("el esperado es el fondo mas el efectivo, y solo el efectivo")
    void elCajonSoloCuentaElEfectivo() {
        List<Map<String, Object>> cajas = jdbc.queryForList(
                """
                select s.id,
                       s."fondoCentimos"     as fondo,
                       s."esperadoCentimos"  as esperado,
                       coalesce(e.efectivo, 0) as efectivoCobrado
                from "SesionCaja" s
                left join (
                  select "sesionCajaId" as caja, sum("totalCentimos") as efectivo
                  from "VentaBarra" where "metodoPago" = 'EFECTIVO'
                  group by "sesionCajaId"
                ) e on e.caja = s.id
                """);

        assertThat(cajas).as("la base tiene cajas que comprobar").isNotEmpty();

        assertThat(cajas).allSatisfy(c -> {
            int fondo = ((Number) c.get("fondo")).intValue();
            int efectivo = ((Number) c.get("efectivoCobrado")).intValue();

            assertThat(((Number) c.get("esperado")).intValue())
                    .as("caja %s: el esperado no es fondo + efectivo", c.get("id"))
                    .isEqualTo(fondo + efectivo);
        });
    }

    /**
     * El total de una venta tiene que ser lo que suman sus líneas. Si se
     * separan, el ticket dice una cosa y el arqueo otra.
     */
    @Test
    @DisplayName("el total de cada venta coincide con sus lineas")
    void elTicketCuadra() {
        List<Map<String, Object>> descuadradas = jdbc.queryForList(
                """
                select v.id,
                       v."totalCentimos" as total,
                       coalesce(l.suma, 0) as segunLasLineas
                from "VentaBarra" v
                left join (
                  select "ventaBarraId" as venta,
                         sum(cantidad * "precioUnitarioCentimos") as suma
                  from "LineaVentaBarra" group by "ventaBarraId"
                ) l on l.venta = v.id
                where v."totalCentimos" <> coalesce(l.suma, 0)
                """);

        assertThat(descuadradas).as("ventas cuyo total no cuadra: %s", descuadradas).isEmpty();
    }

    /**
     * Toda venta cuelga de un turno de caja.
     *
     * <p>La clave ajena ya lo garantiza, pero comprobarlo aquí documenta por qué
     * importa: una venta sin turno no se le puede reclamar a nadie cuando el
     * cajón no cuadra, que es la razón entera de que exista el arqueo.
     */
    @Test
    @DisplayName("ninguna venta cuelga del aire")
    void todaVentaTieneTurno() {
        Integer sueltas = jdbc.queryForObject(
                """
                select count(*) from "VentaBarra" v
                left join "SesionCaja" s on s.id = v."sesionCajaId"
                where s.id is null
                """,
                Integer.class);

        assertThat(sueltas).isZero();
    }

    /**
     * Una caja cerrada tiene su recuento y su descuadre; una abierta, ninguno de
     * los dos. Un estado a medias —cerrada sin contar, o contada sin cerrar—
     * significa que algo se quedó a mitad.
     */
    @Test
    @DisplayName("el cierre es completo o no es")
    void elCierreNoSeQuedaAMedias() {
        List<Map<String, Object>> raras = jdbc.queryForList(
                """
                select id, "cerradaEn", "contadoCentimos", "descuadreCentimos"
                from "SesionCaja"
                where ("cerradaEn" is null) <> ("contadoCentimos" is null)
                   or ("cerradaEn" is null) <> ("descuadreCentimos" is null)
                """);

        assertThat(raras).as("cajas con el cierre a medias: %s", raras).isEmpty();
    }
}
