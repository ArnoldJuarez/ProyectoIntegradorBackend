package pe.ssiomama.backend.entradas;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.ssiomama.backend.caja.MetodoDePago;

/**
 * Pedidos y entradas, para el panel.
 *
 * <p>De momento <b>solo lectura</b>. La compra en sí y el escaneo en puerta van
 * después y aparte: son las dos operaciones que corren en transacciones
 * serializables, y conviene tener el modelo verificado contra los datos reales
 * antes de tocarlas.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private static final int POR_DEFECTO = 50;
    private static final int MAXIMO = 200;

    private final PedidoRepository pedidos;
    private final EntradaRepository entradas;

    public PedidoController(PedidoRepository pedidos, EntradaRepository entradas) {
        this.pedidos = pedidos;
        this.entradas = entradas;
    }

    // ── Lo que sale por la API ────────────────────────────────────

    public record PedidoEnLista(
            String codigo,
            String cliente,
            String noche,
            String nocheSlug,
            EstadoDePedido estado,
            String estadoEtiqueta,
            MetodoDePago metodoDePago,
            int totalCentimos,
            Instant creadoEn,
            Instant pagadoEn) {

        static PedidoEnLista de(Pedido p) {
            return new PedidoEnLista(
                    p.getCodigo(),
                    p.getCliente().getNombreCompleto(),
                    p.getEvento().getTitulo(),
                    p.getEvento().getSlug(),
                    p.getEstado(),
                    p.getEstado().etiqueta(),
                    p.getMetodoDePago(),
                    p.getTotalCentimos(),
                    p.getCreadoEn(),
                    p.getPagadoEn());
        }
    }

    public record LineaEnFicha(
            String tipoDeEntrada, int cantidad, int precioUnitarioCentimos, long importeCentimos) {

        static LineaEnFicha de(LineaDePedido l) {
            return new LineaEnFicha(
                    l.getTipoDeEntrada().getNombre(),
                    l.getCantidad(),
                    l.getPrecioUnitarioCentimos(),
                    l.importeCentimos());
        }
    }

    public record EntradaEnFicha(
            /**
             * El número de la entrada, para poder identificarla al hablar. NO
             * se manda el código entero: incluye la firma del QR, no dice nada
             * a una persona, y publicarlo en una lista es repartir entradas
             * válidas.
             */
            String referencia,
            String tipoDeEntrada,
            String titular,
            EstadoDeEntrada estado,
            String estadoEtiqueta,
            boolean yaSeUso,
            Instant escaneadaEn) {

        static EntradaEnFicha de(Entrada e) {
            return new EntradaEnFicha(
                    String.valueOf(e.getId()),
                    e.getTipoDeEntrada().getNombre(),
                    e.getTitular(),
                    e.getEstado(),
                    e.getEstado().etiqueta(),
                    e.yaSeUso(),
                    e.yaSeUso() ? e.getEscaneo().getEscaneadoEn() : null);
        }
    }

    public record FichaDePedido(
            PedidoEnLista pedido,
            String email,
            String telefono,
            int subtotalCentimos,
            int comisionCentimos,
            List<LineaEnFicha> lineas,
            List<EntradaEnFicha> entradas) {}

    /** Cómo va el aforo de una noche, en vivo. */
    public record AforoDeLaNoche(
            String slug,
            int emitidas,
            long dentro,
            Map<String, Long> porEstado) {}

    // ── Endpoints ─────────────────────────────────────────────────

    @GetMapping
    @Transactional(readOnly = true)
    public List<PedidoEnLista> listar(@RequestParam(defaultValue = "" + POR_DEFECTO) int limite) {
        var pagina = PageRequest.of(0, Math.clamp(limite, 1, MAXIMO));
        return pedidos.buscarRecientes(pagina).stream().map(PedidoEnLista::de).toList();
    }

    /** Un pedido por el código que ve el cliente, con sus entradas. */
    @GetMapping("/{codigo}")
    @Transactional(readOnly = true)
    public ResponseEntity<FichaDePedido> ficha(@PathVariable String codigo) {
        Pedido p = pedidos.buscarPorCodigoConLineas(codigo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe ese pedido."));

        return ResponseEntity.ok(new FichaDePedido(
                PedidoEnLista.de(p),
                p.getCliente().getEmail(),
                p.getCliente().getTelefono(),
                p.getSubtotalCentimos(),
                p.getComisionCentimos(),
                p.getLineas().stream().map(LineaEnFicha::de).toList(),
                entradas.buscarPorCodigoDePedido(codigo).stream()
                        .map(EntradaEnFicha::de)
                        .toList()));
    }

    /**
     * El aforo de una noche: cuántas se emitieron y cuántas han entrado ya.
     *
     * <p>Es lo que mira la dirección durante la noche, así que se calcula con
     * dos consultas agregadas en vez de traerse las entradas.
     */
    @GetMapping("/aforo/{slug}")
    @Transactional(readOnly = true)
    public AforoDeLaNoche aforo(@PathVariable String slug) {
        Map<String, Long> porEstado = new LinkedHashMap<>();
        int emitidas = 0;

        for (Object[] fila : entradas.contarPorEstado(slug)) {
            EstadoDeEntrada estado = (EstadoDeEntrada) fila[0];
            long cuantas = ((Number) fila[1]).longValue();
            porEstado.put(estado.etiqueta(), cuantas);
            emitidas += (int) cuantas;
        }

        return new AforoDeLaNoche(slug, emitidas, entradas.contarEscaneadas(slug), porEstado);
    }
}
