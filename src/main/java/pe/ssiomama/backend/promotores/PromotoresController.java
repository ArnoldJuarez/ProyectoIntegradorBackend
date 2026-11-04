package pe.ssiomama.backend.promotores;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.auth.UsuarioRepository;
import pe.ssiomama.backend.caja.MetodoDePago;
import pe.ssiomama.backend.promotores.FichasDePromotores.FichaDePromotor;
import pe.ssiomama.backend.promotores.FichasDePromotores.LiquidacionEnFicha;
import pe.ssiomama.backend.promotores.FichasDePromotores.ListaEnFicha;
import pe.ssiomama.backend.promotores.FichasDePromotores.Promotores;

/**
 * Promotores: quién trae gente, cuánta entra de verdad, cuánto se le debe y
 * cuánto se le ha pagado.
 *
 * <p>Promotor no es una tabla: es cualquiera del personal con
 * {@code codigoPromotor}. Por eso aquí puede salir el promotor externo de
 * siempre y también el de barra que una noche jaló a un grupo.
 *
 * <p>La comisión se <b>calcula</b> mientras está pendiente y se <b>congela</b>
 * al pagarla: ver {@link Comisiones} y {@link Liquidacion}.
 */
@RestController
public class PromotoresController {

    private final FichasDePromotores fichas;
    private final Comisiones comisiones;
    private final ListaDeInvitadosRepository listas;
    private final UsuarioRepository usuarios;

    public PromotoresController(
            FichasDePromotores fichas,
            Comisiones comisiones,
            ListaDeInvitadosRepository listas,
            UsuarioRepository usuarios) {
        this.fichas = fichas;
        this.comisiones = comisiones;
        this.listas = listas;
        this.usuarios = usuarios;
    }

    // ── Lo que entra ──────────────────────────────────────────────

    public record Pago(
            /** Incluye las noches que empezaron antes de este instante. Vacío: hasta ahora. */
            Instant hasta,
            @NotNull(message = "Elige cómo se le pagó") MetodoDePago metodoDePago,
            @Size(max = 80, message = "La referencia es demasiado larga") String referencia,
            @Size(max = 500, message = "La nota es demasiado larga") String nota,
            @Min(value = -10_000_000, message = "Ese ajuste no parece real")
            @Max(value = 10_000_000, message = "Ese ajuste no parece real")
            int ajusteCentimos) {}

    // ── Endpoints ─────────────────────────────────────────────────

    @GetMapping("/api/promotores")
    @Transactional(readOnly = true)
    public Promotores promotores() {
        return fichas.todos();
    }

    /** Un promotor con su desglose noche a noche, lo pendiente y lo pagado. */
    @GetMapping("/api/promotores/{id}")
    @Transactional(readOnly = true)
    public FichaDePromotor ficha(@PathVariable String id) {
        Usuario u = usuarios.findById(id)
                .filter(Usuario::esPromotor)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese promotor no existe."));
        return fichas.de(u);
    }

    @PostMapping("/api/promotores/{id}/liquidaciones")
    public ResponseEntity<LiquidacionEnFicha> liquidar(
            @PathVariable String id, @Valid @RequestBody Pago p, @AuthenticationPrincipal Usuario quien) {
        Liquidacion l = comisiones.liquidar(
                id, p.hasta(), p.metodoDePago(), p.referencia(), p.nota(), p.ajusteCentimos(), quien);
        return ResponseEntity.status(HttpStatus.CREATED).body(LiquidacionEnFicha.de(l, quien.getNombre()));
    }

    @DeleteMapping("/api/promotores/liquidaciones/{id}")
    public ResponseEntity<Void> anular(@PathVariable String id) {
        comisiones.anular(id);
        return ResponseEntity.noContent().build();
    }

    /** Las listas de invitados de una noche, para su ficha en el panel. */
    @GetMapping("/api/eventos/{slug}/listas")
    @Transactional(readOnly = true)
    public List<ListaEnFicha> listasDeLaNoche(@PathVariable String slug) {
        Instant ahora = Instant.now();
        return listas.buscarDeLaNoche(slug).stream().map(l -> ListaEnFicha.de(l, ahora)).toList();
    }
}
