package pe.ssiomama.backend.eventos;

import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las noches, para el panel.
 *
 * <p>Equivale a {@code app/panel/eventos/page.tsx} del sistema anterior.
 */
@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    private final EventoRepository eventos;

    public EventoController(EventoRepository eventos) {
        this.eventos = eventos;
    }

    /**
     * Una noche en el listado.
     *
     * <p>Las fechas salen como {@link Instant}, que Jackson serializa en ISO-8601
     * con la {@code Z} del final. Se manda el instante y no una hora ya
     * formateada a propósito: quien la enseña sabe en qué zona y en qué idioma
     * la quiere; quien la sirve, no.
     */
    public record EventoResumen(
            String id,
            String slug,
            String titulo,
            String sala,
            Instant puertasA,
            Instant empiezaA,
            EstadoDeEvento estado,
            int aforoALaVenta,
            int vendidas,
            long recaudadoCentimos) {

        static EventoResumen de(Evento e) {
            return new EventoResumen(
                    e.getId(),
                    e.getSlug(),
                    e.getTitulo(),
                    e.getSala().getNombre(),
                    e.getPuertasA(),
                    e.getEmpiezaA(),
                    e.getEstado(),
                    e.getAforoALaVenta(),
                    e.getVendidas(),
                    e.getRecaudadoCentimos());
        }
    }

    public record TipoDeEntradaResumen(
            String id,
            String nombre,
            String descripcion,
            int precioCentimos,
            int cantidad,
            int vendidas,
            int disponibles,
            /*
             * Estos dos salen aquí desde que el panel edita los tipos de
             * entrada: el formulario los rellena con lo que ya hay, y sin ellos
             * cada edición los pisaría con el valor por defecto.
             */
            int maxPorPedido,
            int consumicionesIncluidas,
            boolean activo) {

        static TipoDeEntradaResumen de(TipoDeEntrada t) {
            return new TipoDeEntradaResumen(
                    t.getId(),
                    t.getNombre(),
                    t.getDescripcion(),
                    t.getPrecioCentimos(),
                    t.getCantidad(),
                    t.getVendidas(),
                    t.getDisponibles(),
                    t.getMaximoPorPedido(),
                    t.getConsumicionesIncluidas(),
                    t.isActivo());
        }
    }

    /** La ficha completa de una noche. */
    public record EventoDetalle(
            String id,
            String slug,
            String titulo,
            String descripcion,
            String portada,
            String sala,
            int aforoDeSala,
            Instant puertasA,
            Instant empiezaA,
            Instant terminaA,
            int edadMinima,
            String vestimenta,
            EstadoDeEvento estado,
            int vendidas,
            long recaudadoCentimos,
            List<TipoDeEntradaResumen> tiposDeEntrada) {

        static EventoDetalle de(Evento e) {
            return new EventoDetalle(
                    e.getId(),
                    e.getSlug(),
                    e.getTitulo(),
                    e.getDescripcion(),
                    e.getPortada(),
                    e.getSala().getNombre(),
                    e.getSala().getAforo(),
                    e.getPuertasA(),
                    e.getEmpiezaA(),
                    e.getTerminaA(),
                    e.getEdadMinima(),
                    e.getVestimenta(),
                    e.getEstado(),
                    e.getVendidas(),
                    e.getRecaudadoCentimos(),
                    e.getTiposDeEntrada().stream().map(TipoDeEntradaResumen::de).toList());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<EventoResumen> listar() {
        return eventos.buscarTodosConDetalle().stream().map(EventoResumen::de).toList();
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    public ResponseEntity<EventoDetalle> ficha(@PathVariable String slug) {
        return eventos.buscarPorSlugConDetalle(slug)
                .map(EventoDetalle::de)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
