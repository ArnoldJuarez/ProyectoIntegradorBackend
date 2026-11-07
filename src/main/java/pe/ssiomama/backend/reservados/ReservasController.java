package pe.ssiomama.backend.reservados;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.ssiomama.backend.clientes.CanalDeInteraccion;
import pe.ssiomama.backend.eventos.EstadoDeEvento;
import pe.ssiomama.backend.eventos.EventoRepository;

/**
 * Las reservas de box desde el panel: la lista de solicitudes, confirmarlas
 * tras la llamada, apuntar una que llega por teléfono y, la noche misma, marcar
 * quién se sentó y quién no vino.
 *
 * <p>Antes las solicitudes de la web solo se veían en la torre de control de la
 * noche, sin poder hacer nada con ellas: se confirmaban «de palabra» y el
 * sistema seguía diciendo «solicitada».
 */
@RestController
@RequestMapping("/api/reservas")
public class ReservasController {

    private final ReservaRepository reservas;
    private final MesaVipRepository mesas;
    private final EventoRepository eventos;
    private final GestionDeReservas gestion;

    public ReservasController(
            ReservaRepository reservas,
            MesaVipRepository mesas,
            EventoRepository eventos,
            GestionDeReservas gestion) {
        this.reservas = reservas;
        this.mesas = mesas;
        this.eventos = eventos;
        this.gestion = gestion;
    }

    // ── Lo que entra ──────────────────────────────────────────────

    /** Una reserva apuntada a mano, normalmente tras una llamada o un WhatsApp. */
    public record NuevaReserva(
            @NotBlank(message = "Elige la noche") String slug,
            @NotNull(message = "Elige un box") Long mesaId,
            @Min(value = 1, message = "¿Cuántas personas?") @Max(value = 30, message = "Más de 30 no caben en un box")
            int personas,
            @Size(max = 500, message = "Las notas son demasiado largas") String notas,
            @NotBlank(message = "Falta el nombre") @Size(max = 80) String nombre,
            @Size(max = 80) String apellido,
            @NotBlank(message = "Falta el correo: es como se reconoce al cliente")
            @Email(message = "El correo no es válido")
            @Size(max = 160)
            String email,
            @NotBlank(message = "Falta el teléfono") @Size(min = 6, max = 30, message = "Ese teléfono no parece completo")
            String telefono,
            /** Por dónde llegó: TELEFONO, WHATSAPP o INSTAGRAM. */
            CanalDeInteraccion canal) {}

    public record CambioDeReserva(
            @NotNull(message = "Falta el estado") EstadoDeReserva estado,
            @Min(value = 0, message = "El depósito no puede ser negativo")
            @Max(value = 10_000_000, message = "Ese depósito no parece real")
            int depositoCentimos,
            @Size(max = 500, message = "Las notas son demasiado largas") String notas) {}

    // ── Lo que sale ───────────────────────────────────────────────

    public record ReservaEnLista(
            Long id,
            String noche,
            String nocheSlug,
            Instant empiezaA,
            String mesa,
            String zona,
            int plazas,
            int consumoMinimoCentimos,
            String cliente,
            Long clienteId,
            String telefono,
            String email,
            int personas,
            int depositoCentimos,
            EstadoDeReserva estado,
            String notas,
            Instant creadaEn) {

        static ReservaEnLista de(Reserva r) {
            var c = r.getCliente();
            return new ReservaEnLista(
                    r.getId(),
                    r.getEvento().getTitulo(),
                    r.getEvento().getSlug(),
                    r.getEvento().getEmpiezaA(),
                    r.getMesa().getNombre(),
                    r.getMesa().getZona() == null ? null : r.getMesa().getZona().getNombre(),
                    r.getMesa().getPlazas(),
                    r.getMesa().getConsumoMinimoCentimos(),
                    c.getNombreCompleto(),
                    c.getId(),
                    c.getTelefono(),
                    c.getEmail(),
                    r.getPersonas(),
                    r.getDepositoCentimos(),
                    r.getEstado(),
                    r.getNotas(),
                    r.getCreadaEn());
        }
    }

    public record NocheReservable(String slug, String titulo, Instant empiezaA) {}

    public record BoxDelLocal(Long id, String nombre, String zona, int plazas, int consumoMinimoCentimos) {}

    public record OpcionesDeReserva(List<NocheReservable> noches, List<BoxDelLocal> boxes) {}

    // ── Endpoints ─────────────────────────────────────────────────

    /**
     * Las reservas de las próximas noches, o las de los últimos dos meses.
     *
     * <p>«Próximas» empieza doce horas atrás a propósito: a las tres de la
     * mañana la noche de hoy ya «empezó», y es justo cuando hay que marcar quién
     * se sentó.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<ReservaEnLista> listar(@RequestParam(defaultValue = "false") boolean pasadas) {
        Instant ahora = Instant.now();
        Instant corte = ahora.minus(Duration.ofHours(12));
        List<Reserva> filas = pasadas
                ? reservas.buscarEntre(ahora.minus(Duration.ofDays(60)), corte)
                : reservas.buscarDesde(corte);
        return filas.stream().map(ReservaEnLista::de).toList();
    }

    /** Las noches en las que se puede apuntar una reserva y los boxes del local. */
    @GetMapping("/opciones")
    @Transactional(readOnly = true)
    public OpcionesDeReserva opciones() {
        Instant ahora = Instant.now();
        List<NocheReservable> noches = eventos.buscarTodosConDetalle().stream()
                .filter(e -> e.getEmpiezaA().isAfter(ahora))
                .filter(e -> e.getEstado() == EstadoDeEvento.PUBLICADO || e.getEstado() == EstadoDeEvento.AGOTADO)
                .sorted(java.util.Comparator.comparing(e -> e.getEmpiezaA()))
                .map(e -> new NocheReservable(e.getSlug(), e.getTitulo(), e.getEmpiezaA()))
                .toList();
        List<BoxDelLocal> boxes = mesas.buscarDisponibles().stream()
                .map(m -> new BoxDelLocal(m.getId(), m.getNombre(),
                        m.getZona() == null ? null : m.getZona().getNombre(), m.getPlazas(),
                        m.getConsumoMinimoCentimos()))
                .toList();
        return new OpcionesDeReserva(noches, boxes);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ReservaEnLista> apuntar(@Valid @RequestBody NuevaReserva n) {
        CanalDeInteraccion canal = n.canal() == null || n.canal() == CanalDeInteraccion.WEB
                ? CanalDeInteraccion.TELEFONO
                : n.canal();
        Reserva r = gestion.solicitar(new GestionDeReservas.Solicitud(
                n.slug(), n.mesaId(), n.personas(), n.notas(), n.nombre(), n.apellido(), n.email(), n.telefono()),
                canal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ReservaEnLista.de(reservas.buscarCompleta(r.getId()).orElseThrow()));
    }

    @PutMapping("/{id}")
    @Transactional
    public ReservaEnLista cambiar(@PathVariable Long id, @Valid @RequestBody CambioDeReserva c) {
        return ReservaEnLista.de(gestion.cambiar(id, c.estado(), c.depositoCentimos(), c.notas()));
    }
}
