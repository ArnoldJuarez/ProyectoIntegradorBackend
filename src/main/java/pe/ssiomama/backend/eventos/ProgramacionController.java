package pe.ssiomama.backend.eventos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

/**
 * Programar noches: crearlas, editarlas y poner sus entradas a la venta.
 *
 * <p>Va bajo {@code /api/eventos}, el mismo prefijo que ya sirve el listado del
 * panel, porque es la misma sección vista por la misma gente. Lo que cambia es
 * el verbo: {@code GET} lee, y estos escriben.
 *
 * <h2>Lo que se decidió no migrar todavía</h2>
 *
 * <p>La <b>subida de la imagen de portada</b>. En el sistema anterior era un
 * formulario multiparte que optimizaba la imagen y la escribía en disco, y eso
 * es un problema aparte —almacenamiento de archivos— que no tiene nada que ver
 * con programar una noche. Aquí la portada es una dirección de imagen, que es lo
 * que era antes de aquella pantalla y sigue funcionando igual de cara al
 * público.
 */
@RestController
@RequestMapping("/api/eventos")
public class ProgramacionController {

    private final GestionDeEventos gestion;
    private final SalaRepository salas;
    private final ArtistaRepository artistas;

    public ProgramacionController(
            GestionDeEventos gestion, SalaRepository salas, ArtistaRepository artistas) {
        this.gestion = gestion;
        this.salas = salas;
        this.artistas = artistas;
    }

    // ── Lo que entra ──────────────────────────────────────────────

    /**
     * El formulario de una noche.
     *
     * <p>Las fechas llegan como instantes ISO en UTC, no como texto de hora
     * local: el navegador ya sabe en qué zona está la sala y convierte antes de
     * enviar. Así el servidor no tiene que adivinar zonas horarias, que es de
     * donde salen las noches que empiezan cinco horas antes.
     */
    public record FormularioDeNoche(
            @NotBlank(message = "Falta el título")
            @Size(min = 3, max = 160, message = "El título tiene entre 3 y 160 caracteres")
            String titulo,
            @Size(max = 2000, message = "La descripción es demasiado larga") String descripcion,
            @NotBlank(message = "Elige una sala") String salaId,
            @NotNull(message = "Falta la hora de apertura") Instant puertasA,
            @NotNull(message = "Falta la hora de cierre") Instant terminaA,
            @Min(value = 0, message = "La edad mínima no puede ser negativa")
            @Max(value = 99, message = "Esa edad mínima no parece real")
            int edadMinima,
            @Size(max = 120, message = "El código de vestimenta es demasiado largo")
            String vestimenta,
            @NotNull(message = "Falta el estado") EstadoDeEvento estado,
            @Size(max = 500) String portada,
            @Size(max = 10, message = "Como mucho diez artistas en el cartel")
            List<String> artistaIds) {}

    public record FormularioDeTipo(
            @NotBlank(message = "Falta el nombre")
            @Size(min = 2, max = 60, message = "El nombre tiene entre 2 y 60 caracteres")
            String nombre,
            @Size(max = 300, message = "La descripción es demasiado larga") String descripcion,
            @Min(value = 0, message = "El precio no puede ser negativo")
            @Max(value = 1_000_000, message = "Ese precio no parece real")
            int precioCentimos,
            @Min(value = 1, message = "Tiene que haber al menos una entrada")
            @Max(value = 100_000, message = "Ese cupo no parece real")
            int cantidad,
            @Min(value = 1, message = "El máximo por pedido es de una entrada o más")
            @Max(value = 20, message = "Veinte entradas por pedido es el tope")
            int maximoPorPedido,
            @Min(value = 0) @Max(value = 10) int consumicionesIncluidas,
            boolean activo) {}

    // ── Lo que sale ───────────────────────────────────────────────

    /** Lo mínimo para volver a la ficha después de guardar. */
    public record NocheGuardada(String id, String slug, String titulo) {

        static NocheGuardada de(Evento e) {
            return new NocheGuardada(e.getId(), e.getSlug(), e.getTitulo());
        }
    }

    public record TipoGuardado(String id, String nombre, int cantidad, boolean activo) {

        static TipoGuardado de(TipoDeEntrada t) {
            return new TipoGuardado(t.getId(), t.getNombre(), t.getCantidad(), t.isActivo());
        }
    }

    /**
     * El resultado de quitar un tipo.
     *
     * @param retirado cierto si tenía ventas y se retiró en vez de borrarse. La
     *     pantalla tiene que decirlo, o parecerá que el botón no funcionó.
     */
    public record TipoQuitado(boolean retirado, String mensaje) {}

    /** Las salas y los artistas que hay para elegir en el formulario. */
    public record SalaParaElegir(String id, String nombre, int aforo) {}

    public record ArtistaParaElegir(String id, String nombre, String genero) {}

    public record OpcionesDeProgramacion(
            List<SalaParaElegir> salas, List<ArtistaParaElegir> artistas) {}

    // ── Endpoints ─────────────────────────────────────────────────

    /**
     * Lo que hace falta para rellenar el formulario.
     *
     * <p>El aforo de cada sala viaja con ella porque el formulario avisa antes
     * de enviar si el cupo se pasa. Es solo una ayuda: quien decide de verdad es
     * el servidor.
     */
    @GetMapping("/opciones")
    @Transactional(readOnly = true)
    public OpcionesDeProgramacion opciones() {
        return new OpcionesDeProgramacion(
                salas.findAllByOrderByAforoDesc().stream()
                        .map(s -> new SalaParaElegir(s.getId(), s.getNombre(), s.getAforo()))
                        .toList(),
                artistas.findAllByOrderByNombreAsc().stream()
                        .map(a -> new ArtistaParaElegir(a.getId(), a.getNombre(), a.getGenero()))
                        .toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<NocheGuardada> crear(@Valid @RequestBody FormularioDeNoche formulario) {
        var noche = gestion.crear(aDatos(formulario));
        return ResponseEntity.status(HttpStatus.CREATED).body(NocheGuardada.de(noche));
    }

    /**
     * Cambia una noche.
     *
     * <p>{@code PUT} y no {@code PATCH} porque el formulario manda la noche
     * entera: lo que llega es el estado completo que debe quedar, no un puñado
     * de campos sueltos. Con PATCH habría que distinguir «no lo mandó» de «lo
     * quiere vacío», y eso es exactamente lo que hace desaparecer descripciones
     * sin que nadie sepa por qué.
     */
    @PutMapping("/{slug}")
    @Transactional
    public NocheGuardada editar(
            @PathVariable String slug, @Valid @RequestBody FormularioDeNoche formulario) {
        return NocheGuardada.de(gestion.editar(slug, aDatos(formulario)));
    }

    @PostMapping("/{slug}/entradas")
    @Transactional
    public ResponseEntity<TipoGuardado> anadirTipo(
            @PathVariable String slug, @Valid @RequestBody FormularioDeTipo formulario) {

        var tipo = gestion.anadirTipo(slug, aDatos(formulario));
        return ResponseEntity.status(HttpStatus.CREATED).body(TipoGuardado.de(tipo));
    }

    @PutMapping("/entradas/{tipoId}")
    @Transactional
    public TipoGuardado editarTipo(
            @PathVariable String tipoId, @Valid @RequestBody FormularioDeTipo formulario) {
        return TipoGuardado.de(gestion.editarTipo(tipoId, aDatos(formulario)));
    }

    @DeleteMapping("/entradas/{tipoId}")
    @Transactional
    public TipoQuitado quitarTipo(@PathVariable String tipoId) {
        boolean retirado = gestion.quitarTipo(tipoId);

        return new TipoQuitado(
                retirado,
                retirado
                        ? "Tiene entradas vendidas, así que se ha retirado de la venta en vez de borrarse."
                        : "Tipo de entrada eliminado.");
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    private GestionDeEventos.DatosDeNoche aDatos(FormularioDeNoche f) {
        return new GestionDeEventos.DatosDeNoche(
                f.titulo(),
                f.descripcion(),
                f.salaId(),
                f.puertasA(),
                f.terminaA(),
                f.edadMinima(),
                f.vestimenta(),
                f.estado(),
                f.portada(),
                f.artistaIds() == null ? List.of() : f.artistaIds());
    }

    private GestionDeEventos.DatosDeTipo aDatos(FormularioDeTipo f) {
        return new GestionDeEventos.DatosDeTipo(
                f.nombre(),
                f.descripcion(),
                f.precioCentimos(),
                f.cantidad(),
                f.maximoPorPedido(),
                f.consumicionesIncluidas(),
                f.activo());
    }
}
