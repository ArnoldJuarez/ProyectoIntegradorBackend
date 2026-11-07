package pe.ssiomama.backend.clientes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Segmentos y campañas: a quién se le escribe y qué se le escribe.
 *
 * <h2>Los segmentos se calculan, no se guardan</h2>
 *
 * <p>Cada vez que alguien abre la pantalla se vuelve a evaluar la regla de cada
 * segmento contra los clientes. Suena caro y no lo es: son decenas de clientes y
 * cinco reglas. Lo que compra es que un segmento nunca está desactualizado —
 * quien volvió ayer ya no aparece en «dormidos», sin que nadie recalcule nada.
 *
 * <h2>Lo que este servicio no hace</h2>
 *
 * <p><b>No manda mensajes.</b> No hay proveedor de correo ni de SMS conectado,
 * igual que en el sistema anterior. Una campaña se redacta, se guarda en
 * borrador y ahí se queda; quien la envíe de verdad lo hará por su cuenta.
 * Fingir un envío sería peor que no tenerlo: el estado {@code ENVIADO} dejaría
 * de significar que alguien recibió algo.
 */
@Service
public class Crm {

    private static final Logger log = LoggerFactory.getLogger(Crm.class);

    private final SegmentoRepository segmentos;
    private final CampaniaRepository campanias;
    private final ClienteRepository clientes;
    private final ObjectMapper json;

    public Crm(
            SegmentoRepository segmentos,
            CampaniaRepository campanias,
            ClienteRepository clientes,
            ObjectMapper json) {
        this.segmentos = segmentos;
        this.campanias = campanias;
        this.clientes = clientes;
        this.json = json;
    }

    // ── Lo que sale ───────────────────────────────────────────────

    /**
     * Un segmento ya evaluado.
     *
     * @param regla la condición interpretada, o vacío si el JSON guardado no se
     *     entiende
     * @param miembros los clientes que cumplen la condición ahora mismo
     */
    public record SegmentoEvaluado(
            Segmento segmento, Optional<ReglaDeSegmento> regla, List<Cliente> miembros) {

        /** Cuánto ha gastado en total la gente de este segmento. */
        public long valorCentimos() {
            return miembros.stream().mapToLong(Cliente::getGastadoCentimos).sum();
        }

        /**
         * Cuántos de ellos se pueden contactar.
         *
         * <p>No es lo mismo que el tamaño del segmento: «VIP» incluye a quien
         * gastó mucho y nunca dio permiso para escribirle. Enseñar solo el total
         * hace prometer un alcance que no existe.
         */
        public long contactables() {
            return miembros.stream().filter(Cliente::isAceptaMarketing).count();
        }
    }

    // ── Segmentos ─────────────────────────────────────────────────

    /** Todos los segmentos, cada uno con su gente de ahora mismo. */
    @Transactional(readOnly = true)
    public List<SegmentoEvaluado> evaluarTodos(Instant ahora) {
        List<Cliente> todos = clientes.findAll();

        return segmentos.findAllByOrderByNombreAsc().stream()
                .map(segmento -> evaluar(segmento, todos, ahora))
                .toList();
    }

    /** Un segmento concreto con su gente. */
    @Transactional(readOnly = true)
    public Optional<SegmentoEvaluado> evaluar(Long segmentoId, Instant ahora) {
        return segmentos.findById(segmentoId)
                .map(segmento -> evaluar(segmento, clientes.findAll(), ahora));
    }

    /**
     * Aplica la regla de un segmento a una lista de clientes ya cargada.
     *
     * <p>Los clientes llegan desde fuera para no repetir la misma consulta una
     * vez por segmento: con cinco segmentos serían cinco lecturas de la misma
     * tabla para responder una sola pantalla.
     */
    private SegmentoEvaluado evaluar(Segmento segmento, List<Cliente> todos, Instant ahora) {
        Optional<ReglaDeSegmento> regla = leerRegla(segmento);

        List<Cliente> miembros = regla
                .map(r -> todos.stream().filter(r.comoFiltro(ahora)).toList())
                .orElseGet(List::of);

        return new SegmentoEvaluado(segmento, regla, miembros);
    }

    /**
     * Interpreta el JSON guardado.
     *
     * <p>Una regla que no se entiende no tumba la pantalla: se registra y el
     * segmento sale con cero miembros y un aviso. Es la diferencia entre «hay un
     * segmento mal configurado» y «el CRM no abre».
     */
    private Optional<ReglaDeSegmento> leerRegla(Segmento segmento) {
        try {
            ReglaDeSegmento regla = json.readValue(segmento.getReglasJson(), ReglaDeSegmento.class);
            return regla.esConocida() ? Optional.of(regla) : Optional.empty();
        } catch (JsonProcessingException mal) {
            log.warn(
                    "El segmento «{}» tiene una regla que no se puede leer: {}",
                    segmento.getNombre(),
                    mal.getOriginalMessage());
            return Optional.empty();
        }
    }

    // ── Campañas ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Campania> listarCampanias() {
        return campanias.buscarRecientes();
    }

    /**
     * Redacta una campaña dirigida a un segmento.
     *
     * <p>Nace en borrador y no sale de ahí sola. Quien la mande de verdad tendrá
     * que exportarla: aquí solo queda escrita.
     */
    @Transactional
    public Campania redactar(
            String nombre,
            CanalDeCampania canal,
            Long segmentoId,
            String asunto,
            String cuerpo) {

        Segmento segmento = segmentos.findById(segmentoId)
                .orElseThrow(() -> new CrmRechazado("Ese segmento no existe."));

        // Un correo sin asunto llega como «(sin asunto)» y nadie lo abre. Los
        // demás canales no tienen asunto, así que ahí no se pide.
        if (canal == CanalDeCampania.CORREO && (asunto == null || asunto.isBlank())) {
            throw new CrmRechazado("Una campaña por correo necesita un asunto.");
        }

        return campanias.save(
                Campania.redactar(nombre.trim(), canal, segmento, asunto, cuerpo.trim()));
    }

    /** Cambia el texto de una campaña que todavía no ha salido. */
    @Transactional
    public Campania reescribir(
            Long campaniaId, String nombre, CanalDeCampania canal, String asunto, String cuerpo) {

        Campania campania = campanias.buscarConSegmento(campaniaId)
                .orElseThrow(() -> new CrmRechazado("Esa campaña no existe."));

        if (!campania.sePuedeTocar()) {
            throw new CrmRechazado(
                    "Esta campaña está %s y ya no se puede cambiar."
                            .formatted(campania.getEstado().etiqueta().toLowerCase()));
        }
        if (canal == CanalDeCampania.CORREO && (asunto == null || asunto.isBlank())) {
            throw new CrmRechazado("Una campaña por correo necesita un asunto.");
        }

        campania.reescribir(nombre.trim(), canal, asunto, cuerpo.trim());
        return campania;
    }

    /**
     * Descarta una campaña.
     *
     * <p>Se marca como cancelada en vez de borrarla. Queda el rastro de que se
     * pensó en escribir a alguien y se decidió que no, que es justo lo que se
     * pregunta cuando alguien revisa por qué no salió una promoción.
     */
    @Transactional
    public Campania cancelar(Long campaniaId) {
        Campania campania = campanias.buscarConSegmento(campaniaId)
                .orElseThrow(() -> new CrmRechazado("Esa campaña no existe."));

        if (!campania.sePuedeTocar()) {
            throw new CrmRechazado(
                    "Esta campaña está %s y ya no se puede cancelar."
                            .formatted(campania.getEstado().etiqueta().toLowerCase()));
        }

        campania.cancelar();
        return campania;
    }
}
