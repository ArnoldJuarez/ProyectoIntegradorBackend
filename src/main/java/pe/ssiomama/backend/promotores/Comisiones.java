package pe.ssiomama.backend.promotores;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.auth.UsuarioRepository;
import pe.ssiomama.backend.caja.MetodoDePago;

/**
 * Lo que se le debe a cada promotor y lo que ya se le pagó.
 *
 * <h2>Cómo se gana un promotor su dinero</h2>
 *
 * <ul>
 *   <li><b>Comisión</b>: su porcentaje sobre el precio de las entradas vendidas
 *       con su código. Sin los gastos de gestión, que son de la casa.
 *   <li><b>Pago por cabeza</b>: un fijo por cada persona de sus listas que entra
 *       de verdad. Apuntada y no venida no cuenta.
 * </ul>
 *
 * <h2>Cuándo se le debe</h2>
 *
 * <p>Cuando la noche ya empezó. Antes, un pedido todavía se puede devolver y
 * una lista todavía no ha entrado: pagar por adelantado es pagar por algo que
 * puede no pasar. Lo de noches que no han llegado sale como «por celebrar».
 *
 * <h2>Cómo se evita pagar dos veces</h2>
 *
 * <p>Al liquidar, cada pedido y cada lista que entra en el pago queda marcado
 * con el id de la liquidación, y lo marcado ya no vuelve a salir como
 * pendiente. Las cifras del pago se calculan <b>sobre lo que se marcó de
 * verdad</b> (un {@code update ... returning}), no sobre una consulta previa:
 * así dos liquidaciones a la vez no pueden contar el mismo pedido.
 */
@Service
public class Comisiones {

    private final JdbcClient sql;
    private final UsuarioRepository usuarios;
    private final LiquidacionRepository liquidaciones;

    public Comisiones(JdbcClient sql, UsuarioRepository usuarios, LiquidacionRepository liquidaciones) {
        this.sql = sql;
        this.usuarios = usuarios;
        this.liquidaciones = liquidaciones;
    }

    // ── Lo que sale ───────────────────────────────────────────────

    /** En qué punto está lo de una noche para el promotor. */
    public enum EstadoDeNoche { POR_CELEBRAR, PENDIENTE, LIQUIDADA }

    public record NocheDelPromotor(
            String slug,
            String titulo,
            Instant empiezaA,
            EstadoDeNoche estado,
            long pedidos,
            long entradas,
            long ventasCentimos,
            long comisionCentimos,
            long listas,
            long apuntados,
            long entraron,
            long bonoCentimos,
            long totalCentimos) {}

    /** El saldo de un promotor, para la tabla general. */
    public record Saldo(
            Long promotorId,
            long pedidos,
            long ventasCentimos,
            long pendienteCentimos,
            long porCelebrarCentimos,
            long pagadoCentimos,
            Instant ultimoPago) {}

    /** Lo que entraría en una liquidación hecha ahora. */
    public record Pendiente(
            long pedidos,
            long entradas,
            long ventasCentimos,
            int comisionPct,
            long comisionCentimos,
            long listas,
            long invitadosEntraron,
            long bonoPorInvitadoCentimos,
            long bonoCentimos,
            long totalCentimos) {}

    // ── Lecturas ──────────────────────────────────────────────────

    /** Noche a noche, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<NocheDelPromotor> porNoche(Usuario promotor) {
        int pct = porcentaje(promotor);
        long bono = promotor.getBonoPorInvitadoCentimos();
        Instant ahora = Instant.now();

        return sql.sql("""
                        with ventas as (
                            select p."eventoId" as evento, count(*) as pedidos,
                                   coalesce(sum((select sum(l.cantidad) from "LineaPedido" l
                                                 where l."pedidoId" = p.id)), 0) as entradas,
                                   coalesce(sum(p."subtotalCentimos"), 0) as ventas,
                                   bool_and(p."liquidacionId" is not null) as liquidado
                            from "Pedido" p
                            where p."promotorId" = :id and p.estado = 'PAGADO'
                            group by 1
                        ),
                        listas as (
                            select l."eventoId" as evento, count(distinct l.id) as listas,
                                   coalesce(sum(i.personas), 0) as apuntados,
                                   coalesce(sum(i.personas) filter (where i.entro), 0) as entraron,
                                   bool_and(l."liquidacionId" is not null) as liquidado
                            from "ListaInvitados" l left join "Invitado" i on i."listaInvitadosId" = l.id
                            where l."promotorId" = :id
                            group by 1
                        )
                        select e.slug, e.titulo, e."empiezaEn",
                               coalesce(v.pedidos, 0) as pedidos, coalesce(v.entradas, 0) as entradas,
                               coalesce(v.ventas, 0) as ventas,
                               coalesce(li.listas, 0) as listas, coalesce(li.apuntados, 0) as apuntados,
                               coalesce(li.entraron, 0) as entraron,
                               coalesce(v.liquidado, true) and coalesce(li.liquidado, true) as liquidado
                        from "Evento" e
                        left join ventas v on v.evento = e.id
                        left join listas li on li.evento = e.id
                        where v.evento is not null or li.evento is not null
                        order by e."empiezaEn" desc
                        """)
                .param("id", promotor.getId())
                .query((rs, i) -> {
                    Instant empieza = rs.getObject("empiezaEn", LocalDateTime.class).toInstant(ZoneOffset.UTC);
                    long ventas = rs.getLong("ventas");
                    long comision = Math.round(ventas * pct / 100.0);
                    long bonoNoche = rs.getLong("entraron") * bono;
                    EstadoDeNoche estado = !empieza.isBefore(ahora)
                            ? EstadoDeNoche.POR_CELEBRAR
                            : rs.getBoolean("liquidado") ? EstadoDeNoche.LIQUIDADA : EstadoDeNoche.PENDIENTE;
                    return new NocheDelPromotor(
                            rs.getString("slug"),
                            rs.getString("titulo"),
                            empieza,
                            estado,
                            rs.getLong("pedidos"),
                            rs.getLong("entradas"),
                            ventas,
                            comision,
                            rs.getLong("listas"),
                            rs.getLong("apuntados"),
                            rs.getLong("entraron"),
                            bonoNoche,
                            comision + bonoNoche);
                })
                .list();
    }

    /** Lo que entraría en una liquidación con noches hasta {@code hasta}. */
    @Transactional(readOnly = true)
    public Pendiente pendiente(Usuario promotor, Instant hasta) {
        LocalDateTime corte = corte(hasta);

        record Ventas(long pedidos, long entradas, long ventas) {}
        Ventas v = sql.sql("""
                        select count(*) as pedidos,
                               coalesce(sum((select sum(l.cantidad) from "LineaPedido" l where l."pedidoId" = p.id)), 0)
                                   as entradas,
                               coalesce(sum(p."subtotalCentimos"), 0) as ventas
                        from "Pedido" p join "Evento" e on e.id = p."eventoId"
                        where p."promotorId" = :id and p.estado = 'PAGADO' and p."liquidacionId" is null
                          and e."empiezaEn" < :corte
                        """)
                .param("id", promotor.getId())
                .param("corte", corte)
                .query((rs, i) -> new Ventas(rs.getLong("pedidos"), rs.getLong("entradas"), rs.getLong("ventas")))
                .single();

        record Listas(long listas, long entraron) {}
        Listas l = sql.sql("""
                        select count(distinct li.id) as listas,
                               coalesce(sum(i.personas) filter (where i.entro), 0) as entraron
                        from "ListaInvitados" li join "Evento" e on e.id = li."eventoId"
                        left join "Invitado" i on i."listaInvitadosId" = li.id
                        where li."promotorId" = :id and li."liquidacionId" is null and e."empiezaEn" < :corte
                        """)
                .param("id", promotor.getId())
                .param("corte", corte)
                .query((rs, i) -> new Listas(rs.getLong("listas"), rs.getLong("entraron")))
                .single();

        int pct = porcentaje(promotor);
        long comision = Math.round(v.ventas() * pct / 100.0);
        long bono = l.entraron() * promotor.getBonoPorInvitadoCentimos();
        return new Pendiente(v.pedidos(), v.entradas(), v.ventas(), pct, comision, l.listas(), l.entraron(),
                promotor.getBonoPorInvitadoCentimos(), bono, comision + bono);
    }

    /** El saldo de cada promotor, en una sola consulta para la tabla general. */
    @Transactional(readOnly = true)
    public Map<Long, Saldo> saldos() {
        return sql.sql("""
                        with ahora as (select (now() at time zone 'utc') as t),
                        ventas as (
                            select p."promotorId" as promotor,
                                   count(*) as pedidos,
                                   coalesce(sum(p."subtotalCentimos"), 0) as ventas,
                                   coalesce(sum(p."subtotalCentimos") filter (
                                       where p."liquidacionId" is null and e."empiezaEn" < (select t from ahora)), 0)
                                       as pendiente,
                                   coalesce(sum(p."subtotalCentimos") filter (
                                       where e."empiezaEn" >= (select t from ahora)), 0) as por_celebrar
                            from "Pedido" p join "Evento" e on e.id = p."eventoId"
                            where p.estado = 'PAGADO' and p."promotorId" is not null
                            group by 1
                        ),
                        cabezas as (
                            select li."promotorId" as promotor,
                                   coalesce(sum(i.personas) filter (where i.entro), 0) as entraron
                            from "ListaInvitados" li join "Evento" e on e.id = li."eventoId"
                            join "Invitado" i on i."listaInvitadosId" = li.id
                            where li."liquidacionId" is null and e."empiezaEn" < (select t from ahora)
                            group by 1
                        ),
                        pagos as (
                            select "promotorId" as promotor, coalesce(sum("totalCentimos"), 0) as pagado,
                                   max("pagadaEn") as ultimo
                            from "LiquidacionPromotor" group by 1
                        )
                        select u.id, coalesce(u."comisionPct", 0) as pct, u."bonoInvitadoCentimos" as bono,
                               coalesce(v.pedidos, 0) as pedidos, coalesce(v.ventas, 0) as ventas,
                               coalesce(v.pendiente, 0) as pendiente, coalesce(v.por_celebrar, 0) as por_celebrar,
                               coalesce(c.entraron, 0) as entraron,
                               coalesce(pg.pagado, 0) as pagado, pg.ultimo
                        from "Usuario" u
                        left join ventas v on v.promotor = u.id
                        left join cabezas c on c.promotor = u.id
                        left join pagos pg on pg.promotor = u.id
                        where u."codigoPromotor" is not null
                        """)
                .query((rs, i) -> {
                    int pct = rs.getInt("pct");
                    LocalDateTime ultimo = rs.getObject("ultimo", LocalDateTime.class);
                    return new Saldo(
                            rs.getObject("id", Long.class),
                            rs.getLong("pedidos"),
                            rs.getLong("ventas"),
                            Math.round(rs.getLong("pendiente") * pct / 100.0) + rs.getLong("entraron") * rs.getLong("bono"),
                            Math.round(rs.getLong("por_celebrar") * pct / 100.0),
                            rs.getLong("pagado"),
                            ultimo == null ? null : ultimo.toInstant(ZoneOffset.UTC));
                })
                .list()
                .stream()
                .collect(Collectors.toMap(Saldo::promotorId, Function.identity()));
    }

    // ── Escrituras ────────────────────────────────────────────────

    /**
     * Registra un pago y marca lo que cubre.
     *
     * @param hasta incluye las noches que empezaron antes de este instante; nulo
     *     es «hasta ahora». Nunca más allá de ahora: lo que no ha pasado no se paga.
     * @param ajusteCentimos lo que se suma o resta a mano. Con nota obligatoria.
     */
    @Transactional
    public Liquidacion liquidar(
            Long promotorId,
            Instant hasta,
            MetodoDePago metodo,
            String referencia,
            String nota,
            int ajusteCentimos,
            Usuario quien) {

        if (metodo == null) {
            throw new ComisionRechazada("Elige cómo se le pagó.");
        }
        if (ajusteCentimos != 0 && (nota == null || nota.isBlank())) {
            throw new ComisionRechazada("Un ajuste a mano necesita una nota que diga por qué.");
        }

        // Se bloquea la fila del promotor: dos liquidaciones suyas a la vez
        // esperan una a la otra en vez de pelearse por los mismos pedidos.
        sql.sql("select id from \"Usuario\" where id = :id for update").param("id", promotorId).query(Long.class).optional();
        Usuario promotor = usuarios.findById(promotorId)
                .filter(Usuario::esPromotor)
                .orElseThrow(() -> new ComisionRechazada("Esa persona no es promotor."));

        Instant ahora = Instant.now();
        Instant corte = hasta == null || hasta.isAfter(ahora) ? ahora : hasta;

        Liquidacion liquidacion = liquidaciones.saveAndFlush(
                Liquidacion.abrir(promotor, corte, metodo, referencia, nota, ajusteCentimos, quien, ahora));

        record Marcado(long entradas, long ventas) {}
        List<Marcado> pedidos = sql.sql("""
                        update "Pedido" p set "liquidacionId" = :liquidacion
                        from "Evento" e
                        where e.id = p."eventoId"
                          and p."promotorId" = :promotor and p.estado = 'PAGADO' and p."liquidacionId" is null
                          and e."empiezaEn" < :corte
                        returning (select coalesce(sum(l.cantidad), 0) from "LineaPedido" l where l."pedidoId" = p.id)
                                      as entradas,
                                  p."subtotalCentimos" as ventas
                        """)
                .param("liquidacion", liquidacion.getId())
                .param("promotor", promotorId)
                .param("corte", LocalDateTime.ofInstant(corte, ZoneOffset.UTC))
                .query((rs, i) -> new Marcado(rs.getLong("entradas"), rs.getLong("ventas")))
                .list();

        List<Long> listas = sql.sql("""
                        update "ListaInvitados" li set "liquidacionId" = :liquidacion
                        from "Evento" e
                        where e.id = li."eventoId"
                          and li."promotorId" = :promotor and li."liquidacionId" is null
                          and e."empiezaEn" < :corte
                        returning li.id
                        """)
                .param("liquidacion", liquidacion.getId())
                .param("promotor", promotorId)
                .param("corte", LocalDateTime.ofInstant(corte, ZoneOffset.UTC))
                .query(Long.class)
                .list();

        long entraron = listas.isEmpty() ? 0 : sql.sql("""
                        select coalesce(sum(personas) filter (where entro), 0) from "Invitado"
                        where "listaInvitadosId" in (:listas)
                        """)
                .param("listas", listas)
                .query(Long.class)
                .single();

        if (pedidos.isEmpty() && entraron == 0 && ajusteCentimos == 0) {
            // La transacción se deshace entera: ni la liquidación vacía ni las
            // listas sin nadie dentro quedan marcadas.
            throw new ComisionRechazada(promotor.getNombre() + " no tiene nada pendiente de cobro hasta esa fecha.");
        }

        liquidacion.fijarCifras(
                pedidos.size(),
                Math.toIntExact(pedidos.stream().mapToLong(Marcado::entradas).sum()),
                pedidos.stream().mapToLong(Marcado::ventas).sum(),
                Math.toIntExact(entraron));

        if (liquidacion.getTotalCentimos() < 0) {
            throw new ComisionRechazada("El ajuste deja el pago en negativo.");
        }
        return liquidacion;
    }

    /**
     * Anula un pago mal registrado: lo que cubría vuelve a estar pendiente.
     *
     * <p>Se borra en vez de marcarse como anulado porque no representa dinero
     * que se moviera: es una corrección de quien lo apuntó. Si el pago sí se
     * hizo, lo que toca es registrar uno nuevo con un ajuste en negativo.
     */
    @Transactional
    public void anular(Long liquidacionId) {
        Liquidacion l = liquidaciones.findById(liquidacionId)
                .orElseThrow(() -> new ComisionRechazada("Esa liquidación no existe."));
        sql.sql("update \"Pedido\" set \"liquidacionId\" = null where \"liquidacionId\" = :id")
                .param("id", l.getId()).update();
        sql.sql("update \"ListaInvitados\" set \"liquidacionId\" = null where \"liquidacionId\" = :id")
                .param("id", l.getId()).update();
        liquidaciones.delete(l);
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    private static int porcentaje(Usuario promotor) {
        return promotor.getComisionPorcentaje() == null ? 0 : promotor.getComisionPorcentaje();
    }

    private static LocalDateTime corte(Instant hasta) {
        Instant ahora = Instant.now();
        Instant c = hasta == null || hasta.isAfter(ahora) ? ahora : hasta;
        return LocalDateTime.ofInstant(c, ZoneOffset.UTC);
    }
}
