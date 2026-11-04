package pe.ssiomama.backend.publico;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.ssiomama.backend.clientes.CanalDeInteraccion;
import pe.ssiomama.backend.reservados.EstadoDeReserva;
import pe.ssiomama.backend.reservados.GestionDeReservas;
import pe.ssiomama.backend.reservados.MesaVipRepository;
import pe.ssiomama.backend.reservados.Reserva;
import pe.ssiomama.backend.reservados.ReservaRepository;

/**
 * Reservados: qué mesas hay libres para una noche y cómo pedir una.
 *
 * <p>Pedir un reservado <b>no lo aparta</b>: deja una solicitud en
 * {@code SOLICITADA} y alguien del local llama para confirmarla y acordar el
 * depósito desde el panel (ver {@code ReservasController}). La web no cobra ni
 * compromete la mesa, y por eso el mensaje de vuelta lo dice con esas palabras.
 *
 * <p>Las reglas —qué noches admiten reservas, cuántos caben, qué pasa si dos
 * piden a la vez— viven en {@link GestionDeReservas}, que comparte con el
 * panel: una reserva tomada por teléfono sigue las mismas.
 */
@RestController
@RequestMapping("/api/publico")
public class ReservadosController {

    private final MesaVipRepository mesas;
    private final ReservaRepository reservas;
    private final CarteleraRepository cartelera;
    private final GestionDeReservas gestion;

    public ReservadosController(
            MesaVipRepository mesas,
            ReservaRepository reservas,
            CarteleraRepository cartelera,
            GestionDeReservas gestion) {
        this.mesas = mesas;
        this.reservas = reservas;
        this.cartelera = cartelera;
        this.gestion = gestion;
    }

    public record MesaDisponible(
            String id,
            String nombre,
            String zona,
            int plazas,
            int consumoMinimoCentimos,
            boolean libre) {}

    public record Solicitud(
            @NotBlank(message = "Falta la noche") String slug,
            @NotBlank(message = "Elige un reservado") String mesaId,
            @Min(value = 1, message = "Dinos cuántos venís")
            @Max(value = 30, message = "Para grupos de más de 30 llámanos")
            int personas,
            @Size(max = 500, message = "El comentario es demasiado largo") String notas,
            @NotBlank(message = "Falta el nombre")
            @Size(max = 80, message = "Ese nombre es demasiado largo")
            String nombre,
            @Size(max = 80, message = "Ese apellido es demasiado largo") String apellido,
            @NotBlank(message = "Falta el correo")
            @Email(message = "El correo no es válido")
            @Size(max = 160)
            String email,
            @NotBlank(message = "Necesitamos un teléfono para confirmar la mesa")
            @Size(min = 6, max = 30, message = "Ese teléfono no parece completo")
            String telefono) {}

    public record Respuesta(String id, String mensaje) {}

    /**
     * Los reservados de una noche, marcando cuáles siguen libres.
     *
     * <p>Se devuelven también los pedidos, marcados como no libres, en vez de
     * esconderlos: ver que «el box grande está cogido» explica por qué no
     * aparece, y esconderlo parece un fallo de la web.
     */
    @GetMapping("/reservados/{slug}")
    @Transactional(readOnly = true)
    public List<MesaDisponible> deLaNoche(@PathVariable String slug) {
        // Comprueba que la noche existe y es pública antes de enseñar nada.
        cartelera.buscarPublicaPorSlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Set<String> pedidas =
                Set.copyOf(reservas.idsDeMesasPedidas(slug, EstadoDeReserva.CANCELADO));

        return mesas.buscarDisponibles().stream()
                .map(m -> new MesaDisponible(
                        m.getId(),
                        m.getNombre(),
                        m.getZona() == null ? null : m.getZona().getNombre(),
                        m.getPlazas(),
                        m.getConsumoMinimoCentimos(),
                        !pedidas.contains(m.getId())))
                .toList();
    }

    @PostMapping("/reservas")
    @Transactional
    public ResponseEntity<Respuesta> pedir(@Valid @RequestBody Solicitud s) {
        Reserva reserva = gestion.solicitar(new GestionDeReservas.Solicitud(
                s.slug(), s.mesaId(), s.personas(), s.notas(), s.nombre(), s.apellido(), s.email(), s.telefono()),
                CanalDeInteraccion.WEB);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new Respuesta(
                        reserva.getId(),
                        "Solicitud recibida. Te llamamos para confirmarla y acordar el depósito."));
    }
}
