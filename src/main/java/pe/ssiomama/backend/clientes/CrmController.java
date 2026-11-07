package pe.ssiomama.backend.clientes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Segmentos y campañas del CRM.
 *
 * <p>Va bajo {@code /api/clientes}, el mismo prefijo que ya sirve las fichas:
 * es la misma sección y la misma gente. Segmentar es mirar a los clientes desde
 * arriba en vez de de uno en uno.
 *
 * <p>Por eso hereda su permiso sin añadir regla nueva — dirección y gerencia y
 * nadie más. Son datos personales de gente real, y un segmento es precisamente
 * la lista de a quién se le puede escribir.
 */
@RestController
@RequestMapping("/api/clientes")
public class CrmController {

    private final Crm crm;

    public CrmController(Crm crm) {
        this.crm = crm;
    }

    // ── Lo que entra ──────────────────────────────────────────────

    public record FormularioDeCampania(
            @NotBlank(message = "Falta el nombre de la campaña")
            @Size(max = 120, message = "Ese nombre es demasiado largo") String nombre,
            @NotNull(message = "Elige un canal") CanalDeCampania canal,
            @NotNull(message = "Elige un segmento") Long segmentoId,
            @Size(max = 200, message = "El asunto es demasiado largo") String asunto,
            @NotBlank(message = "La campaña no puede ir vacía")
            @Size(max = 4000, message = "El texto es demasiado largo") String cuerpo) {}

    // ── Lo que sale ───────────────────────────────────────────────

    /**
     * Un segmento con su gente de ahora mismo.
     *
     * @param regla la condición en palabras. Se genera al vuelo, así que cambiar
     *     el umbral cambia también el texto.
     * @param contactables cuántos de ellos dieron permiso para escribirles. No
     *     es lo mismo que el total, y enseñar solo el total promete un alcance
     *     que no existe.
     */
    public record SegmentoEnLista(
            Long id,
            String nombre,
            String descripcion,
            String regla,
            boolean reglaReconocida,
            int miembros,
            long contactables,
            long valorCentimos,
            Instant actualizadoEn) {

        static SegmentoEnLista de(Crm.SegmentoEvaluado e) {
            return new SegmentoEnLista(
                    e.segmento().getId(),
                    e.segmento().getNombre(),
                    e.segmento().getDescripcion(),
                    e.regla().map(ReglaDeSegmento::enPalabras).orElse("Regla no reconocida"),
                    e.regla().isPresent(),
                    e.miembros().size(),
                    e.contactables(),
                    e.valorCentimos(),
                    e.segmento().getActualizadoEn());
        }
    }

    /**
     * Un miembro del segmento.
     *
     * <p>Lleva el correo porque el uso de esta pantalla es exportar la lista
     * para escribirle. Es información personal y por eso vive detrás del permiso
     * del panel, como el resto de la sección de clientes.
     */
    public record MiembroDeSegmento(
            Long id,
            String nombre,
            String email,
            boolean aceptaMarketing,
            int visitas,
            int gastadoCentimos,
            Instant ultimaVisita) {

        static MiembroDeSegmento de(Cliente c) {
            return new MiembroDeSegmento(
                    c.getId(),
                    c.getNombreCompleto(),
                    c.getEmail(),
                    c.isAceptaMarketing(),
                    c.getVisitas(),
                    c.getGastadoCentimos(),
                    c.getUltimaVisita());
        }
    }

    public record FichaDeSegmento(SegmentoEnLista segmento, List<MiembroDeSegmento> miembros) {}

    public record CampaniaEnLista(
            Long id,
            String nombre,
            CanalDeCampania canal,
            String canalEtiqueta,
            String segmento,
            Long segmentoId,
            String asunto,
            String cuerpo,
            EstadoDeCampania estado,
            String estadoEtiqueta,
            boolean sePuedeTocar,
            int enviados,
            Instant creadoEn) {

        static CampaniaEnLista de(Campania c) {
            return new CampaniaEnLista(
                    c.getId(),
                    c.getNombre(),
                    c.getCanal(),
                    c.getCanal().etiqueta(),
                    c.getSegmento().getNombre(),
                    c.getSegmento().getId(),
                    c.getAsunto(),
                    c.getCuerpo(),
                    c.getEstado(),
                    c.getEstado().etiqueta(),
                    c.sePuedeTocar(),
                    c.getEnviados(),
                    c.getCreadoEn());
        }
    }

    // ── Endpoints ─────────────────────────────────────────────────

    /**
     * Los segmentos, recalculados en esta misma petición.
     *
     * <p>No hay «actualizar segmentos» en ninguna parte y es a propósito: no
     * existe nada que actualizar, porque la lista se deduce de la regla cada vez
     * que se pregunta.
     */
    @GetMapping("/segmentos")
    @Transactional(readOnly = true)
    public List<SegmentoEnLista> segmentos() {
        return crm.evaluarTodos(Instant.now()).stream().map(SegmentoEnLista::de).toList();
    }

    /** Un segmento con la lista de quién cae dentro ahora mismo. */
    @GetMapping("/segmentos/{id}")
    @Transactional(readOnly = true)
    public FichaDeSegmento segmento(@PathVariable Long id) {
        var evaluado = crm.evaluar(id, Instant.now())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ese segmento no existe."));

        return new FichaDeSegmento(
                SegmentoEnLista.de(evaluado),
                evaluado.miembros().stream().map(MiembroDeSegmento::de).toList());
    }

    @GetMapping("/campanias")
    @Transactional(readOnly = true)
    public List<CampaniaEnLista> campanias() {
        return crm.listarCampanias().stream().map(CampaniaEnLista::de).toList();
    }

    /**
     * Redacta una campaña.
     *
     * <p>Queda en borrador. <b>El sistema no la manda</b>: no hay proveedor de
     * correo ni de SMS conectado, igual que en el sistema anterior.
     */
    @PostMapping("/campanias")
    @Transactional
    public ResponseEntity<CampaniaEnLista> redactar(
            @Valid @RequestBody FormularioDeCampania formulario) {

        var campania = crm.redactar(
                formulario.nombre(),
                formulario.canal(),
                formulario.segmentoId(),
                formulario.asunto(),
                formulario.cuerpo());

        return ResponseEntity.status(HttpStatus.CREATED).body(CampaniaEnLista.de(campania));
    }

    @PutMapping("/campanias/{id}")
    @Transactional
    public CampaniaEnLista reescribir(
            @PathVariable Long id, @Valid @RequestBody FormularioDeCampania formulario) {

        return CampaniaEnLista.de(crm.reescribir(
                id,
                formulario.nombre(),
                formulario.canal(),
                formulario.asunto(),
                formulario.cuerpo()));
    }

    /** Descarta una campaña. Se marca como cancelada, no se borra. */
    @DeleteMapping("/campanias/{id}")
    @Transactional
    public CampaniaEnLista cancelar(@PathVariable Long id) {
        return CampaniaEnLista.de(crm.cancelar(id));
    }
}
