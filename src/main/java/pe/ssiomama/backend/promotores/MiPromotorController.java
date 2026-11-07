package pe.ssiomama.backend.promotores;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.eventos.EstadoDeEvento;
import pe.ssiomama.backend.eventos.EventoRepository;
import pe.ssiomama.backend.promotores.FichasDePromotores.FichaDePromotor;
import pe.ssiomama.backend.promotores.ListasController.FichaDeLista;
import pe.ssiomama.backend.promotores.ListasController.InvitadoEnFicha;
import pe.ssiomama.backend.promotores.ListasController.NocheParaLista;

/**
 * La pantalla del propio promotor: lo que ha vendido, lo que se le debe, lo que
 * se le ha pagado y sus listas.
 *
 * <h2>Solo se ve a sí mismo</h2>
 *
 * <p>Ningún endpoint de aquí recibe el id del promotor: sale siempre de la
 * sesión. Así no hay forma de pedir la ficha de otro cambiando un número en la
 * URL. Y las listas se comprueban una a una contra quien pregunta.
 *
 * <h2>Lo que puede y lo que no</h2>
 *
 * <p>Puede abrir listas para noches que vienen y apuntar o quitar gente. No
 * puede marcar quién entró: eso lo hace la puerta, y si pudiera hacerlo él se
 * estaría apuntando su propio pago por cabeza.
 */
@RestController
@RequestMapping("/api/mi-promotor")
public class MiPromotorController {

    /** Tope de una lista abierta por el propio promotor. Más grande, que la abra la sala. */
    private static final int TOPE_PROPIO = 150;

    private final FichasDePromotores fichas;
    private final ListaDeInvitadosRepository listas;
    private final InvitadoRepository invitados;
    private final EventoRepository eventos;
    private final GestionDeListas gestion;

    public MiPromotorController(
            FichasDePromotores fichas,
            ListaDeInvitadosRepository listas,
            InvitadoRepository invitados,
            EventoRepository eventos,
            GestionDeListas gestion) {
        this.fichas = fichas;
        this.listas = listas;
        this.invitados = invitados;
        this.eventos = eventos;
        this.gestion = gestion;
    }

    public record NuevaLista(
            @NotBlank(message = "Elige la noche") String slug,
            @NotBlank(message = "Ponle un nombre a la lista")
            @Size(min = 2, max = 80, message = "El nombre tiene entre 2 y 80 caracteres") String nombre,
            @Min(value = 1, message = "Al menos una persona")
            @Max(value = TOPE_PROPIO, message = "Para más de " + TOPE_PROPIO + " personas, habla con la sala")
            int maxInvitados) {}

    /**
     * @param noches las que admiten listas todavía, para el formulario
     */
    public record MiPanel(FichaDePromotor ficha, List<NocheParaLista> noches) {}

    @GetMapping
    @Transactional(readOnly = true)
    public MiPanel panel(@AuthenticationPrincipal Usuario quien) {
        Usuario yo = promotor(quien);
        Instant desde = Instant.now().minus(Duration.ofHours(2));
        List<NocheParaLista> noches = eventos.buscarTodosConDetalle().stream()
                .filter(e -> e.getEstado() == EstadoDeEvento.PUBLICADO || e.getEstado() == EstadoDeEvento.AGOTADO)
                .filter(e -> e.getEmpiezaA().isAfter(desde))
                .sorted(Comparator.comparing(e -> e.getEmpiezaA()))
                .map(e -> new NocheParaLista(e.getSlug(), e.getTitulo(), e.getEmpiezaA()))
                .toList();
        return new MiPanel(fichas.de(yo), noches);
    }

    @GetMapping("/listas/{id}")
    @Transactional(readOnly = true)
    public FichaDeLista lista(@PathVariable Long id, @AuthenticationPrincipal Usuario quien) {
        return FichaDeLista.de(suya(id, promotor(quien)));
    }

    @PostMapping("/listas")
    @Transactional
    public ResponseEntity<FichaDeLista> abrir(@Valid @RequestBody NuevaLista n, @AuthenticationPrincipal Usuario quien) {
        Usuario yo = promotor(quien);
        ListaDeInvitados l = gestion.abrir(n.slug(), yo.getId(), n.nombre(), n.maxInvitados());
        return ResponseEntity.status(HttpStatus.CREATED).body(FichaDeLista.de(l));
    }

    @PostMapping("/listas/{id}/invitados")
    @Transactional
    public ResponseEntity<InvitadoEnFicha> apuntar(
            @PathVariable Long id,
            @Valid @RequestBody ListasController.NuevoInvitado n,
            @AuthenticationPrincipal Usuario quien) {
        suya(id, promotor(quien));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InvitadoEnFicha.de(gestion.apuntar(id, n.nombreCompleto(), n.personas())));
    }

    @DeleteMapping("/invitados/{id}")
    @Transactional
    public ResponseEntity<Void> quitar(@PathVariable Long id, @AuthenticationPrincipal Usuario quien) {
        Usuario yo = promotor(quien);
        Invitado i = invitados.buscarConLista(id).orElseThrow(() -> new ListaRechazada("Esa fila no existe."));
        if (!i.getLista().getPromotor().getId().equals(yo.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esa lista no es tuya.");
        }
        gestion.quitar(id);
        return ResponseEntity.noContent().build();
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    /** Quien pregunta, si es promotor. Si no, 403: esta pantalla no es para él. */
    private static Usuario promotor(Usuario quien) {
        if (quien == null || !quien.esPromotor()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes código de promotor.");
        }
        return quien;
    }

    /** La lista, si es de quien pregunta. Una ajena responde 404 y no 403: no se confirma que exista. */
    private ListaDeInvitados suya(Long id, Usuario yo) {
        return listas.buscarConInvitados(id)
                .filter(l -> l.getPromotor().getId().equals(yo.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Esa lista no existe."));
    }
}
