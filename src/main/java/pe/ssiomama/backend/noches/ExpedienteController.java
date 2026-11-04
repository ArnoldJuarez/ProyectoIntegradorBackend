package pe.ssiomama.backend.noches;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.noches.ExpedienteDeNoche.Expediente;
import pe.ssiomama.backend.noches.ExpedienteDeNoche.IncidenciaEnFicha;
import pe.ssiomama.backend.noches.ExpedienteDeNoche.NocheEnHistorico;

/**
 * El expediente de cada noche y el histórico para compararlas.
 *
 * <p>Cuelga de {@code /api/eventos} y {@code /api/panel}: lo ven dirección y
 * jefatura de sala, como el resto del panel. Lleva cifras de caja por persona y
 * nombres de clientes de los reservados.
 */
@RestController
public class ExpedienteController {

    private final ExpedienteDeNoche expedientes;
    private final GestionDeLaNoche gestion;

    public ExpedienteController(ExpedienteDeNoche expedientes, GestionDeLaNoche gestion) {
        this.expedientes = expedientes;
        this.gestion = gestion;
    }

    public record NuevaIncidencia(
            @NotNull(message = "Elige el tipo de incidencia") TipoDeIncidencia tipo,
            @NotBlank(message = "Cuenta qué pasó")
            @Size(max = 2000, message = "La descripción es demasiado larga") String descripcion,
            /** Cuándo pasó. Vacío: ahora. */
            Instant ocurrioEn) {}

    public record Notas(@Size(max = 4000, message = "Las notas son demasiado largas") String notas) {}

    @GetMapping("/api/eventos/{slug}/expediente")
    public Expediente expediente(@PathVariable String slug) {
        return expedientes.de(slug).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Esa noche no existe."));
    }

    /**
     * El mismo expediente en el texto que recibe la IA.
     *
     * <p>Se publica para que cualquiera pueda comprobar qué le llega al modelo:
     * si el informe dice algo raro, lo primero es mirar si el dato venía así.
     */
    @GetMapping(value = "/api/eventos/{slug}/expediente/texto", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String comoTexto(@PathVariable String slug) {
        return TextoDeExpediente.de(expediente(slug));
    }

    @PostMapping("/api/eventos/{slug}/incidencias")
    public ResponseEntity<IncidenciaEnFicha> apuntar(
            @PathVariable String slug,
            @Valid @RequestBody NuevaIncidencia n,
            @AuthenticationPrincipal Usuario quien) {
        Incidencia i = gestion.apuntar(slug, n.tipo(), n.descripcion(), n.ocurrioEn(), quien);
        return ResponseEntity.status(HttpStatus.CREATED).body(new IncidenciaEnFicha(
                i.getId(), i.getTipo().name(), i.getDescripcion(), i.getOcurrioEn(), quien.getNombre()));
    }

    @DeleteMapping("/api/eventos/{slug}/incidencias/{id}")
    public ResponseEntity<Void> quitar(@PathVariable String slug, @PathVariable String id) {
        gestion.quitar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/eventos/{slug}/notas-cierre")
    public ResponseEntity<Void> notas(@PathVariable String slug, @Valid @RequestBody Notas n) {
        gestion.guardarNotas(slug, n.notas());
        return ResponseEntity.noContent().build();
    }

    /** Las noches celebradas, de la más reciente a la más antigua. */
    @GetMapping("/api/panel/noches")
    public List<NocheEnHistorico> celebradas(@RequestParam(defaultValue = "60") int limite) {
        return expedientes.celebradas(limite);
    }
}
