package pe.ssiomama.backend.noches;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Todo lo que pasó en una noche, en un solo sitio.
 *
 * <h2>Por qué existe</h2>
 *
 * <p>Las cifras de una noche estaban repartidas en seis pantallas: la taquilla en
 * pedidos, la gente en la puerta, la barra en la caja, los promotores en su
 * página, los gastos en la suya. Para contestar «¿cómo fue el sábado?» había que
 * abrirlas todas y sumar de cabeza. Esto las cruza una vez y las devuelve juntas.
 *
 * <h2>Lo leen dos: una persona y la IA</h2>
 *
 * <p>El panel pinta este expediente tal cual, y el agente de informes recibe su
 * versión en texto ({@link TextoDeExpediente}). Que sea <b>la misma</b> fuente
 * es a propósito: si la IA dice «entraron 448», es porque la pantalla dice 448.
 * Ninguna cifra la calcula el modelo.
 *
 * <h2>Qué se cuenta como «dentro»</h2>
 *
 * <p>Quien pasó por la puerta con entrada (un acceso escaneado) más quien entró
 * por lista de invitados. No quien compró: una entrada vendida a alguien que no
 * vino no ocupa la pista ni bebe. Los reservados no se suman aparte porque su
 * gente entra por una de esas dos vías.
 *
 * <h2>Las horas</h2>
 *
 * <p>La base guarda UTC. Todo lo que se agrupa por hora se pasa antes a la hora
 * de Arequipa: una curva de entrada que empieza a las cuatro de la mañana no le
 * sirve a nadie.
 */
@Service
public class ExpedienteDeNoche {

    static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Locale ES_PE = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:00");

    /** Cuántas noches parecidas se miran para comparar. */
    private static final int NOCHES_DE_REFERENCIA = 6;

    /** El instante local de una columna UTC, truncado a la hora. */
    private static String horaLocal(String columna) {
        return "date_trunc('hour', (" + columna + " at time zone 'utc') at time zone 'America/Lima')";
    }

    private final JdbcClient sql;

    public ExpedienteDeNoche(JdbcClient sql) {
        this.sql = sql;
    }

    // ── Lo que sale ───────────────────────────────────────────────

    /** Una observación que salta a la vista. {@code tono}: bien, mal o neutro. */
    public record Aviso(String tono, String texto) {}

    public record Noche(
            String id,
            String slug,
            String titulo,
            String estado,
            String diaDeLaSemana,
            Instant puertasA,
            Instant empiezaA,
            Instant terminaA,
            String sala,
            int aforo,
            List<String> cartel,
            boolean pasada) {}

    public record Tramo(String hora, long personas) {}

    public record PorPuerta(String puerta, long personas) {}

    public record Asistencia(
            long entradasEmitidas,
            long entraronConEntrada,
            long noVinieron,
            /** Porcentaje de entradas emitidas que nadie usó. Nulo sin entradas. */
            Integer noVinieronPct,
            long invitadosApuntados,
            long invitadosEntraron,
            long dentro,
            /** Dentro sobre aforo. Nulo si la sala no tiene aforo. */
            Integer ocupacionPct,
            Tramo pico,
            Instant primeraEntrada,
            Instant ultimaEntrada,
            List<Tramo> porHora,
            List<PorPuerta> porPuerta) {}

    public record PorTipo(String tipo, int precioCentimos, int cupo, long vendidas, long ingresosCentimos) {}

    public record PorMetodo(String metodo, long operaciones, long centimos) {}

    public record PorCanal(String canal, long pedidos, long entradas, long centimos) {}

    public record Taquilla(
            long pedidosPagados,
            long entradasVendidas,
            /** Lo que pagó el público, gastos de gestión incluidos. */
            long brutoCentimos,
            long gestionCentimos,
            /** El precio de las entradas sin gastos de gestión: la base de las comisiones. */
            long entradasCentimos,
            long ticketMedioCentimos,
            long pendientes,
            long cancelados,
            long devueltos,
            long devueltoCentimos,
            List<PorTipo> porTipo,
            List<PorMetodo> porMetodo,
            List<PorCanal> porCanal) {}

    public record PorBarra(String barra, long cajas, long tickets, long unidades, long centimos) {}

    public record CajaDeLaNoche(
            String id,
            String barra,
            String responsable,
            Instant abiertaEn,
            Instant cerradaEn,
            long tickets,
            long vendidoCentimos,
            long efectivoCentimos,
            /** Contado menos esperado. Nulo mientras siga abierta. */
            Integer descuadreCentimos) {}

    public record TramoDeBarra(String hora, long tickets, long centimos) {}

    public record ProductoVendido(
            String producto,
            String categoria,
            long unidades,
            long ingresosCentimos,
            long costeCentimos,
            long margenCentimos) {}

    public record BarraDeLaNoche(
            long totalCentimos,
            long tickets,
            long unidades,
            long ticketMedioCentimos,
            /** Lo que gastó en barra cada persona que entró. Nulo si no entró nadie. */
            Long gastoPorPersonaCentimos,
            long costeProductoCentimos,
            long margenCentimos,
            List<PorBarra> porBarra,
            List<CajaDeLaNoche> cajas,
            List<PorMetodo> porMetodo,
            List<TramoDeBarra> porHora,
            List<ProductoVendido> productos) {}

    public record PromotorDeLaNoche(
            String id,
            String nombre,
            String codigo,
            long pedidos,
            long entradas,
            long ventasCentimos,
            int comisionPct,
            long comisionCentimos,
            long listas,
            long apuntados,
            long entraron,
            long bonoCentimos,
            long aPagarCentimos,
            /** Si lo de esta noche ya entró en una liquidación. */
            boolean liquidado) {}

    public record ReservaDeLaNoche(
            String box,
            String cliente,
            int personas,
            String estado,
            long depositoCentimos,
            long consumoMinimoCentimos) {}

    public record Reservas(
            long total,
            long confirmadas,
            long sentadas,
            long noVinieron,
            long canceladas,
            long personas,
            long depositosCentimos,
            List<ReservaDeLaNoche> lista) {}

    public record PorCategoria(String categoria, long centimos) {}

    public record PorPuesto(String puesto, long personas, double horas, long centimos) {}

    public record Costes(
            long gastosCentimos,
            List<PorCategoria> gastosPorCategoria,
            long personalCentimos,
            List<PorPuesto> personalPorPuesto,
            long comisionesCentimos,
            long costeProductoCentimos,
            long totalCentimos) {}

    public record Resultado(
            long ingresosCentimos,
            long costesCentimos,
            long resultadoCentimos,
            /** Resultado sobre ingresos. Nulo sin ingresos. */
            Integer margenPct) {}

    public record Distrito(String distrito, long clientes) {}

    public record Publico(
            long compradores,
            long nuevos,
            long recurrentes,
            /** Edad media de quien compró y dejó su fecha de nacimiento. */
            Integer edadMedia,
            long aceptanPublicidad,
            List<Distrito> distritos) {}

    public record IncidenciaEnFicha(
            String id, String tipo, String descripcion, Instant ocurrioEn, String registradaPor) {}

    public record Referencia(String slug, String titulo, Instant empiezaA, long dentro, long taquillaCentimos, long barraCentimos) {}

    /**
     * Esta noche frente a la media de las anteriores del mismo día de la semana.
     *
     * <p>Comparar un jueves con un sábado no dice nada; comparar este sábado con
     * los seis sábados anteriores, sí.
     */
    public record Comparativa(
            String criterio,
            int noches,
            long mediaDentro,
            long mediaTaquillaCentimos,
            long mediaBarraCentimos,
            Integer dentroVsMediaPct,
            Integer taquillaVsMediaPct,
            Integer barraVsMediaPct,
            List<Referencia> referencias) {}

    public record Expediente(
            Instant generadoEn,
            Noche noche,
            List<Aviso> avisos,
            Asistencia asistencia,
            Taquilla taquilla,
            BarraDeLaNoche barra,
            List<PromotorDeLaNoche> promotores,
            Reservas reservas,
            Costes costes,
            Resultado resultado,
            Publico publico,
            List<IncidenciaEnFicha> incidencias,
            String notasDeCierre,
            Comparativa comparativa) {}

    /** Una fila del histórico: cada noche celebrada con sus cifras clave. */
    public record NocheEnHistorico(
            String slug,
            String titulo,
            Instant empiezaA,
            String diaDeLaSemana,
            int aforo,
            long dentro,
            Integer ocupacionPct,
            long entradasVendidas,
            long taquillaCentimos,
            long barraCentimos,
            long costesCentimos,
            long resultadoCentimos,
            long incidencias) {}

    // ── El expediente ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<Expediente> de(String slug) {
        return noche(slug).map(this::armar);
    }

    private Expediente armar(Noche n) {
        Asistencia asistencia = asistencia(n);
        Taquilla taquilla = taquilla(n.id());
        BarraDeLaNoche barra = barra(n.id(), asistencia.dentro());
        List<PromotorDeLaNoche> promotores = promotores(n.id());
        Reservas reservas = reservas(n.id());
        Costes costes = costes(n.id(), promotores, barra.costeProductoCentimos());
        Resultado resultado = resultado(taquilla, barra, costes);
        Comparativa comparativa = comparativa(n, asistencia.dentro(), taquilla.brutoCentimos(), barra.totalCentimos());

        return new Expediente(
                Instant.now(),
                n,
                avisos(n, asistencia, taquilla, barra, promotores, reservas, resultado, comparativa),
                asistencia,
                taquilla,
                barra,
                promotores,
                reservas,
                costes,
                resultado,
                publico(n.id()),
                incidencias(n.id()),
                notasDeCierre(n.id()),
                comparativa);
    }

    // ── La noche ──────────────────────────────────────────────────

    private Optional<Noche> noche(String slug) {
        record Fila(String id, String slug, String titulo, String estado, Instant puertas, Instant empieza,
                Instant termina, String sala, int aforo) {}

        return sql.sql("""
                        select e.id, e.slug, e.titulo, e.estado::text as estado, e."aperturaPuertas",
                               e."empiezaEn", e."terminaEn", s.nombre as sala, s.aforo
                        from "Evento" e join "Sala" s on s.id = e."salaId"
                        where e.slug = :slug
                        """)
                .param("slug", slug)
                .query((rs, i) -> new Fila(
                        rs.getString("id"),
                        rs.getString("slug"),
                        rs.getString("titulo"),
                        rs.getString("estado"),
                        instante(rs, "aperturaPuertas"),
                        instante(rs, "empiezaEn"),
                        instante(rs, "terminaEn"),
                        rs.getString("sala"),
                        rs.getInt("aforo")))
                .optional()
                .map(f -> new Noche(
                        f.id(), f.slug(), f.titulo(), f.estado(), diaDeLaSemana(f.empieza()),
                        f.puertas(), f.empieza(), f.termina(), f.sala(), f.aforo(),
                        cartel(f.id()), f.empieza().isBefore(Instant.now())));
    }

    private List<String> cartel(String eventoId) {
        return sql.sql("""
                        select a.nombre from "EventoArtista" ea join "Artista" a on a.id = ea."artistaId"
                        where ea."eventoId" = :id
                        order by ea."cabezaCartel" desc, ea."inicioSet" nulls last, a.nombre
                        """)
                .param("id", eventoId)
                .query(String.class)
                .list();
    }

    // ── Asistencia ────────────────────────────────────────────────

    private Asistencia asistencia(Noche n) {
        String id = n.id();

        long emitidas = contar("""
                select count(*) from "Entrada" where "eventoId" = :id and estado in ('VALIDA', 'USADA')
                """, id);
        long conEntrada = contar("select count(*) from \"Acceso\" where \"eventoId\" = :id", id);

        record Invitados(long apuntados, long entraron) {}
        Invitados inv = sql.sql("""
                        select coalesce(sum(i.personas), 0) as apuntados,
                               coalesce(sum(i.personas) filter (where i.entro), 0) as entraron
                        from "ListaInvitados" l join "Invitado" i on i."listaInvitadosId" = l.id
                        where l."eventoId" = :id
                        """)
                .param("id", id)
                .query((rs, i) -> new Invitados(rs.getLong("apuntados"), rs.getLong("entraron")))
                .single();

        List<Tramo> porHora = sql.sql("""
                        select %s as hora, count(*) as personas
                        from "Acceso" where "eventoId" = :id
                        group by 1 order by 1
                        """.formatted(horaLocal("\"escaneadoEn\"")))
                .param("id", id)
                .query((rs, i) -> new Tramo(etiquetaDeHora(rs), rs.getLong("personas")))
                .list();

        List<PorPuerta> porPuerta = sql.sql("""
                        select coalesce(p.nombre, 'Sin puerta') as puerta, count(*) as personas
                        from "Acceso" a left join "Puerta" p on p.id = a."puertaId"
                        where a."eventoId" = :id
                        group by 1 order by personas desc
                        """)
                .param("id", id)
                .query((rs, i) -> new PorPuerta(rs.getString("puerta"), rs.getLong("personas")))
                .list();

        record Extremos(Instant primera, Instant ultima) {}
        Extremos extremos = sql.sql("""
                        select min("escaneadoEn") as primera, max("escaneadoEn") as ultima
                        from "Acceso" where "eventoId" = :id
                        """)
                .param("id", id)
                .query((rs, i) -> new Extremos(instante(rs, "primera"), instante(rs, "ultima")))
                .single();

        long dentro = conEntrada + inv.entraron();
        // Antes de la noche, «no vinieron» no tiene sentido: todavía no ha venido nadie.
        long noVinieron = n.pasada() ? Math.max(0, emitidas - conEntrada) : 0;

        return new Asistencia(
                emitidas,
                conEntrada,
                noVinieron,
                n.pasada() ? porcentaje(noVinieron, emitidas) : null,
                inv.apuntados(),
                inv.entraron(),
                dentro,
                porcentaje(dentro, n.aforo()),
                porHora.stream().max(Comparator.comparingLong(Tramo::personas)).orElse(null),
                extremos.primera(),
                extremos.ultima(),
                porHora,
                porPuerta);
    }

    // ── Taquilla ──────────────────────────────────────────────────

    private Taquilla taquilla(String id) {
        record Totales(long pagados, long bruto, long gestion, long entradasCentimos, long pendientes,
                long cancelados, long devueltos, long devuelto) {}

        Totales t = sql.sql("""
                        select count(*) filter (where estado = 'PAGADO') as pagados,
                               coalesce(sum("totalCentimos") filter (where estado = 'PAGADO'), 0) as bruto,
                               coalesce(sum("comisionCentimos") filter (where estado = 'PAGADO'), 0) as gestion,
                               coalesce(sum("subtotalCentimos") filter (where estado = 'PAGADO'), 0) as entradas,
                               count(*) filter (where estado = 'PENDIENTE') as pendientes,
                               count(*) filter (where estado = 'CANCELADO') as cancelados,
                               count(*) filter (where estado = 'DEVUELTO') as devueltos,
                               coalesce(sum("totalCentimos") filter (where estado = 'DEVUELTO'), 0) as devuelto
                        from "Pedido" where "eventoId" = :id
                        """)
                .param("id", id)
                .query((rs, i) -> new Totales(
                        rs.getLong("pagados"), rs.getLong("bruto"), rs.getLong("gestion"), rs.getLong("entradas"),
                        rs.getLong("pendientes"), rs.getLong("cancelados"), rs.getLong("devueltos"),
                        rs.getLong("devuelto")))
                .single();

        // Lo vendido por tipo sale de las líneas pagadas, no del contador
        // «vendidas» del tipo: ese cuenta también lo apartado por pedidos que
        // nadie pagó todavía.
        List<PorTipo> porTipo = sql.sql("""
                        select t.nombre, t."precioCentimos", t.cantidad,
                               coalesce(sum(l.cantidad) filter (where p.estado = 'PAGADO'), 0) as vendidas,
                               coalesce(sum(l.cantidad * l."precioUnitarioCentimos")
                                        filter (where p.estado = 'PAGADO'), 0) as ingresos
                        from "TipoEntrada" t
                        left join "LineaPedido" l on l."tipoEntradaId" = t.id
                        left join "Pedido" p on p.id = l."pedidoId"
                        where t."eventoId" = :id
                        group by t.id, t.nombre, t."precioCentimos", t.cantidad
                        order by t."precioCentimos"
                        """)
                .param("id", id)
                .query((rs, i) -> new PorTipo(
                        rs.getString("nombre"), rs.getInt("precioCentimos"), rs.getInt("cantidad"),
                        rs.getLong("vendidas"), rs.getLong("ingresos")))
                .list();

        List<PorMetodo> porMetodo = sql.sql("""
                        select coalesce("metodoPago"::text, 'SIN_DATO') as metodo, count(*) as operaciones,
                               coalesce(sum("totalCentimos"), 0) as centimos
                        from "Pedido" where "eventoId" = :id and estado = 'PAGADO'
                        group by 1 order by centimos desc
                        """)
                .param("id", id)
                .query(ExpedienteDeNoche::porMetodo)
                .list();

        List<PorCanal> porCanal = sql.sql("""
                        select case when p."promotorId" is null then 'Venta directa'
                                    else 'Promotor: ' || u.nombre end as canal,
                               count(*) as pedidos,
                               coalesce(sum((select sum(l.cantidad) from "LineaPedido" l where l."pedidoId" = p.id)), 0)
                                   as entradas,
                               coalesce(sum(p."totalCentimos"), 0) as centimos
                        from "Pedido" p left join "Usuario" u on u.id = p."promotorId"
                        where p."eventoId" = :id and p.estado = 'PAGADO'
                        group by 1 order by centimos desc
                        """)
                .param("id", id)
                .query((rs, i) -> new PorCanal(
                        rs.getString("canal"), rs.getLong("pedidos"), rs.getLong("entradas"), rs.getLong("centimos")))
                .list();

        long entradasVendidas = porTipo.stream().mapToLong(PorTipo::vendidas).sum();

        return new Taquilla(
                t.pagados(),
                entradasVendidas,
                t.bruto(),
                t.gestion(),
                t.entradasCentimos(),
                t.pagados() == 0 ? 0 : t.bruto() / t.pagados(),
                t.pendientes(),
                t.cancelados(),
                t.devueltos(),
                t.devuelto(),
                porTipo,
                porMetodo,
                porCanal);
    }

    // ── Barra ─────────────────────────────────────────────────────

    private BarraDeLaNoche barra(String id, long dentro) {
        List<CajaDeLaNoche> cajas = sql.sql("""
                        select s.id, coalesce(pu.nombre, 'Sin barra asignada') as barra, u.nombre as responsable,
                               s."abiertaEn", s."cerradaEn", s."descuadreCentimos",
                               count(v.id) as tickets,
                               coalesce(sum(v."totalCentimos"), 0) as vendido,
                               coalesce(sum(v."totalCentimos") filter (where v."metodoPago" = 'EFECTIVO'), 0) as efectivo
                        from "SesionCaja" s
                        join "Usuario" u on u.id = s."usuarioId"
                        left join "Puesto" pu on pu.id = s."puestoId"
                        join "VentaBarra" v on v."sesionCajaId" = s.id and v."eventoId" = :id
                        group by s.id, pu.nombre, u.nombre, s."abiertaEn", s."cerradaEn", s."descuadreCentimos"
                        order by vendido desc
                        """)
                .param("id", id)
                .query((rs, i) -> new CajaDeLaNoche(
                        rs.getString("id"),
                        rs.getString("barra"),
                        rs.getString("responsable"),
                        instante(rs, "abiertaEn"),
                        instante(rs, "cerradaEn"),
                        rs.getLong("tickets"),
                        rs.getLong("vendido"),
                        rs.getLong("efectivo"),
                        (Integer) rs.getObject("descuadreCentimos")))
                .list();

        List<PorBarra> porBarra = sql.sql("""
                        select coalesce(pu.nombre, 'Sin barra asignada') as barra,
                               count(distinct s.id) as cajas,
                               count(distinct v.id) as tickets,
                               coalesce(sum(l.cantidad), 0) as unidades,
                               coalesce(sum(l.cantidad * l."precioUnitarioCentimos"), 0) as centimos
                        from "VentaBarra" v
                        join "SesionCaja" s on s.id = v."sesionCajaId"
                        left join "Puesto" pu on pu.id = s."puestoId"
                        left join "LineaVentaBarra" l on l."ventaBarraId" = v.id
                        where v."eventoId" = :id
                        group by 1 order by centimos desc
                        """)
                .param("id", id)
                .query((rs, i) -> new PorBarra(
                        rs.getString("barra"), rs.getLong("cajas"), rs.getLong("tickets"),
                        rs.getLong("unidades"), rs.getLong("centimos")))
                .list();

        List<PorMetodo> porMetodo = sql.sql("""
                        select "metodoPago"::text as metodo, count(*) as operaciones,
                               coalesce(sum("totalCentimos"), 0) as centimos
                        from "VentaBarra" where "eventoId" = :id
                        group by 1 order by centimos desc
                        """)
                .param("id", id)
                .query(ExpedienteDeNoche::porMetodo)
                .list();

        List<TramoDeBarra> porHora = sql.sql("""
                        select %s as hora, count(*) as tickets, coalesce(sum("totalCentimos"), 0) as centimos
                        from "VentaBarra" where "eventoId" = :id
                        group by 1 order by 1
                        """.formatted(horaLocal("\"creadoEn\"")))
                .param("id", id)
                .query((rs, i) -> new TramoDeBarra(etiquetaDeHora(rs), rs.getLong("tickets"), rs.getLong("centimos")))
                .list();

        // El coste es el coste actual de cada producto: el histórico de costes
        // no se guarda por venta. Sirve para el orden de magnitud del margen,
        // no para la contabilidad.
        List<ProductoVendido> productos = sql.sql("""
                        select pr.nombre as producto, c.nombre as categoria,
                               sum(l.cantidad) as unidades,
                               sum(l.cantidad * l."precioUnitarioCentimos") as ingresos,
                               sum(l.cantidad * pr."costeCentimos") as coste
                        from "LineaVentaBarra" l
                        join "VentaBarra" v on v.id = l."ventaBarraId"
                        join "Producto" pr on pr.id = l."productoId"
                        join "CategoriaProducto" c on c.id = pr."categoriaId"
                        where v."eventoId" = :id
                        group by pr.nombre, c.nombre
                        order by ingresos desc
                        """)
                .param("id", id)
                .query((rs, i) -> new ProductoVendido(
                        rs.getString("producto"),
                        rs.getString("categoria"),
                        rs.getLong("unidades"),
                        rs.getLong("ingresos"),
                        rs.getLong("coste"),
                        rs.getLong("ingresos") - rs.getLong("coste")))
                .list();

        long total = porMetodo.stream().mapToLong(PorMetodo::centimos).sum();
        long tickets = porMetodo.stream().mapToLong(PorMetodo::operaciones).sum();
        long unidades = productos.stream().mapToLong(ProductoVendido::unidades).sum();
        long coste = productos.stream().mapToLong(ProductoVendido::costeCentimos).sum();

        return new BarraDeLaNoche(
                total,
                tickets,
                unidades,
                tickets == 0 ? 0 : total / tickets,
                dentro == 0 ? null : total / dentro,
                coste,
                total - coste,
                porBarra,
                cajas,
                porMetodo,
                porHora,
                productos);
    }

    // ── Promotores ────────────────────────────────────────────────

    private List<PromotorDeLaNoche> promotores(String id) {
        return sql.sql("""
                        with ventas as (
                            select p."promotorId" as promotor,
                                   count(*) as pedidos,
                                   coalesce(sum((select sum(l.cantidad) from "LineaPedido" l
                                                 where l."pedidoId" = p.id)), 0) as entradas,
                                   coalesce(sum(p."subtotalCentimos"), 0) as ventas,
                                   bool_and(p."liquidacionId" is not null) as liquidado
                            from "Pedido" p
                            where p."eventoId" = :id and p.estado = 'PAGADO' and p."promotorId" is not null
                            group by 1
                        ),
                        listas as (
                            select l."promotorId" as promotor,
                                   count(distinct l.id) as listas,
                                   coalesce(sum(i.personas), 0) as apuntados,
                                   coalesce(sum(i.personas) filter (where i.entro), 0) as entraron,
                                   bool_and(l."liquidacionId" is not null) as liquidado
                            from "ListaInvitados" l left join "Invitado" i on i."listaInvitadosId" = l.id
                            where l."eventoId" = :id
                            group by 1
                        )
                        select u.id, u.nombre, u."codigoPromotor" as codigo,
                               coalesce(u."comisionPct", 0) as pct, u."bonoInvitadoCentimos" as bono,
                               coalesce(v.pedidos, 0) as pedidos, coalesce(v.entradas, 0) as entradas,
                               coalesce(v.ventas, 0) as ventas,
                               coalesce(li.listas, 0) as listas, coalesce(li.apuntados, 0) as apuntados,
                               coalesce(li.entraron, 0) as entraron,
                               coalesce(v.liquidado, true) and coalesce(li.liquidado, true) as liquidado
                        from "Usuario" u
                        left join ventas v on v.promotor = u.id
                        left join listas li on li.promotor = u.id
                        where v.promotor is not null or li.promotor is not null
                        """)
                .param("id", id)
                .query((rs, i) -> {
                    long ventas = rs.getLong("ventas");
                    int pct = rs.getInt("pct");
                    long comision = Math.round(ventas * pct / 100.0);
                    long bono = rs.getLong("entraron") * rs.getLong("bono");
                    return new PromotorDeLaNoche(
                            rs.getString("id"),
                            rs.getString("nombre"),
                            rs.getString("codigo"),
                            rs.getLong("pedidos"),
                            rs.getLong("entradas"),
                            ventas,
                            pct,
                            comision,
                            rs.getLong("listas"),
                            rs.getLong("apuntados"),
                            rs.getLong("entraron"),
                            bono,
                            comision + bono,
                            rs.getBoolean("liquidado"));
                })
                .list()
                .stream()
                .sorted(Comparator.comparingLong(PromotorDeLaNoche::aPagarCentimos).reversed())
                .toList();
    }

    // ── Reservas ──────────────────────────────────────────────────

    private Reservas reservas(String id) {
        List<ReservaDeLaNoche> lista = sql.sql("""
                        select b.nombre as box, trim(c.nombre || ' ' || coalesce(c.apellido, '')) as cliente,
                               r.personas, r.estado::text as estado, r."depositoCentimos",
                               b."consumoMinimoCentimos"
                        from "ReservaBox" r
                        join "Box" b on b.id = r."boxId"
                        join "Cliente" c on c.id = r."clienteId"
                        where r."eventoId" = :id
                        order by b.nombre
                        """)
                .param("id", id)
                .query((rs, i) -> new ReservaDeLaNoche(
                        rs.getString("box"),
                        rs.getString("cliente"),
                        rs.getInt("personas"),
                        rs.getString("estado"),
                        rs.getLong("depositoCentimos"),
                        rs.getLong("consumoMinimoCentimos")))
                .list();

        long canceladas = lista.stream().filter(r -> r.estado().equals("CANCELADO")).count();
        return new Reservas(
                lista.size(),
                lista.stream().filter(r -> r.estado().equals("CONFIRMADA")).count(),
                lista.stream().filter(r -> r.estado().equals("SENTADA")).count(),
                lista.stream().filter(r -> r.estado().equals("NO_VINO")).count(),
                canceladas,
                lista.stream().filter(r -> !r.estado().equals("CANCELADO")).mapToLong(ReservaDeLaNoche::personas).sum(),
                lista.stream().filter(r -> !r.estado().equals("CANCELADO"))
                        .mapToLong(ReservaDeLaNoche::depositoCentimos).sum(),
                lista);
    }

    // ── Costes y resultado ────────────────────────────────────────

    private Costes costes(String id, List<PromotorDeLaNoche> promotores, long costeProducto) {
        List<PorCategoria> gastos = sql.sql("""
                        select c.nombre as categoria, coalesce(sum(g."importeCentimos"), 0) as centimos
                        from "Gasto" g join "CategoriaGasto" c on c.id = g."categoriaId"
                        where g."eventoId" = :id
                        group by 1 order by centimos desc
                        """)
                .param("id", id)
                .query((rs, i) -> new PorCategoria(rs.getString("categoria"), rs.getLong("centimos")))
                .list();

        // Las horas salen de los turnos publicados para la noche; la tarifa es
        // la actual de cada persona.
        List<PorPuesto> personal = sql.sql("""
                        select pu.nombre as puesto, count(distinct t."usuarioId") as personas,
                               sum(extract(epoch from (t."terminaEn" - t."empiezaEn")) / 3600.0) as horas,
                               sum(extract(epoch from (t."terminaEn" - t."empiezaEn")) / 3600.0
                                   * u."tarifaHoraCentimos") as centimos
                        from "Turno" t
                        join "Usuario" u on u.id = t."usuarioId"
                        join "Puesto" pu on pu.id = t."puestoId"
                        where t."eventoId" = :id
                        group by 1 order by centimos desc
                        """)
                .param("id", id)
                .query((rs, i) -> new PorPuesto(
                        rs.getString("puesto"),
                        rs.getLong("personas"),
                        Math.round(rs.getDouble("horas") * 10) / 10.0,
                        Math.round(rs.getDouble("centimos"))))
                .list();

        long gastosTotal = gastos.stream().mapToLong(PorCategoria::centimos).sum();
        long personalTotal = personal.stream().mapToLong(PorPuesto::centimos).sum();
        long comisiones = promotores.stream().mapToLong(PromotorDeLaNoche::aPagarCentimos).sum();

        return new Costes(
                gastosTotal,
                gastos,
                personalTotal,
                personal,
                comisiones,
                costeProducto,
                gastosTotal + personalTotal + comisiones + costeProducto);
    }

    private static Resultado resultado(Taquilla taquilla, BarraDeLaNoche barra, Costes costes) {
        // Los depósitos de reservado no se suman: se consumen en barra y ya
        // están dentro de lo vendido allí. Sumarlos los contaría dos veces.
        long ingresos = taquilla.brutoCentimos() + barra.totalCentimos();
        long resultado = ingresos - costes.totalCentimos();
        return new Resultado(ingresos, costes.totalCentimos(), resultado, porcentaje(resultado, ingresos));
    }

    // ── Público ───────────────────────────────────────────────────

    private Publico publico(String id) {
        record Cifras(long compradores, long nuevos, Integer edad, long publicidad) {}

        Cifras c = sql.sql("""
                        with compradores as (
                            select distinct p."clienteId" as id
                            from "Pedido" p where p."eventoId" = :id and p.estado = 'PAGADO'
                        ),
                        primera as (
                            select p."clienteId" as id, min(p."pagadoEn") as primera
                            from "Pedido" p
                            where p.estado = 'PAGADO' and p."clienteId" in (select id from compradores)
                            group by 1
                        )
                        select count(*) as compradores,
                               count(*) filter (where exists (
                                   select 1 from "Pedido" p2
                                   where p2."clienteId" = c.id and p2."eventoId" = :id and p2.estado = 'PAGADO'
                                     and p2."pagadoEn" = pr.primera)) as nuevos,
                               round(avg(extract(year from age(cl."fechaNacimiento")))) as edad,
                               count(*) filter (where cl."consienteMarketing") as publicidad
                        from compradores c
                        join "Cliente" cl on cl.id = c.id
                        join primera pr on pr.id = c.id
                        """)
                .param("id", id)
                .query((rs, i) -> new Cifras(
                        rs.getLong("compradores"),
                        rs.getLong("nuevos"),
                        rs.getObject("edad") == null ? null : rs.getInt("edad"),
                        rs.getLong("publicidad")))
                .single();

        List<Distrito> distritos = sql.sql("""
                        select coalesce(nullif(trim(cl.distrito), ''), 'Sin indicar') as distrito,
                               count(distinct cl.id) as clientes
                        from "Pedido" p join "Cliente" cl on cl.id = p."clienteId"
                        where p."eventoId" = :id and p.estado = 'PAGADO'
                        group by 1 order by clientes desc limit 6
                        """)
                .param("id", id)
                .query((rs, i) -> new Distrito(rs.getString("distrito"), rs.getLong("clientes")))
                .list();

        return new Publico(c.compradores(), c.nuevos(), c.compradores() - c.nuevos(), c.edad(), c.publicidad(), distritos);
    }

    // ── Incidencias y notas ───────────────────────────────────────

    private List<IncidenciaEnFicha> incidencias(String id) {
        return sql.sql("""
                        select i.id, i.tipo, i.descripcion, i."ocurrioEn", u.nombre as quien
                        from "IncidenciaNoche" i left join "Usuario" u on u.id = i."registradaPorId"
                        where i."eventoId" = :id
                        order by i."ocurrioEn"
                        """)
                .param("id", id)
                .query((rs, i) -> new IncidenciaEnFicha(
                        rs.getString("id"),
                        rs.getString("tipo"),
                        rs.getString("descripcion"),
                        instante(rs, "ocurrioEn"),
                        rs.getString("quien")))
                .list();
    }

    private String notasDeCierre(String id) {
        return sql.sql("select \"notasCierre\" from \"Evento\" where id = :id")
                .param("id", id)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    // ── Comparativa ───────────────────────────────────────────────

    private Comparativa comparativa(Noche n, long dentro, long taquilla, long barra) {
        int dow = n.empiezaA().atZone(LIMA).minusHours(6).getDayOfWeek().getValue();

        // Mismo día de la semana (la noche a la que pertenece, no la fecha del
        // reloj: lo que empieza a la una es de la noche anterior), antes de
        // esta y con gente dentro.
        List<Referencia> referencias = sql.sql("""
                        select e.slug, e.titulo, e."empiezaEn",
                               (select count(*) from "Acceso" a where a."eventoId" = e.id)
                               + (select coalesce(sum(i.personas) filter (where i.entro), 0)
                                  from "ListaInvitados" l join "Invitado" i on i."listaInvitadosId" = l.id
                                  where l."eventoId" = e.id) as dentro,
                               (select coalesce(sum(p."totalCentimos"), 0) from "Pedido" p
                                where p."eventoId" = e.id and p.estado = 'PAGADO') as taquilla,
                               (select coalesce(sum(v."totalCentimos"), 0) from "VentaBarra" v
                                where v."eventoId" = e.id) as barra
                        from "Evento" e
                        where e.id <> :id
                          and e."empiezaEn" < :antes
                          and e.estado <> 'CANCELADO'
                          and extract(isodow from (((e."empiezaEn" at time zone 'utc') at time zone 'America/Lima')
                                                   - interval '6 hours')) = :dow
                          and exists (select 1 from "Acceso" a where a."eventoId" = e.id)
                        order by e."empiezaEn" desc
                        limit :n
                        """)
                .param("id", n.id())
                .param("antes", LocalDateTime.ofInstant(n.empiezaA(), ZoneOffset.UTC))
                .param("dow", dow)
                .param("n", NOCHES_DE_REFERENCIA)
                .query((rs, i) -> new Referencia(
                        rs.getString("slug"),
                        rs.getString("titulo"),
                        instante(rs, "empiezaEn"),
                        rs.getLong("dentro"),
                        rs.getLong("taquilla"),
                        rs.getLong("barra")))
                .list();

        String criterio = "las " + referencias.size() + " noches de " + diaDeLaSemana(n.empiezaA())
                + " anteriores con gente dentro";

        if (referencias.isEmpty()) {
            return new Comparativa(criterio, 0, 0, 0, 0, null, null, null, List.of());
        }

        long mediaDentro = Math.round(referencias.stream().mapToLong(Referencia::dentro).average().orElse(0));
        long mediaTaquilla = Math.round(referencias.stream().mapToLong(Referencia::taquillaCentimos).average().orElse(0));
        long mediaBarra = Math.round(referencias.stream().mapToLong(Referencia::barraCentimos).average().orElse(0));

        // Una noche que no ha pasado no se compara: saldría «-100 %» en todo.
        boolean comparable = n.pasada();
        return new Comparativa(
                criterio,
                referencias.size(),
                mediaDentro,
                mediaTaquilla,
                mediaBarra,
                comparable ? variacion(dentro, mediaDentro) : null,
                comparable ? variacion(taquilla, mediaTaquilla) : null,
                comparable ? variacion(barra, mediaBarra) : null,
                referencias);
    }

    // ── Avisos ────────────────────────────────────────────────────

    /**
     * Lo que salta a la vista, ya dicho en una frase.
     *
     * <p>Son reglas fijas y no la IA: un descuadre de caja o un absentismo del
     * 30 % se ven con una resta, y no tiene sentido pagar un modelo para eso.
     * La IA viene después, a explicar el porqué y proponer qué hacer.
     */
    private List<Aviso> avisos(
            Noche n,
            Asistencia a,
            Taquilla t,
            BarraDeLaNoche b,
            List<PromotorDeLaNoche> promotores,
            Reservas r,
            Resultado resultado,
            Comparativa comp) {

        List<Aviso> avisos = new ArrayList<>();

        if (!n.pasada()) {
            avisos.add(new Aviso("neutro", "La noche todavía no ha empezado: lo que ves es cómo va la venta."));
        }

        if (a.ocupacionPct() != null && a.ocupacionPct() >= 95) {
            avisos.add(new Aviso("bien", "Rozó el aforo: " + a.dentro() + " personas dentro (" + a.ocupacionPct() + " %)."));
        }
        if (a.noVinieronPct() != null && a.noVinieronPct() >= 20) {
            avisos.add(new Aviso("mal", a.noVinieronPct() + " % de las entradas vendidas no se usaron ("
                    + a.noVinieron() + " personas)."));
        }
        if (n.pasada() && a.invitadosApuntados() > 0) {
            Integer pct = porcentaje(a.invitadosEntraron(), a.invitadosApuntados());
            if (pct != null && pct < 40) {
                avisos.add(new Aviso("mal", "De las listas de invitados entró solo el " + pct + " % ("
                        + a.invitadosEntraron() + " de " + a.invitadosApuntados() + ")."));
            }
        }

        for (CajaDeLaNoche c : b.cajas()) {
            if (c.cerradaEn() == null && n.pasada()) {
                avisos.add(new Aviso("mal", "La caja de " + c.responsable() + " (" + c.barra() + ") sigue sin cerrar."));
            } else if (c.descuadreCentimos() != null && c.descuadreCentimos() != 0) {
                String sentido = c.descuadreCentimos() < 0 ? "faltan" : "sobran";
                avisos.add(new Aviso(
                        Math.abs(c.descuadreCentimos()) >= 2000 ? "mal" : "neutro",
                        "En la caja de " + c.responsable() + " (" + c.barra() + ") " + sentido + " "
                                + soles(Math.abs(c.descuadreCentimos())) + "."));
            }
        }

        if (n.pasada() && a.dentro() > 0 && b.tickets() == 0) {
            avisos.add(new Aviso("mal", "Entró gente pero no hay ventas de barra atribuidas a esta noche."));
        }
        if (r.noVinieron() > 0) {
            avisos.add(new Aviso("mal", r.noVinieron() + " " + (r.noVinieron() == 1 ? "reserva no se presentó." : "reservas no se presentaron.")));
        }

        for (PromotorDeLaNoche p : promotores) {
            if (n.pasada() && p.apuntados() >= 10) {
                Integer pct = porcentaje(p.entraron(), p.apuntados());
                if (pct != null && pct < 40) {
                    avisos.add(new Aviso("mal", "La lista de " + p.nombre() + " cumplió el " + pct + " % ("
                            + p.entraron() + " de " + p.apuntados() + ")."));
                }
            }
        }

        if (comp.dentroVsMediaPct() != null && Math.abs(comp.dentroVsMediaPct()) >= 15) {
            boolean sube = comp.dentroVsMediaPct() > 0;
            avisos.add(new Aviso(sube ? "bien" : "mal", "Entró un " + Math.abs(comp.dentroVsMediaPct()) + " % "
                    + (sube ? "más" : "menos") + " de gente que la media de " + comp.criterio() + "."));
        }
        if (comp.barraVsMediaPct() != null && Math.abs(comp.barraVsMediaPct()) >= 15) {
            boolean sube = comp.barraVsMediaPct() > 0;
            avisos.add(new Aviso(sube ? "bien" : "mal", "La barra facturó un " + Math.abs(comp.barraVsMediaPct())
                    + " % " + (sube ? "más" : "menos") + " que la media de esas noches."));
        }

        if (n.pasada() && resultado.ingresosCentimos() > 0 && resultado.resultadoCentimos() < 0) {
            avisos.add(new Aviso("mal", "La noche cerró en pérdidas: " + soles(resultado.resultadoCentimos()) + "."));
        }
        if (t.pendientes() > 0) {
            avisos.add(new Aviso("neutro", t.pendientes() + " " + (t.pendientes() == 1 ? "pedido sigue" : "pedidos siguen")
                    + " pendientes de pago y ocupan cupo."));
        }

        return avisos;
    }

    // ── Histórico ─────────────────────────────────────────────────

    /** Las noches ya celebradas, de la más reciente a la más antigua, con sus cifras clave. */
    @Transactional(readOnly = true)
    public List<NocheEnHistorico> celebradas(int limite) {
        return sql.sql("""
                        select e.slug, e.titulo, e."empiezaEn", s.aforo,
                               (select count(*) from "Acceso" a where a."eventoId" = e.id)
                               + (select coalesce(sum(i.personas) filter (where i.entro), 0)
                                  from "ListaInvitados" l join "Invitado" i on i."listaInvitadosId" = l.id
                                  where l."eventoId" = e.id) as dentro,
                               (select count(*) from "Entrada" en
                                where en."eventoId" = e.id and en.estado in ('VALIDA', 'USADA')) as vendidas,
                               (select coalesce(sum(p."totalCentimos"), 0) from "Pedido" p
                                where p."eventoId" = e.id and p.estado = 'PAGADO') as taquilla,
                               (select coalesce(sum(v."totalCentimos"), 0) from "VentaBarra" v
                                where v."eventoId" = e.id) as barra,
                               (select coalesce(sum(g."importeCentimos"), 0) from "Gasto" g
                                where g."eventoId" = e.id) as gastos,
                               (select coalesce(sum(extract(epoch from (t."terminaEn" - t."empiezaEn")) / 3600.0
                                                    * u."tarifaHoraCentimos"), 0)
                                from "Turno" t join "Usuario" u on u.id = t."usuarioId"
                                where t."eventoId" = e.id) as personal,
                               (select coalesce(sum(l.cantidad * pr."costeCentimos"), 0)
                                from "LineaVentaBarra" l
                                join "VentaBarra" v on v.id = l."ventaBarraId"
                                join "Producto" pr on pr.id = l."productoId"
                                where v."eventoId" = e.id) as coste_producto,
                               (select coalesce(sum(round(x.ventas * x.pct / 100.0) + x.entraron * x.bono), 0) from (
                                    select u.id, coalesce(u."comisionPct", 0) as pct, u."bonoInvitadoCentimos" as bono,
                                           coalesce((select sum(p."subtotalCentimos") from "Pedido" p
                                                     where p."eventoId" = e.id and p.estado = 'PAGADO'
                                                       and p."promotorId" = u.id), 0) as ventas,
                                           coalesce((select sum(i.personas) filter (where i.entro)
                                                     from "ListaInvitados" l join "Invitado" i on i."listaInvitadosId" = l.id
                                                     where l."eventoId" = e.id and l."promotorId" = u.id), 0) as entraron
                                    from "Usuario" u where u."codigoPromotor" is not null) x) as comisiones,
                               (select count(*) from "IncidenciaNoche" i where i."eventoId" = e.id) as incidencias
                        from "Evento" e join "Sala" s on s.id = e."salaId"
                        where e."empiezaEn" < (now() at time zone 'utc')
                          and e.estado <> 'CANCELADO'
                        order by e."empiezaEn" desc
                        limit :limite
                        """)
                .param("limite", Math.clamp(limite, 1, 200))
                .query((rs, i) -> {
                    Instant empieza = instante(rs, "empiezaEn");
                    long dentro = rs.getLong("dentro");
                    long taquilla = rs.getLong("taquilla");
                    long barra = rs.getLong("barra");
                    long costes = rs.getLong("gastos") + Math.round(rs.getDouble("personal"))
                            + rs.getLong("coste_producto") + rs.getLong("comisiones");
                    int aforo = rs.getInt("aforo");
                    return new NocheEnHistorico(
                            rs.getString("slug"),
                            rs.getString("titulo"),
                            empieza,
                            diaDeLaSemana(empieza),
                            aforo,
                            dentro,
                            porcentaje(dentro, aforo),
                            rs.getLong("vendidas"),
                            taquilla,
                            barra,
                            costes,
                            taquilla + barra - costes,
                            rs.getLong("incidencias"));
                })
                .list();
    }

    // ── Utilidades ────────────────────────────────────────────────

    private long contar(String consulta, String eventoId) {
        return sql.sql(consulta).param("id", eventoId).query(Long.class).single();
    }

    private static PorMetodo porMetodo(ResultSet rs, int fila) throws SQLException {
        return new PorMetodo(rs.getString("metodo"), rs.getLong("operaciones"), rs.getLong("centimos"));
    }

    private static String etiquetaDeHora(ResultSet rs) throws SQLException {
        return rs.getObject("hora", LocalDateTime.class).format(HORA);
    }

    static Instant instante(ResultSet rs, String columna) throws SQLException {
        LocalDateTime t = rs.getObject(columna, LocalDateTime.class);
        return t == null ? null : t.toInstant(ZoneOffset.UTC);
    }

    /** «sábado»: el día de la noche, no del reloj. Lo que empieza a la una es de la noche anterior. */
    static String diaDeLaSemana(Instant empieza) {
        LocalDate noche = empieza.atZone(LIMA).minusHours(6).toLocalDate();
        return noche.getDayOfWeek().getDisplayName(TextStyle.FULL, ES_PE);
    }

    static Integer porcentaje(long parte, long total) {
        return total <= 0 ? null : (int) Math.round(parte * 100.0 / total);
    }

    static Integer variacion(long valor, long media) {
        return media <= 0 ? null : (int) Math.round((valor - media) * 100.0 / media);
    }

    static String soles(long centimos) {
        java.text.NumberFormat f = java.text.NumberFormat.getNumberInstance(ES_PE);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return (centimos < 0 ? "-S/ " : "S/ ") + f.format(Math.abs(centimos) / 100.0);
    }
}
