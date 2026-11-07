package pe.ssiomama.backend.promotores;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ssiomama.backend.eventos.EstadoDeEvento;
import pe.ssiomama.backend.eventos.EventoRepository;

/**
 * Las listas de invitados desde el panel: abrirlas, apuntar gente, marcar la
 * entrada y cerrarlas.
 *
 * <p>Va bajo {@code /api/promotores}, que ya es solo del panel: las cifras de
 * las listas son lo que se le paga a cada promotor.
 */
@RestController
@RequestMapping("/api/promotores")
public class ListasController {

    private final ListaDeInvitadosRepository listas;
    private final EventoRepository eventos;
    private final GestionDeListas gestion;

    public ListasController(ListaDeInvitadosRepository listas, EventoRepository eventos, GestionDeListas gestion) {
        this.listas = listas;
        this.eventos = eventos;
        this.gestion = gestion;
    }

    public record FormularioDeLista(
            @NotBlank(message = "Elige la noche") String slug,
            @NotNull(message = "Elige el promotor") Long promotorId,
            @NotBlank(message = "Ponle un nombre a la lista")
            @Size(min = 2, max = 80, message = "El nombre tiene entre 2 y 80 caracteres")
            String nombre,
            @Min(value = 1, message = "Al menos una persona") @Max(value = 500, message = "Más que el aforo no cabe")
            int maxInvitados) {}

    public record CambioDeLista(
            @NotBlank(message = "Ponle un nombre a la lista") @Size(min = 2, max = 80) String nombre,
            @Min(value = 1, message = "Al menos una persona") @Max(value = 500) int maxInvitados) {}

    public record NuevoInvitado(
            @NotBlank(message = "Falta el nombre") @Size(max = 120, message = "Ese nombre es demasiado largo")
            String nombreCompleto,
            @Min(value = 1, message = "Al menos una persona") @Max(value = 20, message = "Más de 20 es otra lista")
            int personas) {}

    public record Entrada(boolean entro) {}

    public record InvitadoEnFicha(Long id, String nombreCompleto, int personas, boolean entro, Instant entroEn) {

        static InvitadoEnFicha de(Invitado i) {
            return new InvitadoEnFicha(i.getId(), i.getNombreCompleto(), i.getPersonas(), i.isEntro(), i.getEntroEn());
        }
    }

    public record FichaDeLista(
            Long id,
            String nombre,
            String noche,
            String nocheSlug,
            Instant empiezaA,
            Long promotorId,
            String promotor,
            int maxInvitados,
            int apuntados,
            int entraron,
            List<InvitadoEnFicha> invitados) {

        static FichaDeLista de(ListaDeInvitados l) {
            return new FichaDeLista(l.getId(), l.getNombre(), l.getEvento().getTitulo(), l.getEvento().getSlug(),
                    l.getEvento().getEmpiezaA(), l.getPromotor().getId(), l.getPromotor().getNombre(),
                    l.getMaxInvitados(), l.apuntados(), l.entraron(),
                    l.getInvitados().stream()
                            .sorted(Comparator.comparing(Invitado::getNombreCompleto, String.CASE_INSENSITIVE_ORDER))
                            .map(InvitadoEnFicha::de)
                            .toList());
        }
    }

    public record NocheParaLista(String slug, String titulo, Instant empiezaA) {}

    public record PromotorParaLista(Long id, String nombre, String codigo) {}

    public record OpcionesDeLista(List<NocheParaLista> noches, List<PromotorParaLista> promotores) {}

    /** Las noches que admiten listas (de hoy en adelante) y quién puede llevarlas. */
    @GetMapping("/opciones")
    @Transactional(readOnly = true)
    public OpcionesDeLista opciones() {
        Instant desde = Instant.now().minus(Duration.ofHours(12));
        return new OpcionesDeLista(
                eventos.buscarTodosConDetalle().stream()
                        .filter(e -> e.getEmpiezaA().isAfter(desde))
                        .filter(e -> e.getEstado() != EstadoDeEvento.CANCELADO && e.getEstado() != EstadoDeEvento.CERRADO)
                        .sorted(Comparator.comparing(e -> e.getEmpiezaA()))
                        .map(e -> new NocheParaLista(e.getSlug(), e.getTitulo(), e.getEmpiezaA()))
                        .toList(),
                listas.buscarPromotores().stream()
                        .filter(u -> u.isActivo())
                        .map(u -> new PromotorParaLista(u.getId(), u.getNombre(), u.getCodigoDePromotor()))
                        .toList());
    }

    @GetMapping("/listas/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<FichaDeLista> ficha(@PathVariable Long id) {
        return listas.buscarConInvitados(id)
                .map(FichaDeLista::de)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/listas")
    @Transactional
    public ResponseEntity<FichaDeLista> abrir(@Valid @RequestBody FormularioDeLista f) {
        ListaDeInvitados l = gestion.abrir(f.slug(), f.promotorId(), f.nombre(), f.maxInvitados());
        return ResponseEntity.status(HttpStatus.CREATED).body(FichaDeLista.de(l));
    }

    @PutMapping("/listas/{id}")
    @Transactional
    public FichaDeLista cambiar(@PathVariable Long id, @Valid @RequestBody CambioDeLista c) {
        return FichaDeLista.de(gestion.cambiar(id, c.nombre(), c.maxInvitados()));
    }

    @DeleteMapping("/listas/{id}")
    @Transactional
    public ResponseEntity<Void> borrar(@PathVariable Long id) {
        gestion.borrar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/listas/{id}/invitados")
    @Transactional
    public ResponseEntity<InvitadoEnFicha> apuntar(@PathVariable Long id, @Valid @RequestBody NuevoInvitado n) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InvitadoEnFicha.de(gestion.apuntar(id, n.nombreCompleto(), n.personas())));
    }

    @DeleteMapping("/invitados/{id}")
    @Transactional
    public ResponseEntity<Void> quitar(@PathVariable Long id) {
        gestion.quitar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/invitados/{id}/entrada")
    @Transactional
    public InvitadoEnFicha marcarEntrada(@PathVariable Long id, @RequestBody Entrada e) {
        return InvitadoEnFicha.de(gestion.marcarEntrada(id, e.entro()));
    }
}
