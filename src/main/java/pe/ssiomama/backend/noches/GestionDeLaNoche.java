package pe.ssiomama.backend.noches;

import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.eventos.Evento;
import pe.ssiomama.backend.eventos.EventoRepository;

/**
 * Lo que la jefatura de sala apunta de una noche: incidencias y notas de cierre.
 */
@Service
public class GestionDeLaNoche {

    /** Una incidencia se apunta durante la noche o al cerrarla, no una semana antes. */
    private static final Duration MARGEN = Duration.ofHours(12);

    private static final int MAXIMO_DE_TEXTO = 2000;

    private final EventoRepository eventos;
    private final IncidenciaRepository incidencias;
    private final JdbcClient sql;

    public GestionDeLaNoche(EventoRepository eventos, IncidenciaRepository incidencias, JdbcClient sql) {
        this.eventos = eventos;
        this.incidencias = incidencias;
        this.sql = sql;
    }

    @Transactional
    public Incidencia apuntar(String slug, TipoDeIncidencia tipo, String descripcion, Instant ocurrioEn, Usuario quien) {
        Evento noche = eventos.findBySlug(slug).orElseThrow(() -> new NocheRechazada("Esa noche no existe."));

        if (descripcion == null || descripcion.isBlank()) {
            throw new NocheRechazada("Cuenta qué pasó.");
        }
        if (descripcion.length() > MAXIMO_DE_TEXTO) {
            throw new NocheRechazada("La descripción es demasiado larga.");
        }

        Instant cuando = ocurrioEn == null ? Instant.now() : ocurrioEn;
        // La hora tiene que caer dentro de la noche, con margen: si no, es casi
        // seguro un error al elegir la fecha, y una incidencia mal fechada
        // engaña justo a quien la busca para explicar algo.
        if (cuando.isBefore(noche.getPuertasA().minus(MARGEN)) || cuando.isAfter(noche.getTerminaA().plus(MARGEN))) {
            throw new NocheRechazada("Esa hora no cae dentro de la noche.");
        }

        return incidencias.save(Incidencia.apuntar(noche, tipo, descripcion, cuando, quien));
    }

    @Transactional
    public void quitar(Long incidenciaId) {
        Incidencia i = incidencias.findById(incidenciaId)
                .orElseThrow(() -> new NocheRechazada("Esa incidencia no existe."));
        incidencias.delete(i);
    }

    /**
     * Guarda las notas de cierre. Vacío las borra.
     *
     * <p>Va por SQL y no por la entidad {@code Evento} porque es una columna que
     * solo se escribe aquí y solo se lee en el expediente: mapearla en la
     * entidad la cargaría en cada lectura de la cartelera para nada.
     */
    @Transactional
    public void guardarNotas(String slug, String notas) {
        if (notas != null && notas.length() > MAXIMO_DE_TEXTO * 2) {
            throw new NocheRechazada("Las notas son demasiado largas.");
        }
        int filas = sql.sql("update \"Evento\" set \"notasCierre\" = :notas where slug = :slug")
                .param("notas", notas == null || notas.isBlank() ? null : notas.trim())
                .param("slug", slug)
                .update();
        if (filas == 0) {
            throw new NocheRechazada("Esa noche no existe.");
        }
    }
}
