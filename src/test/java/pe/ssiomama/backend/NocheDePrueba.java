package pe.ssiomama.backend;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Una noche futura creada por y para una prueba.
 *
 * <h2>Por qué existe</h2>
 *
 * <p>La suite se quedó en rojo sin que nadie tocara el código. La causa: varias
 * pruebas se ataban a una noche concreta del seed —{@code viernes-urbano}— y esa
 * noche tiene fecha. Cuando el calendario la pasó, la venta de sus entradas se
 * cerró sola y diecisiete pruebas empezaron a fallar diciendo «ya no están a la
 * venta». El código hacía exactamente lo que debía.
 *
 * <p>Una suite que se pone roja porque pasó el tiempo es peor que no tenerla:
 * la próxima vez que salga en rojo, nadie va a mirar si esta vez sí es un fallo
 * de verdad.
 *
 * <h2>Cómo lo resuelve</h2>
 *
 * <p>Las pruebas que pasan por el servicio pueden controlar el reloj —
 * {@code Compra.abrir} recibe el instante— y no necesitan esto. Las que van por
 * HTTP no pueden: el controlador llama a {@code Instant.now()} de verdad. Para
 * esas, en vez de buscar una noche del seed que todavía sirva, se crea una
 * propia con fecha futura, se usa y se borra.
 *
 * <p>Así la prueba no depende del calendario ni de que el seed tenga eventos por
 * delante, que es una condición que caduca igual.
 */
public final class NocheDePrueba {

    /** Prefijo del slug, para poder borrar exactamente lo que se creó. */
    public static final String PREFIJO = "prueba-auto-";

    private NocheDePrueba() {
        // Solo métodos estáticos.
    }

    /**
     * Crea una noche publicada que empieza dentro de un mes.
     *
     * <p>Un mes y no un día: algunas comprobaciones miran la ventana de acceso
     * de la puerta, que se abre seis horas antes de empezar. Con la noche lejos,
     * ninguna prueba cae dentro de esa ventana por accidente.
     *
     * @return el id del evento creado
     */
    /**
     * Reserva el próximo id de una tabla sin insertar nada: la misma secuencia
     * que usa su columna identity, así que no choca con nadie.
     */
    public static Long reservarId(JdbcTemplate jdbc, String tabla) {
        return jdbc.queryForObject(
                "select nextval(pg_get_serial_sequence(?, 'id'))", Long.class, "public.\"" + tabla + "\"");
    }

    public static Long crear(JdbcTemplate jdbc, String titulo) {
        Long id = reservarId(jdbc, "Evento");
        String slug = PREFIJO + UUID.randomUUID().toString().substring(0, 8);

        Long salaId = jdbc.queryForObject(
                "select id from \"Sala\" order by aforo desc limit 1", Long.class);

        jdbc.update(
                """
                insert into "Evento" (
                  id, slug, titulo, descripcion, "salaId",
                  "aperturaPuertas", "empiezaEn", "terminaEn",
                  "edadMinima", estado, "publicadoEn", "creadoEn")
                values (?, ?, ?, 'Noche creada por una prueba automática.', ?,
                  now() + interval '30 days',
                  now() + interval '30 days' + interval '1 hour',
                  now() + interval '30 days' + interval '7 hours',
                  18, 'PUBLICADO'::"EstadoEvento", now(), now())
                """,
                id,
                slug,
                titulo,
                salaId);

        return id;
    }

    /** El slug de una noche creada aquí, para las rutas públicas. */
    public static String slugDe(JdbcTemplate jdbc, Long eventoId) {
        return jdbc.queryForObject(
                "select slug from \"Evento\" where id = ?", String.class, eventoId);
    }

    /**
     * Añade un tipo de entrada a la venta hasta que empiece la noche.
     *
     * @return el id del tipo creado
     */
    public static Long anadirTipoDeEntrada(
            JdbcTemplate jdbc, Long eventoId, String nombre, int precioCentimos, int cantidad) {

        Long id = reservarId(jdbc, "TipoEntrada");

        jdbc.update(
                """
                insert into "TipoEntrada" (
                  id, "eventoId", nombre, descripcion, "precioCentimos", cantidad,
                  vendidas, "ventaHasta", "maxPorPedido", "incluyeConsumo", activo)
                select ?, ?, ?, 'Entrada de prueba.', ?, ?, 0, e."empiezaEn", 6, 1, true
                from "Evento" e where e.id = ?
                """,
                id,
                eventoId,
                nombre,
                precioCentimos,
                cantidad,
                eventoId);

        return id;
    }

    /**
     * Borra todas las noches de prueba y lo que cuelgue de ellas.
     *
     * <p>En orden inverso a las claves ajenas. Se borra <b>todo</b> lo marcado
     * con el prefijo y no solo lo de esta prueba: si una ejecución anterior se
     * interrumpió a mitad, sus restos se van aquí en vez de quedarse para
     * siempre ensuciando la base.
     */
    public static void limpiar(JdbcTemplate jdbc) {
        String deLasPruebas = PREFIJO + "%";

        jdbc.update(
                """
                delete from "Acceso" where "eventoId" in (
                  select id from "Evento" where slug like ?)
                """,
                deLasPruebas);
        jdbc.update(
                """
                delete from "Entrada" where "eventoId" in (
                  select id from "Evento" where slug like ?)
                """,
                deLasPruebas);
        jdbc.update(
                """
                delete from "LineaPedido" where "pedidoId" in (
                  select p.id from "Pedido" p join "Evento" e on e.id = p."eventoId"
                  where e.slug like ?)
                """,
                deLasPruebas);
        jdbc.update(
                """
                delete from "Pedido" where "eventoId" in (
                  select id from "Evento" where slug like ?)
                """,
                deLasPruebas);
        jdbc.update(
                """
                delete from "ReservaBox" where "eventoId" in (
                  select id from "Evento" where slug like ?)
                """,
                deLasPruebas);
        jdbc.update(
                """
                delete from "EventoArtista" where "eventoId" in (
                  select id from "Evento" where slug like ?)
                """,
                deLasPruebas);
        jdbc.update(
                """
                delete from "TipoEntrada" where "eventoId" in (
                  select id from "Evento" where slug like ?)
                """,
                deLasPruebas);
        jdbc.update("delete from \"Evento\" where slug like ?", deLasPruebas);
    }
}
