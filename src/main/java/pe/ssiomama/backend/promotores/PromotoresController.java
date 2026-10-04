package pe.ssiomama.backend.promotores;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pe.ssiomama.backend.auth.Usuario;

/**
 * Promotores: quién trae gente, cuánta entra de verdad y cuánto se le debe.
 *
 * <p>Promotor no es una tabla: es cualquiera del personal con
 * {@code codigoPromotor}. Por eso aquí puede salir el promotor externo de
 * siempre y también el de barra que una noche jaló a un grupo.
 *
 * <p>La comisión <b>se calcula, no se guarda</b>: es {@code comisionPct} sobre
 * lo pagado con su código. Guardarla sería otro contador que mantener
 * sincronizado para algo que se mira dos veces al mes.
 *
 * <p>Solo lectura, igual que en el sistema anterior: las listas se abren desde
 * fuera del panel.
 */
@RestController
public class PromotoresController {

    private final ListaDeInvitadosRepository listas;

    public PromotoresController(ListaDeInvitadosRepository listas) {
        this.listas = listas;
    }

    // ── Lo que sale ───────────────────────────────────────────────

    /**
     * Una lista con sus cifras.
     *
     * @param pasada si la noche ya empezó. Antes de eso «entraron 0» no dice
     *     nada malo de nadie, y la pantalla no debe pintarlo como un fracaso.
     */
    public record ListaEnFicha(
            String id,
            String nombre,
            String noche,
            String nocheSlug,
            Instant empiezaA,
            String promotor,
            int apuntados,
            int maxInvitados,
            int entraron,
            boolean pasada) {

        static ListaEnFicha de(ListaDeInvitados l, Instant ahora) {
            return new ListaEnFicha(
                    l.getId(),
                    l.getNombre(),
                    l.getEvento().getTitulo(),
                    l.getEvento().getSlug(),
                    l.getEvento().getEmpiezaA(),
                    l.getPromotor().getNombre(),
                    l.apuntados(),
                    l.getMaxInvitados(),
                    l.entraron(),
                    l.getEvento().getEmpiezaA().isBefore(ahora));
        }
    }

    public record PromotorEnFicha(
            String id,
            String nombre,
            String rol,
            String email,
            String codigo,
            int comisionPorcentaje,
            boolean activo,
            long pedidos,
            long vendidoCentimos,
            long comisionCentimos,
            int invitados,
            int entraron,
            List<ListaEnFicha> listas) {}

    public record Promotores(
            int activos,
            long vendidoCentimos,
            long comisionesCentimos,
            int invitados,
            int entraron,
            List<PromotorEnFicha> promotores) {}

    // ── Endpoints ─────────────────────────────────────────────────

    @GetMapping("/api/promotores")
    @Transactional(readOnly = true)
    public Promotores promotores() {
        Instant ahora = Instant.now();

        Map<String, ListaDeInvitadosRepository.VentaDePromotor> ventas = listas.ventasPorPromotor()
                .stream()
                .collect(Collectors.toMap(
                        ListaDeInvitadosRepository.VentaDePromotor::getPromotorId,
                        Function.identity()));

        Map<String, List<ListaDeInvitados>> listasPorPromotor = listas.buscarTodasConInvitados()
                .stream()
                .collect(Collectors.groupingBy(l -> l.getPromotor().getId()));

        List<PromotorEnFicha> fichas = listas.buscarPromotores().stream()
                .map(u -> ficha(
                        u,
                        ventas.get(u.getId()),
                        listasPorPromotor.getOrDefault(u.getId(), List.of()),
                        ahora))
                .sorted(Comparator.comparingLong(PromotorEnFicha::comisionCentimos).reversed())
                .toList();

        return new Promotores(
                (int) fichas.stream().filter(PromotorEnFicha::activo).count(),
                fichas.stream().mapToLong(PromotorEnFicha::vendidoCentimos).sum(),
                fichas.stream().mapToLong(PromotorEnFicha::comisionCentimos).sum(),
                fichas.stream().mapToInt(PromotorEnFicha::invitados).sum(),
                fichas.stream().mapToInt(PromotorEnFicha::entraron).sum(),
                fichas);
    }

    /** Las listas de invitados de una noche, para su ficha en el panel. */
    @GetMapping("/api/eventos/{slug}/listas")
    @Transactional(readOnly = true)
    public List<ListaEnFicha> listasDeLaNoche(@PathVariable String slug) {
        Instant ahora = Instant.now();
        return listas.buscarDeLaNoche(slug).stream().map(l -> ListaEnFicha.de(l, ahora)).toList();
    }

    private static PromotorEnFicha ficha(
            Usuario u,
            ListaDeInvitadosRepository.VentaDePromotor venta,
            List<ListaDeInvitados> suyas,
            Instant ahora) {

        long vendido = venta == null ? 0 : venta.getTotal();
        int porcentaje = u.getComisionPorcentaje() == null ? 0 : u.getComisionPorcentaje();
        List<ListaEnFicha> fichas = suyas.stream().map(l -> ListaEnFicha.de(l, ahora)).toList();

        return new PromotorEnFicha(
                u.getId(),
                u.getNombre(),
                u.getRol().name(),
                u.getEmail(),
                u.getCodigoDePromotor(),
                porcentaje,
                u.isActivo(),
                venta == null ? 0 : venta.getPedidos(),
                vendido,
                Math.round(vendido * porcentaje / 100.0),
                fichas.stream().mapToInt(ListaEnFicha::apuntados).sum(),
                fichas.stream().mapToInt(ListaEnFicha::entraron).sum(),
                fichas);
    }
}
