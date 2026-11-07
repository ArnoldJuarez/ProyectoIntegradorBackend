package pe.ssiomama.backend.ia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.clientes.Crm;
import pe.ssiomama.backend.clientes.Segmento;
import pe.ssiomama.backend.noches.ExpedienteDeNoche;
import pe.ssiomama.backend.noches.TextoDeExpediente;

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
    private final ExpedienteDeNoche expedientes;

    public Recolectores(JdbcClient sql, Crm crm, ModeloDeDemanda modelo, ExpedienteDeNoche expedientes) {
        this.sql = sql;
        this.crm = crm;
        this.modelo = modelo;
        this.expedientes = expedientes;
    }

    // ── Piezas compartidas ────────────────────────────────────────

    record Noche(
            Long id,
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

    record Tipo(String nombre, int precio, int cantidad, int vendidas, int consumiciones, String descripcion) {
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
                rs.getObject("id", Long.class),
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

    List<Tipo> tiposALaVenta(Long eventoId) {
        return sql.sql("""
                select nombre, "precioCentimos", cantidad, vendidas, "incluyeConsumo", descripcion
                from "TipoEntrada" where "eventoId" = :id and activo
                order by "precioCentimos"
                """)
                .param("id", eventoId)
                .query((rs, n) -> new Tipo(
                        rs.getString("nombre"),
                        rs.getInt("precioCentimos"),
                        rs.getInt("cantidad"),
                        rs.getInt("vendidas"),
                        rs.getInt("incluyeConsumo"),
                        rs.getString("descripcion")))
                .list();
    }

    /** El cartel, el cabeza primero. */
    List<Artista> cartel(Long eventoId) {
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

    private int vendidas(Long eventoId) {
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
        record Fila(Long id, Instant fecha, int aforo, int asistentes) {}
        List<Fila> noches = sql.sql("""
                select e.id, e."empiezaEn", s.aforo,
                       (select count(*) from "Acceso" a where a."eventoId" = e.id) as asistentes
                from "Evento" e join "Sala" s on s.id = e."salaId"
                where e."empiezaEn" < %s
                  and exists (select 1 from "Acceso" a where a."eventoId" = e.id)
                order by e."empiezaEn" desc
                """.formatted(AHORA))
                .query((rs, n) -> new Fila(rs.getObject("id", Long.class), instante(rs, "empiezaEn"),
                        rs.getInt("aforo"), rs.getInt("asistentes")))
                .list();

        // El cabeza de cartel primero: su género es el de la noche (`Rasgos.de` toma el primero).
        Map<Long, List<String>> generos = new HashMap<>();
        sql.sql("""
                select ea."eventoId", a.genero
                from "EventoArtista" ea join "Artista" a on a.id = ea."artistaId"
                where a.genero is not null
                order by ea."eventoId", ea."cabezaCartel" desc, a.nombre
                """)
                .query((rs, n) -> Map.entry(rs.getLong(1), rs.getString(2)))
                .list()
                .forEach(e -> {
                    List<String> suyos = generos.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
                    if (!suyos.contains(e.getValue())) suyos.add(e.getValue());
                });

        Map<Long, List<ModeloDeDemanda.Consumo>> consumos = new HashMap<>();
        sql.sql("""
                select v."eventoId", p.sku, p.nombre, sum(l.cantidad) as unidades
                from "LineaVentaBarra" l
                join "VentaBarra" v on v.id = l."ventaBarraId"
                join "Producto" p on p.id = l."productoId"
                where v."eventoId" is not null
                group by v."eventoId", p.sku, p.nombre
                """)
                .query((rs, n) -> Map.entry(rs.getObject("eventoId", Long.class),
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
                    // La descripción va porque cambia la lectura: una entrada «en puerta» no se vende
                    // antes de la noche, y sin decirlo el modelo propone rebajarla por «no tener ventas».
                    .map(t -> t.nombre() + " a " + Club.soles(t.precio()) + ": " + t.vendidas() + "/" + t.cantidad()
                            + " vendidas" + (t.descripcion() == null ? "" : " (" + t.descripcion() + ")"))
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

    private long entraron(Long eventoId) {
        return sql.sql("select count(*) from \"Acceso\" where \"eventoId\" = :id")
                .param("id", eventoId)
                .query(Long.class)
                .single();
    }

    // ── Informe post-evento ───────────────────────────────────────

    /**
     * La última noche celebrada, con su expediente completo.
     *
     * <p>Antes el informe recibía nueve cifras sueltas (taquilla, barra,
     * gastos…). Ahora recibe lo mismo que ve la jefatura de sala en el panel:
     * asistencia por hora, cada barra y cada caja, promotores, costes de
     * personal, incidencias y la comparación con noches parecidas. Con nueve
     * cifras el modelo solo podía repetirlas; con el expediente puede explicar
     * el porqué.
     *
     * <p>«Celebrada» es que ya empezó y que entró alguien: una noche sin un solo
     * acceso no tiene nada que contar.
     */
    public Optional<String> informe() {
        return sql.sql("""
                        select e.slug from "Evento" e
                        where e."empiezaEn" < %s and e.estado <> 'CANCELADO'
                          and exists (select 1 from "Acceso" a where a."eventoId" = e.id)
                        order by e."empiezaEn" desc limit 1
                        """.formatted(AHORA))
                .query(String.class)
                .optional()
                .flatMap(this::informe);
    }

    /** El expediente de una noche concreta, para pedir su informe desde su ficha. */
    public Optional<String> informe(String slug) {
        return expedientes.de(slug).map(TextoDeExpediente::de);
    }

    // ── Programador de noches ─────────────────────────────────────

    /** Cuántas semanas por delante mira el programador. */
    static final int SEMANAS_A_PROGRAMAR = 5;

    private static final Set<DayOfWeek> DIAS_DE_APERTURA =
            EnumSet.of(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

    private static final DateTimeFormatter DIA_DE_SEMANA =
            DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("es-PE"));

    /** Una noche ya celebrada, con lo que dio de sí. */
    record Celebrada(Long id, String titulo, LocalDate noche, int asistentes, long taquilla, long barra) {}

    /**
     * Lo que necesita el programador para proponer noches: qué fechas quedan
     * libres, qué ha funcionado cada día de la semana, qué géneros y artistas
     * llenan, qué entradas se suelen poner y qué fechas especiales vienen.
     *
     * <p>Las fechas libres se le dan hechas, como lista cerrada: si se le deja
     * calcularlas, propone un sábado que ya tiene noche o un martes cualquiera.
     */
    public Optional<String> programacion() {
        record SalaPrincipal(String nombre, int aforo) {}
        Optional<SalaPrincipal> sala = sql.sql("select nombre, aforo from \"Sala\" order by aforo desc limit 1")
                .query((rs, n) -> new SalaPrincipal(rs.getString(1), rs.getInt(2)))
                .optional();
        if (sala.isEmpty()) return Optional.empty();
        int aforo = sala.get().aforo();

        LocalDate hoy = LocalDate.now(Club.ZONA);
        LocalDate hasta = hoy.plusWeeks(SEMANAS_A_PROGRAMAR);
        List<String> lineas = new ArrayList<>();

        lineas.add("Sala: " + sala.get().nombre() + ", aforo " + aforo + " personas. Abre de jueves a sábado "
                + "y vísperas de feriado; puertas a las 21:00 y cierre entre las 02:00 y las 04:00.");
        lineas.add("Edad mínima de la casa: " + Club.EDAD_MINIMA + " años.");
        lineas.add("");

        // Lo ya programado (sin contar lo cancelado): esas fechas no están libres.
        Map<LocalDate, String> ocupadas = new TreeMap<>();
        sql.sql("""
                select titulo, "empiezaEn", estado::text as estado from "Evento"
                where estado <> 'CANCELADO' and "empiezaEn" >= :desde and "empiezaEn" < :hasta
                """)
                .param("desde", LocalDateTime.ofInstant(hoy.atStartOfDay(Club.ZONA).toInstant(), ZoneOffset.UTC))
                .param("hasta", LocalDateTime.ofInstant(hasta.plusDays(2).atStartOfDay(Club.ZONA).toInstant(), ZoneOffset.UTC))
                .query((rs, n) -> Map.entry(Club.nocheDe(instante(rs, "empiezaEn")),
                        rs.getString("titulo") + " (" + rs.getString("estado").toLowerCase(Locale.ROOT) + ")"))
                .list()
                .forEach(e -> ocupadas.merge(e.getKey(), e.getValue(), (a, b) -> a + "; " + b));

        List<CalendarioPeru.Fecha> senaladas = CalendarioPeru.entre(hoy, hasta.plusDays(1));
        Map<LocalDate, List<CalendarioPeru.Fecha>> porDia = senaladas.stream()
                .collect(Collectors.groupingBy(CalendarioPeru.Fecha::dia, TreeMap::new, Collectors.toList()));

        lineas.add("Fechas libres en las próximas " + SEMANAS_A_PROGRAMAR + " semanas (elige solo de aquí):");
        int libres = 0;
        for (LocalDate d = hoy.plusDays(1); !d.isAfter(hasta); d = d.plusDays(1)) {
            List<CalendarioPeru.Fecha> manana = porDia.getOrDefault(d.plusDays(1), List.of());
            List<CalendarioPeru.Fecha> deHoy = porDia.getOrDefault(d, List.of());
            boolean vispera = manana.stream().anyMatch(CalendarioPeru.Fecha::feriado);
            boolean fiesta = deHoy.stream().anyMatch(f -> !f.feriado());
            if (!DIAS_DE_APERTURA.contains(d.getDayOfWeek()) && !vispera && !fiesta) continue;
            if (ocupadas.containsKey(d)) continue;

            StringBuilder linea = new StringBuilder("- " + d + " (" + DIA_DE_SEMANA.format(d) + ")");
            if (vispera) {
                linea.append(" · víspera de feriado: ").append(manana.stream()
                        .filter(CalendarioPeru.Fecha::feriado).map(CalendarioPeru.Fecha::nombre)
                        .collect(Collectors.joining(", ")));
            }
            deHoy.forEach(f -> linea.append(" · ").append(f.nombre()).append(f.feriado() ? " (feriado)" : ""));
            lineas.add(linea.toString());
            libres++;
        }
        if (libres == 0) return Optional.empty();

        lineas.add("");
        lineas.add("Ya programado en ese periodo (no propongas nada esos días):");
        if (ocupadas.isEmpty()) lineas.add("- Nada todavía.");
        ocupadas.forEach((d, t) -> lineas.add("- " + d + " (" + DIA_DE_SEMANA.format(d) + "): " + t));

        List<Celebrada> celebradas = celebradas();
        Map<Long, Integer> asistentesPorNoche = celebradas.stream()
                .collect(Collectors.toMap(Celebrada::id, Celebrada::asistentes));

        lineas.add("");
        lineas.add("Cómo funciona cada día de la semana (" + celebradas.size() + " noches celebradas):");
        if (celebradas.isEmpty()) lineas.add("- Sin histórico todavía.");
        celebradas.stream()
                .collect(Collectors.groupingBy(c -> c.noche().getDayOfWeek(), TreeMap::new, Collectors.toList()))
                .forEach((dia, noches) -> {
                    double asistentes = noches.stream().mapToInt(Celebrada::asistentes).average().orElse(0);
                    String mejores = noches.stream()
                            .sorted(Comparator.comparingInt(Celebrada::asistentes).reversed())
                            .limit(2)
                            .map(c -> "«" + c.titulo() + "» (" + c.asistentes() + ")")
                            .collect(Collectors.joining(", "));
                    lineas.add("- " + DIA_DE_SEMANA.format(dia) + ": " + noches(noches.size()) + ", "
                            + Math.round(asistentes) + " asistentes de media (" + Math.round(asistentes * 100 / aforo)
                            + "% del aforo), taquilla media "
                            + Club.soles(Math.round(noches.stream().mapToLong(Celebrada::taquilla).average().orElse(0)))
                            + ", barra media "
                            + Club.soles(Math.round(noches.stream().mapToLong(Celebrada::barra).average().orElse(0)))
                            + ". Las que más llenaron: " + mejores + ".");
                });

        // El cartel de todas las noches, el cabeza primero: da el género de cada noche y la cuenta de cada artista.
        record EnCartel(Long eventoId, String artista, String genero, boolean cabeza) {}
        List<EnCartel> carteles = sql.sql("""
                select ea."eventoId", a.nombre, a.genero, ea."cabezaCartel"
                from "EventoArtista" ea join "Artista" a on a.id = ea."artistaId"
                order by ea."eventoId", ea."cabezaCartel" desc, a.nombre
                """)
                .query((rs, n) -> new EnCartel(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)))
                .list();

        Map<Long, String> generoDeLaNoche = new HashMap<>();
        carteles.stream().filter(c -> c.genero() != null)
                .forEach(c -> generoDeLaNoche.putIfAbsent(c.eventoId(), c.genero()));

        lineas.add("");
        lineas.add("Género del cabeza de cartel y asistencia media:");
        Map<String, List<Integer>> porGenero = new TreeMap<>();
        celebradas.forEach(c -> {
            String genero = generoDeLaNoche.get(c.id());
            if (genero != null) porGenero.computeIfAbsent(genero, g -> new ArrayList<>()).add(c.asistentes());
        });
        if (porGenero.isEmpty()) lineas.add("- Sin datos.");
        porGenero.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, List<Integer>> e) ->
                        e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0)).reversed())
                .forEach(e -> lineas.add("- " + e.getKey() + ": " + noches(e.getValue().size()) + ", "
                        + Math.round(e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0))
                        + " asistentes de media."));

        lineas.add("");
        lineas.add("Artistas del catálogo (usa estos nombres exactos; el primero de cada noche es el cabeza):");
        Map<String, List<EnCartel>> porArtista = carteles.stream()
                .collect(Collectors.groupingBy(EnCartel::artista));
        Map<String, LocalDate> ultimaNoche = new HashMap<>();
        celebradas.forEach(c -> carteles.stream().filter(e -> e.eventoId().equals(c.id()))
                .forEach(e -> ultimaNoche.merge(e.artista(), c.noche(), (a, b) -> a.isAfter(b) ? a : b)));
        sql.sql("select nombre, genero from \"Artista\" order by nombre")
                .query((rs, n) -> Map.entry(rs.getString(1), String.valueOf(rs.getString(2))))
                .list()
                .forEach(a -> {
                    List<Integer> suyas = porArtista.getOrDefault(a.getKey(), List.of()).stream()
                            .map(e -> asistentesPorNoche.get(e.eventoId()))
                            .filter(x -> x != null)
                            .toList();
                    String historia = suyas.isEmpty()
                            ? "todavía no ha tocado en la casa"
                            : noches(suyas.size()) + ", " + Math.round(suyas.stream().mapToInt(Integer::intValue)
                                    .average().orElse(0)) + " asistentes de media, la última el "
                                    + Club.fechaLarga(ultimaNoche.get(a.getKey()));
                    lineas.add("- " + a.getKey() + " (" + a.getValue() + "): " + historia + ".");
                });

        lineas.add("");
        lineas.add("Entradas que se suelen poner (plantillas reales, precios en céntimos):");
        List<String> plantillas = sql.sql("""
                select t.nombre, count(*) as veces,
                       mode() within group (order by t."precioCentimos") as precio,
                       percentile_disc(0.5) within group (order by t.cantidad) as cupo,
                       sum(t.vendidas) as vendidas, sum(t.cantidad) as ofertadas,
                       mode() within group (order by t.descripcion) as descripcion
                from "TipoEntrada" t join "Evento" e on e.id = t."eventoId"
                where e."empiezaEn" < %s
                group by t.nombre order by veces desc limit 12
                """.formatted(AHORA))
                .query((rs, n) -> "- «" + rs.getString("nombre") + "»: puesta " + rs.getInt("veces")
                        + " veces, normalmente a " + rs.getLong("precio") + " céntimos, cupo típico " + rs.getInt("cupo")
                        + ", se vende el " + (rs.getLong("ofertadas") == 0 ? 0
                                : Math.round(rs.getLong("vendidas") * 100.0 / rs.getLong("ofertadas")))
                        + "% de lo puesto" + (rs.getString("descripcion") == null ? "" : " (" + rs.getString("descripcion") + ")")
                        + ".")
                .list();
        if (plantillas.isEmpty()) lineas.add("- Sin histórico.");
        lineas.addAll(plantillas);

        lineas.add("");
        lineas.add("Títulos que ha usado la casa (es el estilo a seguir):");
        lineas.add("- " + celebradas.stream().map(Celebrada::titulo).distinct().limit(15)
                .collect(Collectors.joining("; ")));

        lineas.add("");
        lineas.add("Promociones de la casa que puedes mencionar:");
        Club.PROMOCIONES.forEach(p -> lineas.add("- " + p));

        return Optional.of(String.join("\n", lineas));
    }

    private static String noches(int n) {
        return n == 1 ? "1 noche" : n + " noches";
    }

    /** Las noches ya celebradas con gente dentro, de la más reciente a la más antigua. */
    List<Celebrada> celebradas() {
        return sql.sql("""
                select e.id, e.titulo, e."empiezaEn",
                       (select count(*) from "Acceso" a where a."eventoId" = e.id) as asistentes,
                       (select coalesce(sum(p."totalCentimos"), 0) from "Pedido" p
                         where p."eventoId" = e.id and p.estado = 'PAGADO') as taquilla,
                       (select coalesce(sum(v."totalCentimos"), 0) from "VentaBarra" v
                         where v."eventoId" = e.id) as barra
                from "Evento" e
                where e."empiezaEn" < %s and exists (select 1 from "Acceso" a where a."eventoId" = e.id)
                order by e."empiezaEn" desc
                """.formatted(AHORA))
                .query((rs, n) -> new Celebrada(
                        rs.getObject("id", Long.class),
                        rs.getString("titulo"),
                        Club.nocheDe(instante(rs, "empiezaEn")),
                        rs.getInt("asistentes"),
                        rs.getLong("taquilla"),
                        rs.getLong("barra")))
                .list();
    }

    // ── Utilidades ────────────────────────────────────────────────

    static Instant instante(ResultSet rs, String columna) throws SQLException {
        LocalDateTime t = rs.getObject(columna, LocalDateTime.class);
        return t == null ? null : t.toInstant(ZoneOffset.UTC);
    }
}
