package pe.ssiomama.backend.ia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lo que la capa de IA escribe y lee: ejecuciones, propuestas y el chat.
 *
 * <h2>Por qué SQL a mano y no entidades JPA</h2>
 *
 * <p>Tres de los enum de estas tablas tienen etiquetas con guiones o en inglés
 * ({@code crm-segmenter}, {@code benchmark}, {@code assistant}) que no caben en
 * un enum de Java. Hibernate manda esos valores como texto, y PostgreSQL no
 * convierte texto a un enum propio en un parámetro: la inserción falla. Con SQL
 * a mano el {@code CAST} va escrito donde hace falta y se ve.
 *
 * <h2>Sin transacción, a propósito</h2>
 *
 * <p>Cada escritura se confirma sola. Si la llamada al modelo falla a mitad, la
 * ejecución tiene que quedar registrada como fallida: dentro de una transacción
 * el fallo se llevaría por delante también el registro de que se intentó.
 *
 * <p>Las fechas se escriben con {@code now() at time zone 'utc'}: las columnas
 * son {@code timestamp without time zone} y guardan el instante en UTC, igual
 * que el resto del esquema.
 */
@Repository
public class RegistroDeIa {

    private static final String AHORA = "(now() at time zone 'utc')";

    private final JdbcClient sql;
    private final ObjectMapper json;

    public RegistroDeIa(JdbcClient sql, ObjectMapper json) {
        this.sql = sql;
        this.json = json;
    }

    // ── Ejecuciones ───────────────────────────────────────────────

    public Long abrirEjecucion(
            ClaveDeAgente clave, Disparador disparador, Long lanzadoPorId, Object entrada, String modelo) {
        return sql.sql("""
                insert into "EjecucionAgente"
                  ("claveAgente", disparador, "lanzadoPorId", entrada, estado, modelo, "iniciadaEn")
                values (cast(:clave as "ClaveAgente"), cast(:disparador as "DisparadorAgente"),
                        :lanzadoPor, cast(:entrada as jsonb), 'EJECUTANDO', :modelo, %s)
                returning id
                """.formatted(AHORA))
                .param("clave", clave.etiqueta())
                .param("disparador", disparador.etiqueta())
                .param("lanzadoPor", lanzadoPorId)
                .param("entrada", aJson(entrada))
                .param("modelo", modelo)
                .query(Long.class)
                .single();
    }

    public void completarEjecucion(
            Long id, Object salida, int tokensEntrada, int tokensSalida, int costeCentimos, int intentos) {
        sql.sql("""
                update "EjecucionAgente"
                set estado = 'COMPLETADA', salida = cast(:salida as jsonb),
                    "tokensEntrada" = :te, "tokensSalida" = :ts, "costeCentimos" = :coste,
                    intentos = :intentos, "terminadaEn" = %s
                where id = :id
                """.formatted(AHORA))
                .param("id", id)
                .param("salida", aJson(salida))
                .param("te", tokensEntrada)
                .param("ts", tokensSalida)
                .param("coste", costeCentimos)
                .param("intentos", intentos)
                .update();
    }

    public void fallarEjecucion(Long id, String error) {
        sql.sql("""
                update "EjecucionAgente"
                set estado = 'FALLIDA', error = :error, "terminadaEn" = %s
                where id = :id
                """.formatted(AHORA))
                .param("id", id)
                .param("error", error == null ? "Error desconocido" : error)
                .update();
    }

    // ── Propuestas ────────────────────────────────────────────────

    public Long proponer(Long ejecucionId, TipoDePropuesta tipo, JsonNode datos) {
        return sql.sql("""
                insert into "PropuestaAgente" ("ejecucionId", tipo, datos, estado, "creadoEn")
                values (:ejecucion, cast(:tipo as "TipoPropuesta"), cast(:datos as jsonb), 'PENDIENTE', %s)
                returning id
                """.formatted(AHORA))
                .param("ejecucion", ejecucionId)
                .param("tipo", tipo.name())
                .param("datos", aJson(datos))
                .query(Long.class)
                .single();
    }

    /** Una propuesta tal como se pinta en la bandeja. */
    public record Propuesta(
            Long id,
            Long ejecucionId,
            TipoDePropuesta tipo,
            String agente,
            String estado,
            JsonNode datos,
            Instant creadoEn,
            String revisadaPor,
            Instant revisadaEn,
            Instant aplicadaEn,
            int costeCentimos,
            int tokens) {}

    private static final String SELECT_PROPUESTA = """
            select p.id, p."ejecucionId", p.tipo::text as tipo, e."claveAgente"::text as agente,
                   p.estado::text as estado, p.datos::text as datos, p."creadoEn", u.nombre as revisor,
                   p."revisadaEn", p."aplicadaEn", e."costeCentimos",
                   e."tokensEntrada" + e."tokensSalida" as tokens
            from "PropuestaAgente" p
            join "EjecucionAgente" e on e.id = p."ejecucionId"
            left join "Usuario" u on u.id = p."revisadaPorId"
            """;

    /**
     * Las propuestas de la operación real: primero las pendientes, luego por fecha.
     *
     * <p>Las del banco de pruebas no salen: son laboratorio, y en la bandeja
     * enterrarían a las de verdad.
     */
    public List<Propuesta> propuestas(int limite) {
        return sql.sql(SELECT_PROPUESTA + """
                where e.disparador <> 'benchmark'
                order by (p.estado = 'PENDIENTE') desc, p."creadoEn" desc
                limit :limite
                """)
                .param("limite", limite)
                .query((rs, n) -> propuesta(rs, n))
                .list();
    }

    public Optional<Propuesta> propuesta(Long id) {
        return sql.sql(SELECT_PROPUESTA + " where p.id = :id")
                .param("id", id)
                .query((rs, n) -> propuesta(rs, n))
                .optional();
    }

    /**
     * Marca una propuesta como revisada, solo si seguía pendiente.
     *
     * @return {@code false} si alguien la revisó antes: dos personas pulsando
     *     «Aprobar» a la vez no deben aplicarla dos veces
     */
    public boolean revisar(Long id, String estado, Long revisorId) {
        return sql.sql("""
                update "PropuestaAgente"
                set estado = cast(:estado as "EstadoPropuesta"), "revisadaPorId" = :revisor, "revisadaEn" = %s
                where id = :id and estado = 'PENDIENTE'
                """.formatted(AHORA))
                .param("id", id)
                .param("estado", estado)
                .param("revisor", revisorId)
                .update() == 1;
    }

    /**
     * Sustituye lo propuesto por lo que la persona dejó tras retocarlo.
     *
     * <p>Lo que propuso el modelo no se pierde: sigue tal cual en la salida de su
     * ejecución ({@code EjecucionAgente.salida}). Así se puede comparar lo que
     * dijo la IA con lo que se aprobó de verdad.
     */
    public void reemplazarDatos(Long id, JsonNode datos) {
        sql.sql("update \"PropuestaAgente\" set datos = cast(:datos as jsonb) where id = :id")
                .param("id", id)
                .param("datos", aJson(datos))
                .update();
    }

    public void marcarAplicada(Long id) {
        sql.sql("update \"PropuestaAgente\" set \"aplicadaEn\" = %s where id = :id".formatted(AHORA))
                .param("id", id)
                .update();
    }

    private Propuesta propuesta(ResultSet rs, int fila) throws SQLException {
        return new Propuesta(
                rs.getObject("id", Long.class),
                rs.getObject("ejecucionId", Long.class),
                TipoDePropuesta.valueOf(rs.getString("tipo")),
                rs.getString("agente"),
                rs.getString("estado"),
                deJson(rs.getString("datos")),
                instante(rs, "creadoEn"),
                rs.getString("revisor"),
                instante(rs, "revisadaEn"),
                instante(rs, "aplicadaEn"),
                rs.getInt("costeCentimos"),
                rs.getInt("tokens"));
    }

    // ── Métricas ──────────────────────────────────────────────────

    /** Una ejecución, con lo justo para agregarla. */
    public record Ejecucion(
            Long id,
            String agente,
            String disparador,
            String modelo,
            String estado,
            int intentos,
            int tokensEntrada,
            int tokensSalida,
            int costeCentimos,
            Instant iniciadaEn,
            Instant terminadaEn,
            String lanzadaPor,
            int propuestas) {}

    public List<Ejecucion> ejecuciones() {
        return sql.sql("""
                select e.id, e."claveAgente"::text as agente, e.disparador::text as disparador, e.modelo,
                       e.estado::text as estado, e.intentos, e."tokensEntrada", e."tokensSalida",
                       e."costeCentimos", e."iniciadaEn", e."terminadaEn", u.nombre as lanzador,
                       (select count(*) from "PropuestaAgente" p where p."ejecucionId" = e.id) as propuestas
                from "EjecucionAgente" e
                left join "Usuario" u on u.id = e."lanzadoPorId"
                order by e."iniciadaEn" desc
                """)
                .query((rs, n) -> new Ejecucion(
                        rs.getObject("id", Long.class),
                        rs.getString("agente"),
                        rs.getString("disparador"),
                        rs.getString("modelo"),
                        rs.getString("estado"),
                        rs.getInt("intentos"),
                        rs.getInt("tokensEntrada"),
                        rs.getInt("tokensSalida"),
                        rs.getInt("costeCentimos"),
                        instante(rs, "iniciadaEn"),
                        instante(rs, "terminadaEn"),
                        rs.getString("lanzador"),
                        rs.getInt("propuestas")))
                .list();
    }

    // ── Chat ──────────────────────────────────────────────────────

    /** La sesión de chat, si existe; si no, una nueva. */
    public Long sesionDeChat(Long sesionId) {
        if (sesionId != null) {
            boolean existe = sql.sql("select count(*) from \"SesionChat\" where id = :id")
                    .param("id", sesionId)
                    .query(Long.class)
                    .single() > 0;
            if (existe) return sesionId;
        }
        return sql.sql("""
                insert into "SesionChat" (canal, "iniciadaEn", "ultimoMensajeEn")
                values ('WEB', %s, %s)
                returning id
                """.formatted(AHORA, AHORA))
                .query(Long.class)
                .single();
    }

    /** @param rol {@code user} o {@code assistant}, como en la base */
    public void guardarMensaje(Long sesionId, String rol, String contenido, Long ejecucionId) {
        sql.sql("""
                insert into "MensajeChat" ("sesionId", rol, contenido, "ejecucionId", "creadoEn")
                values (:sesion, cast(:rol as "RolChat"), :contenido, :ejecucion, %s)
                """.formatted(AHORA))
                .param("sesion", sesionId)
                .param("rol", rol)
                .param("contenido", contenido)
                .param("ejecucion", ejecucionId)
                .update();
        sql.sql("update \"SesionChat\" set \"ultimoMensajeEn\" = %s where id = :id".formatted(AHORA))
                .param("id", sesionId)
                .update();
    }

    // ── Utilidades ────────────────────────────────────────────────

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        LocalDateTime t = rs.getObject(columna, LocalDateTime.class);
        return t == null ? null : t.toInstant(ZoneOffset.UTC);
    }

    private String aJson(Object valor) {
        if (valor == null) return null;
        try {
            return json.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo convertir a JSON", e);
        }
    }

    private JsonNode deJson(String texto) {
        try {
            return texto == null ? null : json.readTree(texto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON guardado ilegible", e);
        }
    }
}
