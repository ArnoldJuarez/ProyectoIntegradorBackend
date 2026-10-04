package pe.ssiomama.backend.panel;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las últimas noches celebradas, para las gráficas del resumen.
 *
 * <p>Cuánta gente entró y cuánto dejó cada noche en taquilla y en barra. Se
 * cuenta a quien pasó por la puerta (un acceso escaneado), no a quien compró:
 * una entrada vendida a alguien que no vino no ocupa la pista ni bebe.
 *
 * <p>Es una agregación que cruza pedidos, accesos y barra, y en SQL se lee de un
 * vistazo. Cuelga de {@code /api/panel}, así que la ven dirección y gerencia.
 */
@RestController
public class HistorialController {

    private static final int MAXIMO = 40;

    private final JdbcClient sql;

    public HistorialController(JdbcClient sql) {
        this.sql = sql;
    }

    public record NocheCelebrada(
            String slug,
            String titulo,
            Instant empiezaA,
            long asistentes,
            long taquillaCentimos,
            long barraCentimos) {}

    @GetMapping("/api/panel/historial")
    public List<NocheCelebrada> historial(@RequestParam(defaultValue = "12") int noches) {
        List<NocheCelebrada> ultimas = sql.sql("""
                select e.slug, e.titulo, e."empiezaEn",
                       (select count(*) from "Acceso" a where a."eventoId" = e.id) as asistentes,
                       (select coalesce(sum(p."totalCentimos"), 0) from "Pedido" p
                         where p."eventoId" = e.id and p.estado = 'PAGADO') as taquilla,
                       (select coalesce(sum(v."totalCentimos"), 0) from "VentaBarra" v
                         where v."eventoId" = e.id) as barra
                from "Evento" e
                where e."empiezaEn" < (now() at time zone 'utc')
                  and e.estado <> 'CANCELADO'
                  and exists (select 1 from "Acceso" a where a."eventoId" = e.id)
                order by e."empiezaEn" desc
                limit :noches
                """)
                .param("noches", Math.clamp(noches, 1, MAXIMO))
                .query((rs, n) -> new NocheCelebrada(
                        rs.getString("slug"),
                        rs.getString("titulo"),
                        rs.getObject("empiezaEn", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getLong("asistentes"),
                        rs.getLong("taquilla"),
                        rs.getLong("barra")))
                .list();
        // De la más vieja a la más reciente: una gráfica temporal se lee de izquierda a derecha.
        return ultimas.reversed();
    }
}
