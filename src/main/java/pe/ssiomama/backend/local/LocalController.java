package pe.ssiomama.backend.local;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.ssiomama.backend.eventos.Puerta;
import pe.ssiomama.backend.eventos.PuertaRepository;
import pe.ssiomama.backend.eventos.Sala;
import pe.ssiomama.backend.eventos.SalaRepository;
import pe.ssiomama.backend.reservados.TipoDeZona;
import pe.ssiomama.backend.reservados.Zona;
import pe.ssiomama.backend.reservados.ZonaRepository;

/**
 * El plano del local: medidas de cada sala, sus zonas y sus puertas.
 *
 * <p>La simulación de evacuación <b>no</b> está aquí: es una fórmula sobre
 * estos números y se calcula en el navegador, que es lo que permite ver el
 * tiempo bajar al mover una puerta sin ir y volver al servidor.
 *
 * <p>Desde el plano no se crean ni se borran zonas o puertas: se recolocan. Una
 * puerta tiene accesos registrados y una zona tiene reservados colgando, y
 * borrarlas por arrastrar un rectángulo sería perder historia.
 */
@RestController
@RequestMapping("/api/local")
public class LocalController {

    private final SalaRepository salas;
    private final ZonaRepository zonas;
    private final PuertaRepository puertas;

    public LocalController(SalaRepository salas, ZonaRepository zonas, PuertaRepository puertas) {
        this.salas = salas;
        this.zonas = zonas;
        this.puertas = puertas;
    }

    // ── Lo que sale ───────────────────────────────────────────────

    public record ZonaDelPlano(
            String id,
            String nombre,
            TipoDeZona tipo,
            int aforo,
            double x,
            double y,
            double ancho,
            double fondo,
            double alto,
            int tono) {

        static ZonaDelPlano de(Zona z) {
            return new ZonaDelPlano(
                    z.getId(), z.getNombre(), z.getTipo(), z.getAforo(),
                    z.getX(), z.getY(), z.getAncho(), z.getFondo(), z.getAlto(), z.getTono());
        }
    }

    public record PuertaDelPlano(
            String id,
            String nombre,
            boolean esSalidaEmergencia,
            int caudalPorMinuto,
            boolean activo,
            double x,
            double y,
            double ancho) {

        static PuertaDelPlano de(Puerta p) {
            return new PuertaDelPlano(
                    p.getId(), p.getNombre(), p.isEsSalidaEmergencia(), p.getCaudalPorMinuto(),
                    p.isActivo(), p.getX(), p.getY(), p.getAncho());
        }
    }

    public record SalaDelPlano(
            String id,
            String nombre,
            int aforo,
            double planoAncho,
            double planoFondo,
            List<ZonaDelPlano> zonas,
            List<PuertaDelPlano> puertas) {}

    // ── Lo que entra ──────────────────────────────────────────────
    //
    // Los límites son los del sistema anterior: 500 m de lado es más que
    // cualquier local, y sirven para que un cero de más no deje un plano
    // inservible.

    public record ZonaDibujada(
            @NotBlank String id,
            @NotNull(message = "Falta el tipo de una zona") TipoDeZona tipo,
            @DecimalMin("0") @DecimalMax("500") double x,
            @DecimalMin("0") @DecimalMax("500") double y,
            @DecimalMin("0") @DecimalMax("500") double ancho,
            @DecimalMin("0") @DecimalMax("500") double fondo,
            @DecimalMin("0") @DecimalMax("40") double alto,
            @Min(0) @Max(100_000) int aforo) {}

    public record PuertaDibujada(
            @NotBlank String id,
            @DecimalMin("0") @DecimalMax("500") double x,
            @DecimalMin("0") @DecimalMax("500") double y,
            @DecimalMin(value = "0.1", message = "Una puerta mide al menos 10 cm")
            @DecimalMax("20") double ancho,
            @Min(0) @Max(10_000) int caudalPorMinuto,
            boolean esSalidaEmergencia) {}

    public record GeometriaDeSala(
            @NotBlank(message = "Falta la sala") String salaId,
            @DecimalMin("0") @DecimalMax("500") double planoAncho,
            @DecimalMin("0") @DecimalMax("500") double planoFondo,
            @NotNull @Size(max = 60) List<@Valid ZonaDibujada> zonas,
            @NotNull @Size(max = 60) List<@Valid PuertaDibujada> puertas) {}

    // ── Endpoints ─────────────────────────────────────────────────

    @GetMapping
    @Transactional(readOnly = true)
    public List<SalaDelPlano> local() {
        return salas.findAllByOrderByAforoDesc().stream()
                .map(s -> new SalaDelPlano(
                        s.getId(),
                        s.getNombre(),
                        s.getAforo(),
                        s.getPlanoAncho(),
                        s.getPlanoFondo(),
                        zonas.findBySalaIdOrderByOrdenAsc(s.getId()).stream()
                                .map(ZonaDelPlano::de).toList(),
                        puertas.findBySalaIdOrderByNombreAsc(s.getId()).stream()
                                .map(PuertaDelPlano::de).toList()))
                .toList();
    }

    /**
     * Guarda la geometría de una sala, todo o nada.
     *
     * <p>Una sola transacción: guardar las zonas y fallar en las puertas dejaría
     * un plano a medias, y sobre un plano a medias la evacuación daría un número
     * que parece bueno y no lo es.
     */
    @PutMapping
    @Transactional
    public ResponseEntity<Void> guardar(@Valid @RequestBody GeometriaDeSala geometria) {
        Sala sala = salas.findById(geometria.salaId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "La sala no existe."));

        // Las zonas y puertas tienen que ser de ESTA sala. Sin comprobarlo, un
        // id ajeno en la petición movería la zona de otro local.
        Map<String, Zona> suyasZ = zonas.findBySalaIdOrderByOrdenAsc(sala.getId()).stream()
                .collect(Collectors.toMap(Zona::getId, Function.identity()));
        Map<String, Puerta> suyasP = puertas.findBySalaIdOrderByNombreAsc(sala.getId()).stream()
                .collect(Collectors.toMap(Puerta::getId, Function.identity()));

        if (geometria.zonas().stream().anyMatch(z -> !suyasZ.containsKey(z.id()))
                || geometria.puertas().stream().anyMatch(p -> !suyasP.containsKey(p.id()))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Hay zonas o puertas que no pertenecen a esta sala.");
        }

        sala.medir(geometria.planoAncho(), geometria.planoFondo());
        for (ZonaDibujada z : geometria.zonas()) {
            suyasZ.get(z.id()).dibujar(
                    z.tipo(), z.x(), z.y(), z.ancho(), z.fondo(), z.alto(), z.aforo());
        }
        for (PuertaDibujada p : geometria.puertas()) {
            suyasP.get(p.id()).dibujar(
                    p.x(), p.y(), p.ancho(), p.caudalPorMinuto(), p.esSalidaEmergencia());
        }
        return ResponseEntity.noContent().build();
    }
}
