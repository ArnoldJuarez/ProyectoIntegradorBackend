package pe.ssiomama.backend.publico;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que la portada pública enseña además de la agenda: los boxes, el local,
 * los artistas y las noches pasadas.
 *
 * <p>Va en una sola petición porque la portada lo pinta todo a la vez, y cuatro
 * idas y vueltas desde el móvil de alguien con datos se notan.
 *
 * <p>Como el resto de {@code /api/publico}, solo lleva lo que puede leer
 * cualquiera: nada de lo vendido, lo recaudado ni de quién reservó qué.
 */
@RestController
public class PortadaPublicaController {

    private final JdbcClient sql;

    public PortadaPublicaController(JdbcClient sql) {
        this.sql = sql;
    }

    /**
     * Un tipo de box: los iguales se agrupan.
     *
     * <p>Tres boxes de la misma zona, plazas y consumo mínimo son el mismo
     * producto, y en la portada se venden como uno con «3 libres».
     */
    public record TipoDeBox(String zona, int plazas, int consumoMinimoCentimos, int cuantos) {}

    public record ZonaDeSala(String nombre, int aforo) {}

    public record SalaPublica(String nombre, int aforo, String descripcion, List<ZonaDeSala> zonas) {}

    public record ArtistaPublico(String nombre, String genero, String instagram) {}

    public record NochePasada(String slug, String titulo, Instant empiezaA, String portada) {}

    public record ExtrasDePortada(
            List<TipoDeBox> boxes, List<SalaPublica> salas, List<ArtistaPublico> artistas, List<NochePasada> pasadas) {}

    @GetMapping("/api/publico/portada")
    public ExtrasDePortada portada() {
        List<TipoDeBox> boxes = sql.sql("""
                select z.nombre as zona, b.plazas, b."consumoMinimoCentimos", count(*) as cuantos
                from "Box" b left join "Zona" z on z.id = b."zonaId"
                where b.activo
                group by z.nombre, b.plazas, b."consumoMinimoCentimos"
                order by b."consumoMinimoCentimos" desc
                """)
                .query((rs, n) -> new TipoDeBox(
                        rs.getString("zona"), rs.getInt("plazas"), rs.getInt("consumoMinimoCentimos"), rs.getInt("cuantos")))
                .list();

        Map<Long, SalaPublica> salas = new LinkedHashMap<>();
        sql.sql("""
                select s.id, s.nombre, s.aforo, s.descripcion, z.nombre as zona, z.aforo as "aforoZona"
                from "Sala" s left join "Zona" z on z."salaId" = s.id
                order by s.aforo desc, z.orden
                """)
                .query((rs, n) -> {
                    SalaPublica sala = salas.computeIfAbsent(rs.getObject("id", Long.class), id -> {
                        try {
                            return new SalaPublica(rs.getString("nombre"), rs.getInt("aforo"),
                                    rs.getString("descripcion"), new ArrayList<>());
                        } catch (SQLException e) {
                            throw new IllegalStateException(e);
                        }
                    });
                    if (rs.getString("zona") != null) {
                        sala.zonas().add(new ZonaDeSala(rs.getString("zona"), rs.getInt("aforoZona")));
                    }
                    return sala;
                })
                .list();

        List<ArtistaPublico> artistas = sql.sql("""
                select nombre, genero, instagram from "Artista"
                where genero is not null order by nombre limit 6
                """)
                .query((rs, n) -> new ArtistaPublico(rs.getString("nombre"), rs.getString("genero"), rs.getString("instagram")))
                .list();

        // Un local con historia se nota, y contarlo es gratis.
        List<NochePasada> pasadas = sql.sql("""
                select slug, titulo, "empiezaEn", "imagenPortada" from "Evento"
                where estado in ('PUBLICADO', 'CERRADO') and "empiezaEn" < (now() at time zone 'utc')
                order by "empiezaEn" desc limit 4
                """)
                .query((rs, n) -> new NochePasada(
                        rs.getString("slug"), rs.getString("titulo"), instante(rs, "empiezaEn"), rs.getString("imagenPortada")))
                .list();

        return new ExtrasDePortada(boxes, List.copyOf(salas.values()), artistas, pasadas);
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        LocalDateTime t = rs.getObject(columna, LocalDateTime.class);
        return t == null ? null : t.toInstant(ZoneOffset.UTC);
    }
}
