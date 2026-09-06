package pe.ssiomama.backend.entradas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.ssiomama.backend.entradas.Compra.Comprador;
import pe.ssiomama.backend.entradas.Compra.Peticion;

/**
 * Veinte personas comprando ocho entradas a la vez.
 *
 * <h2>Por qué este test escribe de verdad</h2>
 *
 * <p>Es el único de la migración que <b>no</b> va dentro de una transacción que
 * se deshaga, y tiene que ser así: lo que se prueba aquí es qué pasa cuando dos
 * transacciones se pisan, y dentro de una transacción de test no hay dos
 * transacciones. Un {@code @Transactional} encima de la clase haría que todos
 * los hilos compartieran la misma y el test pasaría sin comprobar nada.
 *
 * <h2>Qué demuestra exactamente</h2>
 *
 * <p>Que {@code vendidas} nunca supera {@code cantidad}. Con el nivel de
 * aislamiento por defecto de PostgreSQL este test falla: veinte hilos leen que
 * quedan ocho, los veinte deciden que se puede, y se venden más entradas que
 * asientos hay. Es un fallo que no da ningún error — se descubre en la puerta,
 * un viernes, con dos personas y un solo sitio.
 *
 * <p>Deja la base como estaba: {@link #limpiar()} borra lo que se creó y
 * devuelve el aforo a su valor original.
 */
@SpringBootTest
class CompraConcurrenteTest {

    private static final String NOCHE = "jueves-barra-libre";
    private static final String TIPO = "VIP";

    /** Cuántas entradas se dejan disponibles. */
    private static final int DISPONIBLES = 8;

    /** Cuántas personas lo intentan a la vez. Más que entradas, a propósito. */
    private static final int COMPRADORES = 20;

    /** Marca para poder borrar exactamente lo que crea este test. */
    private static final String MARCA = "concurrente-%d@ejemplo.test";

    @Autowired
    private Compra compra;

    @Autowired
    private Reintentos reintentos;

    @Autowired
    private JdbcTemplate jdbc;

    private String tipoId;
    private int cantidadOriginal;
    private int vendidasAlEmpezar;

    @BeforeEach
    void prepararElAforo() {
        tipoId = jdbc.queryForObject(
                """
                select t.id from "TipoEntrada" t
                join "Evento" v on v.id = t."eventoId"
                where v.slug = ? and t.nombre = ?
                """,
                String.class,
                NOCHE,
                TIPO);

        cantidadOriginal = jdbc.queryForObject(
                "select cantidad from \"TipoEntrada\" where id = ?", Integer.class, tipoId);
        vendidasAlEmpezar = jdbc.queryForObject(
                "select vendidas from \"TipoEntrada\" where id = ?", Integer.class, tipoId);

        // Se estrecha el aforo para que la carrera sea inevitable: veinte
        // compradores y ocho entradas.
        jdbc.update(
                "update \"TipoEntrada\" set cantidad = ? where id = ?",
                vendidasAlEmpezar + DISPONIBLES,
                tipoId);
    }

    @AfterEach
    void limpiar() {
        // En este orden por las claves ajenas: entradas, líneas, pedidos, y por
        // último los clientes y sus rastros.
        jdbc.update(
                """
                delete from "Entrada" where "pedidoId" in (
                  select p.id from "Pedido" p join "Cliente" c on c.id = p."clienteId"
                  where c.correo like 'concurrente-%@ejemplo.test')
                """);
        jdbc.update(
                """
                delete from "LineaPedido" where "pedidoId" in (
                  select p.id from "Pedido" p join "Cliente" c on c.id = p."clienteId"
                  where c.correo like 'concurrente-%@ejemplo.test')
                """);
        jdbc.update(
                """
                delete from "Pedido" where "clienteId" in (
                  select id from "Cliente" where correo like 'concurrente-%@ejemplo.test')
                """);
        jdbc.update(
                """
                delete from "MovimientoFidelidad" where "clienteId" in (
                  select id from "Cliente" where correo like 'concurrente-%@ejemplo.test')
                """);
        jdbc.update(
                """
                delete from "Interaccion" where "clienteId" in (
                  select id from "Cliente" where correo like 'concurrente-%@ejemplo.test')
                """);
        jdbc.update("delete from \"Cliente\" where correo like 'concurrente-%@ejemplo.test'");

        // Y el aforo vuelve a ser el que era.
        jdbc.update(
                "update \"TipoEntrada\" set cantidad = ?, vendidas = ? where id = ?",
                cantidadOriginal,
                vendidasAlEmpezar,
                tipoId);
    }

    @Test
    @DisplayName("veinte compras a la vez no venden mas entradas de las que hay")
    void noSeVendeDeMas() throws Exception {
        AtomicInteger compradas = new AtomicInteger();
        AtomicInteger rechazadas = new AtomicInteger();
        AtomicInteger rotas = new AtomicInteger();

        /*
         * La barrera es lo que hace que la carrera ocurra. Sin ella, veinte
         * tareas lanzadas en bucle se ejecutan casi en serie —la primera acaba
         * antes de que arranque la última— y no se pisan nunca. Con ella, los
         * veinte hilos esperan y salen en el mismo instante.
         */
        CyclicBarrier salida = new CyclicBarrier(COMPRADORES);

        try (ExecutorService hilos = Executors.newFixedThreadPool(COMPRADORES)) {
            List<Callable<Void>> tareas = java.util.stream.IntStream.range(0, COMPRADORES)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        salida.await(10, TimeUnit.SECONDS);
                        try {
                            // Con el mismo reintento que usa el controlador: es
                            // parte de lo que se está probando.
                            reintentos.conReintento(() -> compra.abrir(
                                    NOCHE,
                                    new Comprador("Comprador", String.valueOf(i),
                                            MARCA.formatted(i), "999000" + i),
                                    List.of(new Peticion(tipoId, 1)),
                                    Instant.now()));
                            compradas.incrementAndGet();
                        } catch (CompraRechazada agotado) {
                            rechazadas.incrementAndGet();
                        } catch (RuntimeException inesperado) {
                            rotas.incrementAndGet();
                        }
                        return null;
                    })
                    .toList();

            for (Future<Void> f : hilos.invokeAll(tareas, 60, TimeUnit.SECONDS)) {
                f.get();
            }
        }

        int vendidasAlAcabar = jdbc.queryForObject(
                "select vendidas from \"TipoEntrada\" where id = ?", Integer.class, tipoId);

        // Lo que importa: ni una entrada de más.
        // El desglose va en el mensaje para que un fallo se pueda diagnosticar
        // sin volver a ejecutarlo: no es lo mismo vender de mas que rechazar a
        // quien si cabia.
        assertThat(vendidasAlAcabar - vendidasAlEmpezar)
                .as(
                        "vendidas de %d disponibles (compradas=%d, rechazadas=%d, rotas=%d)",
                        DISPONIBLES, compradas.get(), rechazadas.get(), rotas.get())
                .isEqualTo(DISPONIBLES);

        assertThat(compradas.get()).isEqualTo(DISPONIBLES);
        assertThat(rechazadas.get()).isEqualTo(COMPRADORES - DISPONIBLES);

        // Y nadie se lleva un 500. Un conflicto de serialización que llegue
        // hasta aquí significa que el reintento no está haciendo su trabajo.
        assertThat(rotas.get())
                .as("compras que fallaron por algo que no es «se agotaron»")
                .isZero();

        // Los pedidos que se crearon son exactamente los que se cobraron.
        int pedidosCreados = jdbc.queryForObject(
                """
                select count(*) from "Pedido" p join "Cliente" c on c.id = p."clienteId"
                where c.correo like 'concurrente-%@ejemplo.test'
                """,
                Integer.class);
        assertThat(pedidosCreados).isEqualTo(DISPONIBLES);
    }
}
