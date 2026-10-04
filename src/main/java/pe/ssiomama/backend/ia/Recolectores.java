package pe.ssiomama.backend.ia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.clientes.Crm;
import pe.ssiomama.backend.clientes.Segmento;

/**
 * Los hechos que se le pasan a cada agente, sacados de la base en el momento.
 *
 * <p>Cada método devuelve el texto que va en el prompt, o vacío si no hay nada
 * que hacer —sin noches publicadas no hay promo que redactar—. Son consultas de
 * agregación que cruzan cinco dominios, y en SQL se leen mejor que en JPQL.
 *
 * <p><b>Ninguna cifra se calcula en el modelo.</b> Todo número que aparece en
 * una propuesta tiene que poder rastrearse hasta una fila de aquí.
 */
@Component
public class Recolectores {

    private static final String AHORA = "(now() at time zone 'utc')";

    private final JdbcClient sql;
    private final Crm crm;
    private final ModeloDeDemanda modelo;

    public Recolectores(JdbcClient sql, Crm crm, ModeloDeDemanda modelo) {
        this.sql = sql;
        this.crm = crm;
        this.modelo = modelo;
    }

    // ── Piezas compartidas ────────────────────────────────────────

    record Noche(
            String id,
            String slug,
            String titulo,
            String descripcion,
            Instant puertas,
            Instant empieza,
            Instant termina,
            String vestimenta,
            int edadMinima,
            String sala,
            int aforo,
            String estado) {}

    record Tipo(String nombre, int precio, int cantidad, int vendidas, int consumiciones) {
        int quedan() {
            return Math.max(0, cantidad - vendidas);
        }
    }

    record Artista(String nombre, String genero, Instant inicio, Instant fin) {}

    private static final String SELECT_NOCHE = """
            select e.id, e.slug, e.titulo, e.descripcion, e."aperturaPuertas", e."empiezaEn", e."terminaEn",
                   e.vestimenta, e."edadMinima", s.nombre as sala, s.aforo, e.estado::text as estado
            from "Evento" e join "Sala" s on s.id = e."salaId"
            """;

    private static Noche noche(ResultSet rs, int fila) throws SQLException {
        return new Noche(
                rs.getString("id"),
                rs.getString("slug"),
                rs.getString("titulo"),
                rs.getString("descripcion"),
                instante(rs, "aperturaPuertas"),
                instante(rs, "empiezaEn"),
                instante(rs, "terminaEn"),
                rs.getString("vestimenta"),
                rs.getInt("edadMinima"),
                rs.getString("sala"),
                rs.getInt("aforo"),
                rs.getString("estado"));
    }

    /** Las próximas noches a la venta, de la más cercana a la más lejana. */
    List<Noche> proximas(int limite) {
        return sql.sql(SELECT_NOCHE + """
                where e.estado = 'PUBLICADO' and e."empiezaEn" >= %s
                order by e."empiezaEn" limit :limite
                """.formatted(AHORA))
                .param("limite", limite)
                .query(Recolectores::noche)
                .list();
    }

    Optional<Noche> porSlug(String slug) {
        return sql.sql(SELECT_NOCHE + " where e.slug = :slug")
                .param("slug", slug)
                .query(Recolectores::noche)
                .optional();
    }

    List<Tipo> tiposALaVenta(String eventoId) {
        return sql.sql("""
                select nombre, "precioCentimos", cantidad, vendidas, "incluyeConsumo"
                from "TipoEntrada" where "eventoId" = :id and activo
                order by "precioCentimos"
                """)
                .param("id", eventoId)
                .query((rs, n) -> new Tipo(
                        rs.getString("nombre"),
                        rs.getInt("precioCentimos"),
                        rs.getInt("cantidad"),
                        rs.getInt("vendidas"),
                        rs.getInt("incluyeConsumo")))
                .list();
    }

    /** El cartel, el cabeza primero. */
    List<Artista> cartel(String eventoId) {
        return sql.sql("""
                select a.nombre, a.genero, ea."inicioSet", ea."finSet"
                from "EventoArtista" ea join "Artista" a on a.id = ea."artistaId"
                where ea."eventoId" = :id
                order by ea."cabezaCartel" desc, ea."inicioSet" nulls last
                """)
                .param("id", eventoId)
                .query((rs, n) -> new Artista(
                        rs.getString("nombre"),
                        rs.getString("genero"),
                        instante(rs, "inicioSet"),
                        instante(rs, "finSet")))
                .list();
    }

    private int vendidas(String eventoId) {
        return sql.sql("select coalesce(sum(vendidas), 0) from \"TipoEntrada\" where \"eventoId\" = :id")
                .param("id", eventoId)
                .query(Integer.class)
                .single();
    }

    // ── Marketing ─────────────────────────────────────────────────

    public Optional<String> marketing() {
        return proximas(1).stream().findFirst().map(e -> {
            String cartel = cartel(e.id()).stream()
                    .map(a -> a.nombre() + " (" + (a.genero() == null ? "sin género" : a.genero()) + ")")
                    .collect(Collectors.joining(", "));
            String entradas = tiposALaVenta(e.id()).stream()
                    .map(t -> t.nombre() + " a " + Club.soles(t.precio()) + " (quedan " + t.quedan() + ")")
                    .collect(Collectors.joining("; "));
            return String.join("\n",
                    "Evento: " + e.titulo(),
                    "Fecha: " + Club.fechaLarga(e.empieza()),
                    // Sin la hora exacta el modelo se la inventa: se midió proponiendo las 23:00.
                    "Puertas: " + Club.hora(e.puertas()) + " · Cierre: " + Club.hora(e.termina()),
                    "Sala: " + e.sala() + " (aforo " + e.aforo() + ")",
                    "Descripción actual: " + (e.descripcion() == null ? "sin descripción" : e.descripcion()),
                    "Cartel: " + (cartel.isEmpty() ? "sin confirmar" : cartel),
                    "Entradas: " + (entradas.isEmpty() ? "sin entradas cargadas" : entradas),
                    "Vestimenta: " + (e.vestimenta() == null ? "libre" : e.vestimenta()),
                    "Edad mínima: " + e.edadMinima());
        });
    }

    // ── Segmentador del CRM ───────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<String> segmentador() {
        var evaluados = crm.evaluarTodos(Instant.now());
        if (evaluados.isEmpty()) return Optional.empty();

        List<String> lineas = new ArrayList<>();
        for (var e : evaluados) {
            Segmento s = e.segmento();
            lineas.add("Segmento \"" + s.getNombre() + "\" ("
                    + (s.getDescripcion() == null ? "sin descripción" : s.getDescripcion()) + "): "
                    + e.miembros().size() + " clientes, " + Club.soles(e.valorCentimos())
                    + " de gasto acumulado.");
        }
        proximas(1).stream().findFirst().ifPresent(n -> lineas.add(
                "Próximo evento al que se les podría invitar: " + n.titulo() + ", "
                        + Club.fechaLarga(n.empieza()) + "."));
        return Optional.of(String.join("\n", lineas));
    }

    // ── Inventario (línea base) ───────────────────────────────────

    record Existencia(String sku, String nombre, String categoria, int stock, int alerta, int vendidas) {}

    List<Existencia> existencias() {
        return sql.sql("""
                select p.sku, p.nombre, c.nombre as categoria, p.stock, p."alertaStock",
                       coalesce((select sum(l.cantidad) from "LineaVentaBarra" l where l."productoId" = p.id), 0)
                         as vendidas
                from "Producto" p join "CategoriaProducto" c on c.id = p."categoriaId"
                where p.activo
                order by p.sku
                """)
                .query((rs, n) -> new Existencia(
                        rs.getString("sku"),
                        rs.getString("nombre"),
                        rs.getString("categoria"),
                        rs.getInt("stock"),
                        rs.getInt("alertaStock"),
                        rs.getInt("vendidas")))
                .list();
    }

    public Optional<String> inventario() {
        List<Existencia> stock = existencias();
        if (stock.isEmpty()) return Optional.empty();

        List<String> lineas = new ArrayList<>();
        lineas.add("Existencias actuales:");
        for (Existencia x : stock) {
            lineas.add(x.sku() + " · " + x.nombre() + " (" + x.categoria() + "): quedan " + x.stock()
                    + ", mínimo " + x.alerta() + ", vendidas históricamente " + x.vendidas() + ".");
        }
        lineas.add("");
        lineas.add("Eventos próximos:");
        List<Noche> proximas = proximas(2);
        if (proximas.isEmpty()) lineas.add("Ninguno publicado.");
        for (Noche n : proximas) {
            lineas.add(n.titulo() + ", " + Club.fechaLarga(n.empieza()) + ", " + vendidas(n.id())
                    + " entradas vendidas de un aforo de " + n.aforo() + ".");
        }
        return Optional.of(String.join("\n", lineas));
    }

    // ── Inventario con modelo ─────────────────────────────────────

    /**
     * Lo mismo que {@link #inventario()}, con la proyección ya calculada.
     *
     * <p>Cada herramienta hace lo que sabe hacer: la regresión estima cuánta
     * gente entra y cuánto bebe cada persona; el modelo de lenguaje no toca esos
     * números, solo los convierte en un pedido y lo explica; y una persona
     * aprueba.
     */
    public Optional<String> inventarioConModelo() {
        List<Existencia> stock = existencias();
        if (stock.isEmpty()) return Optional.empty();
        List<Noche> proximas = proximas(2);
        if (proximas.isEmpty()) return Optional.empty();

        // Sin histórico suficiente lanza una excepción con el motivo: mejor que
        // el agente no corra a que produzca un pedido a ojo disfrazado de cálculo.
        ModeloDeDemanda.Entrenado entrenado = modelo.entrenar(historico());

        Map<String, Integer> necesario = new HashMap<>();
        List<String> lineasEvento = new ArrayList<>();
        for (Noche n : proximas) {
            List<String> generos = cartel(n.id()).stream()
                    .map(Artista::genero).filter(g -> g != null && !g.isBlank()).distinct().toList();
            var rasgos = ModeloDeDemanda.Rasgos.de(n.empieza(), generos, n.aforo());
            var proyeccion = entrenado.proyectarConsumo(rasgos);

            lineasEvento.add(n.titulo() + ", " + Club.fechaLarga(n.empieza()) + ": se proyectan "
                    + proyeccion.asistentes() + " asistentes (aforo " + n.aforo() + ", género " + rasgos.genero()
                    + (rasgos.esSabado() ? ", sábado" : ", viernes")
                    + (rasgos.diasDesdePago() <= 3 ? ", recién cobrado" : "") + ").");
            for (var linea : proyeccion.lineas()) necesario.merge(linea.sku(), linea.unidades(), Integer::sum);
        }

        List<String> lineas = new ArrayList<>();
        lineas.add("Proyección calculada por el modelo de regresión sobre " + entrenado.noches() + " noches");
        lineas.add("(R² del ajuste de ocupación: " + String.format(java.util.Locale.ROOT, "%.3f", entrenado.r2()) + ").");
        lineas.add("");
        lineas.add("Eventos próximos:");
        lineas.addAll(lineasEvento);
        lineas.add("");
        lineas.add("Existencias frente a la proyección:");
        for (Existencia x : stock) {
            int previsto = necesario.getOrDefault(x.sku(), 0);
            int falta = Math.max(0, previsto - x.stock());
            lineas.add(x.sku() + " · " + x.nombre() + " (" + x.categoria() + "): quedan " + x.stock()
                    + ", consumo proyectado " + previsto + ", " + (falta > 0 ? "faltan " + falta : "cubierto")
                    + ". Mínimo de alerta " + x.alerta() + ".");
        }
        return Optional.of(String.join("\n", lineas));
    }

    /** Las noches ya celebradas con gente dentro, con lo que se bebió en cada una. */
    List<ModeloDeDemanda.NocheCelebrada> historico() {
        record Fila(String id, Instant fecha, int aforo, int asistentes) {}
        List<Fila> noches = sql.sql("""
                select e.id, e."empiezaEn", s.aforo,
                       (select count(*) from "Acceso" a where a."eventoId" = e.id) as asistentes
                from "Evento" e join "Sala" s on s.id = e."salaId"
                where e."empiezaEn" < %s
                  and exists (select 1 from "Acceso" a where a."eventoId" = e.id)
                order by e."empiezaEn" desc
                """.formatted(AHORA))
                .query((rs, n) -> new Fila(rs.getString("id"), instante(rs, "empiezaEn"),
                        rs.getInt("aforo"), rs.getInt("asistentes")))
                .list();

        Map<String, List<String>> generos = new HashMap<>();
        sql.sql("""
                select distinct ea."eventoId", a.genero
                from "EventoArtista" ea join "Artista" a on a.id = ea."artistaId"
                where a.genero is not null
                """)
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getString(2)))
                .list()
                .forEach(e -> generos.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));

        Map<String, List<ModeloDeDemanda.Consumo>> consumos = new HashMap<>();
        sql.sql("""
                select v."eventoId", p.sku, p.nombre, sum(l.cantidad) as unidades
                from "LineaVentaBarra" l
                join "VentaBarra" v on v.id = l."ventaBarraId"
                join "Producto" p on p.id = l."productoId"
                where v."eventoId" is not null
                group by v."eventoId", p.sku, p.nombre
                """)
                .query((rs, n) -> Map.entry(rs.getString("eventoId"),
                        new ModeloDeDemanda.Consumo(rs.getString("sku"), rs.getString("nombre"), rs.getInt("unidades"))))
                .list()
                .forEach(e -> consumos.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));

        return noches.stream()
                .map(f -> new ModeloDeDemanda.NocheCelebrada(
                        f.fecha(), f.aforo(), f.asistentes(),
                        generos.getOrDefault(f.id(), List.of()),
                        consumos.getOrDefault(f.id(), List.of())))
                .toList();
    }

    // ── Precios ───────────────────────────────────────────────────

    public Optional<String> precios() {
        List<Noche> aLaVenta = proximas(3);
        if (aLaVenta.isEmpty()) return Optional.empty();

        List<String> lineas = new ArrayList<>();
        lineas.add("Eventos a la venta ahora:");
        Instant ahora = Instant.now();
        for (Noche n : aLaVenta) {
            long dias = (long) Math.ceil(Duration.between(ahora, n.empieza()).toHours() / 24.0);
            String tipos = tiposALaVenta(n.id()).stream()
                    .map(t -> t.nombre() + " a " + Club.soles(t.precio()) + ": " + t.vendidas() + "/" + t.cantidad() + " vendidas")
                    .collect(Collectors.joining("; "));
            lineas.add(n.titulo() + " (dentro de " + dias + " días, aforo " + n.aforo() + ") — " + tipos);
        }

        lineas.add("");
        lineas.add("Histórico reciente para comparar:");
        List<String> pasadas = sql.sql(SELECT_NOCHE + """
                where e.estado = 'CERRADO' order by e."empiezaEn" desc limit 3
                """)
                .query(Recolectores::noche)
                .list()
                .stream()
                .map(n -> n.titulo() + " (" + Club.fechaCorta(n.empieza()) + "): " + vendidas(n.id()) + " vendidas, "
                        + entraron(n.id()) + " entraron, aforo " + n.aforo() + ".")
                .toList();
        if (pasadas.isEmpty()) lineas.add("Sin histórico.");
        lineas.addAll(pasadas);
        return Optional.of(String.join("\n", lineas));
    }

    private long entraron(String eventoId) {
        return sql.sql("select count(*) from \"Acceso\" where \"eventoId\" = :id")
                .param("id", eventoId)
                .query(Long.class)
                .single();
    }

    // ── Informe post-evento ───────────────────────────────────────

    public Optional<String> informe() {
        Optional<Noche> ultima = sql.sql(SELECT_NOCHE + """
                where e."empiezaEn" < %s order by e."empiezaEn" desc limit 1
                """.formatted(AHORA))
                .query(Recolectores::noche)
                .optional();
        if (ultima.isEmpty()) return Optional.empty();
        Noche n = ultima.get();

        long taquilla = sql.sql("""
                select coalesce(sum("totalCentimos"), 0) from "Pedido"
                where "eventoId" = :id and estado = 'PAGADO'
                """).param("id", n.id()).query(Long.class).single();
        long pedidos = sql.sql("""
                select count(*) from "Pedido" where "eventoId" = :id and estado = 'PAGADO'
                """).param("id", n.id()).query(Long.class).single();
        long barra = sql.sql("""
                select coalesce(sum("totalCentimos"), 0) from "VentaBarra" where "eventoId" = :id
                """).param("id", n.id()).query(Long.class).single();
        long gastos = sql.sql("""
                select coalesce(sum("importeCentimos"), 0) from "Gasto" where "eventoId" = :id
                """).param("id", n.id()).query(Long.class).single();
        String top = sql.sql("""
                select p.nombre, sum(l.cantidad) as unidades
                from "LineaVentaBarra" l
                join "VentaBarra" v on v.id = l."ventaBarraId"
                join "Producto" p on p.id = l."productoId"
                where v."eventoId" = :id
                group by p.nombre order by unidades desc limit 5
                """)
                .param("id", n.id())
                .query((rs, i) -> rs.getString(1) + " (" + rs.getInt(2) + ")")
                .list()
                .stream()
                .collect(Collectors.joining(", "));
        String artistas = cartel(n.id()).stream().map(Artista::nombre).collect(Collectors.joining(", "));

        return Optional.of(String.join("\n",
                "Evento: " + n.titulo() + " (" + Club.fechaLarga(n.empieza()) + ", " + n.sala() + ", aforo " + n.aforo() + ")",
                "Cartel: " + (artistas.isEmpty() ? "sin cartel" : artistas),
                "Entradas vendidas: " + vendidas(n.id()) + " en " + pedidos + " pedidos",
                "Personas que entraron: " + entraron(n.id()),
                "Ingresos por entradas: " + Club.soles(taquilla),
                "Ingresos de barra: " + Club.soles(barra),
                "Gastos del evento: " + Club.soles(gastos),
                "Margen: " + Club.soles(taquilla + barra - gastos),
                "Consumiciones más vendidas: " + (top.isEmpty() ? "sin ventas de barra registradas" : top)));
    }

    // ── Utilidades ────────────────────────────────────────────────

    static Instant instante(ResultSet rs, String columna) throws SQLException {
        LocalDateTime t = rs.getObject(columna, LocalDateTime.class);
        return t == null ? null : t.toInstant(ZoneOffset.UTC);
    }
}
