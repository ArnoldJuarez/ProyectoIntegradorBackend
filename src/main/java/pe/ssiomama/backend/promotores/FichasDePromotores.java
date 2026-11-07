package pe.ssiomama.backend.promotores;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.caja.MetodoDePago;
import pe.ssiomama.backend.promotores.Comisiones.NocheDelPromotor;
import pe.ssiomama.backend.promotores.Comisiones.Pendiente;
import pe.ssiomama.backend.promotores.Comisiones.Saldo;

/**
 * Las fichas de los promotores tal y como las leen el panel y el propio promotor.
 *
 * <p>Va aparte de los controladores porque las arman dos: el panel
 * ({@code /api/promotores}), que ve a todos, y la pantalla del promotor
 * ({@code /api/mi-promotor}), que solo se ve a sí mismo. Es la misma ficha para
 * que lo que dice el panel y lo que ve el promotor no puedan no cuadrar.
 */
@Service
public class FichasDePromotores {

    private final ListaDeInvitadosRepository listas;
    private final LiquidacionRepository liquidaciones;
    private final Comisiones comisiones;

    public FichasDePromotores(
            ListaDeInvitadosRepository listas, LiquidacionRepository liquidaciones, Comisiones comisiones) {
        this.listas = listas;
        this.liquidaciones = liquidaciones;
        this.comisiones = comisiones;
    }

    // ── Lo que sale ───────────────────────────────────────────────

    /**
     * Una lista con sus cifras.
     *
     * @param pasada si la noche ya empezó. Antes de eso «entraron 0» no dice
     *     nada malo de nadie, y la pantalla no debe pintarlo como un fracaso.
     */
    public record ListaEnFicha(
            Long id,
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
            Long id,
            String nombre,
            String rol,
            String email,
            String codigo,
            int comisionPorcentaje,
            int bonoPorInvitadoCentimos,
            boolean activo,
            long pedidos,
            /** El precio de las entradas vendidas con su código, sin gastos de gestión. */
            long vendidoCentimos,
            /** Lo que se le debe de noches que ya pasaron y no se le ha pagado. */
            long pendienteCentimos,
            /** Lo que ganará con lo ya vendido para noches que no han llegado. */
            long porCelebrarCentimos,
            long pagadoCentimos,
            Instant ultimoPago,
            int invitados,
            int entraron,
            List<ListaEnFicha> listas) {}

    public record Promotores(
            int activos,
            long vendidoCentimos,
            long pendienteCentimos,
            long pagadoCentimos,
            int invitados,
            int entraron,
            List<PromotorEnFicha> promotores) {}

    public record LiquidacionEnFicha(
            Long id,
            Instant pagadaEn,
            Instant hasta,
            int pedidos,
            int entradas,
            int ventasCentimos,
            int comisionPorcentaje,
            int comisionCentimos,
            int invitadosEntraron,
            int bonoPorInvitadoCentimos,
            int bonoCentimos,
            int ajusteCentimos,
            int totalCentimos,
            MetodoDePago metodoDePago,
            String referencia,
            String nota,
            String registradaPor) {

        static LiquidacionEnFicha de(Liquidacion l, String registradaPor) {
            return new LiquidacionEnFicha(
                    l.getId(), l.getPagadaEn(), l.getHasta(), l.getPedidos(), l.getEntradas(), l.getVentasCentimos(),
                    l.getComisionPorcentaje(), l.getComisionCentimos(), l.getInvitadosEntraron(),
                    l.getBonoPorInvitadoCentimos(), l.getBonoCentimos(), l.getAjusteCentimos(), l.getTotalCentimos(),
                    l.getMetodoDePago(), l.getReferencia(), l.getNota(), registradaPor);
        }
    }

    public record FichaDePromotor(
            PromotorEnFicha promotor,
            /** Lo que entraría en un pago hecho ahora mismo. */
            Pendiente pendiente,
            List<NocheDelPromotor> noches,
            List<LiquidacionEnFicha> liquidaciones) {}

    // ── Armado ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Promotores todos() {
        Instant ahora = Instant.now();
        Map<Long, Saldo> saldos = comisiones.saldos();
        Map<Long, List<ListaDeInvitados>> porPromotor = listas.buscarTodasConInvitados().stream()
                .collect(Collectors.groupingBy(l -> l.getPromotor().getId()));

        List<PromotorEnFicha> todas = listas.buscarPromotores().stream()
                .map(u -> resumen(u, saldos.get(u.getId()), porPromotor.getOrDefault(u.getId(), List.of()), ahora))
                .sorted(Comparator.comparingLong(PromotorEnFicha::pendienteCentimos)
                        .thenComparingLong(PromotorEnFicha::vendidoCentimos)
                        .reversed())
                .toList();

        return new Promotores(
                (int) todas.stream().filter(PromotorEnFicha::activo).count(),
                todas.stream().mapToLong(PromotorEnFicha::vendidoCentimos).sum(),
                todas.stream().mapToLong(PromotorEnFicha::pendienteCentimos).sum(),
                todas.stream().mapToLong(PromotorEnFicha::pagadoCentimos).sum(),
                todas.stream().mapToInt(PromotorEnFicha::invitados).sum(),
                todas.stream().mapToInt(PromotorEnFicha::entraron).sum(),
                todas);
    }

    @Transactional(readOnly = true)
    public FichaDePromotor de(Usuario u) {
        Instant ahora = Instant.now();
        List<ListaDeInvitados> suyas = listas.buscarTodasConInvitados().stream()
                .filter(l -> l.getPromotor().getId().equals(u.getId()))
                .toList();

        return new FichaDePromotor(
                resumen(u, comisiones.saldos().get(u.getId()), suyas, ahora),
                comisiones.pendiente(u, ahora),
                comisiones.porNoche(u),
                liquidaciones.deUnPromotor(u.getId()).stream()
                        .map(l -> LiquidacionEnFicha.de(
                                l, l.getRegistradaPor() == null ? null : l.getRegistradaPor().getNombre()))
                        .toList());
    }

    private static PromotorEnFicha resumen(Usuario u, Saldo saldo, List<ListaDeInvitados> suyas, Instant ahora) {
        List<ListaEnFicha> fichas = suyas.stream().map(l -> ListaEnFicha.de(l, ahora)).toList();
        return new PromotorEnFicha(
                u.getId(),
                u.getNombre(),
                u.getRol().name(),
                u.getEmail(),
                u.getCodigoDePromotor(),
                u.getComisionPorcentaje() == null ? 0 : u.getComisionPorcentaje(),
                u.getBonoPorInvitadoCentimos(),
                u.isActivo(),
                saldo == null ? 0 : saldo.pedidos(),
                saldo == null ? 0 : saldo.ventasCentimos(),
                saldo == null ? 0 : saldo.pendienteCentimos(),
                saldo == null ? 0 : saldo.porCelebrarCentimos(),
                saldo == null ? 0 : saldo.pagadoCentimos(),
                saldo == null ? null : saldo.ultimoPago(),
                fichas.stream().mapToInt(ListaEnFicha::apuntados).sum(),
                fichas.stream().mapToInt(ListaEnFicha::entraron).sum(),
                fichas);
    }
}
