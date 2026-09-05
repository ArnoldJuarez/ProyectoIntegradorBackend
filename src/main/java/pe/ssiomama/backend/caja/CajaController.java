package pe.ssiomama.backend.caja;

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

/**
 * Los arqueos de caja: qué turnos hubo, cuánto se vendió y si cuadró.
 *
 * <p>Equivale a {@code app/panel/caja/} del sistema anterior.
 */
@RestController
@RequestMapping("/api/caja")
public class CajaController {

    private static final int ARQUEOS_POR_DEFECTO = 30;
    private static final int ARQUEOS_MAXIMO = 100;

    private final ArqueoRepository arqueos;
    private final VentaDeBarraRepository ventas;

    public CajaController(ArqueoRepository arqueos, VentaDeBarraRepository ventas) {
        this.arqueos = arqueos;
        this.ventas = ventas;
    }

    // ── Lo que sale por la API ────────────────────────────────────

    public record ArqueoEnLista(
            String id,
            String sala,
            String responsable,
            int aperturaCentimos,
            int esperadoCentimos,
            Integer contadoCentimos,
            Integer diferenciaCentimos,
            /**
             * Solo el efectivo que entró. NO es lo vendido: la tarjeta no llega
             * al cajón. Ver la nota en {@code ArqueoDeCaja}.
             */
            int efectivoCobradoCentimos,
            boolean estaAbierta,
            boolean cuadro,
            Instant abiertaEn,
            Instant cerradaEn) {

        static ArqueoEnLista de(ArqueoDeCaja a) {
            return new ArqueoEnLista(
                    a.getId(),
                    a.getSala().getNombre(),
                    a.getResponsable().getNombre(),
                    a.getAperturaCentimos(),
                    a.getEsperadoCentimos(),
                    a.getContadoCentimos(),
                    a.getDiferenciaCentimos(),
                    a.efectivoCobradoCentimos(),
                    a.estaAbierta(),
                    a.cuadro(),
                    a.getAbiertaEn(),
                    a.getCerradaEn());
        }
    }

    /** Cuánto entró por cada forma de pago. */
    public record TotalPorMetodo(MetodoDePago metodo, String etiqueta, boolean esEfectivo, long totalCentimos) {}

    public record CobroEnLista(
            String id,
            MetodoDePago metodo,
            String metodoEtiqueta,
            int totalCentimos,
            int unidades,
            Instant cuando) {

        static CobroEnLista de(VentaDeBarra v) {
            return new CobroEnLista(
                    v.getId(),
                    v.getMetodoDePago(),
                    v.getMetodoDePago().etiqueta(),
                    v.getTotalCentimos(),
                    v.unidadesVendidas(),
                    v.getCreadaEn());
        }
    }

    /**
     * La ficha de un arqueo.
     *
     * <p>Lleva el desglose por método de pago porque es lo que explica un
     * descuadre: si faltan cien soles pero casi todo se cobró con tarjeta, el
     * problema no está en el cajón.
     */
    public record FichaDeArqueo(
            ArqueoEnLista arqueo,
            List<TotalPorMetodo> porMetodoDePago,
            long efectivoCentimos,
            long otrosMediosCentimos,
            int cobros,
            List<CobroEnLista> ultimosCobros) {}

    // ── Endpoints ─────────────────────────────────────────────────

    @GetMapping("/arqueos")
    @Transactional(readOnly = true)
    public List<ArqueoEnLista> listar(
            @RequestParam(defaultValue = "" + ARQUEOS_POR_DEFECTO) int limite) {

        var pagina = PageRequest.of(0, Math.clamp(limite, 1, ARQUEOS_MAXIMO));
        return arqueos.buscarRecientes(pagina).stream().map(ArqueoEnLista::de).toList();
    }

    @GetMapping("/arqueos/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<FichaDeArqueo> ficha(@PathVariable String id) {
        ArqueoDeCaja arqueo = arqueos.buscarPorIdConDetalle(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe ese arqueo."));

        /*
         * El desglose se pide agregado a la base: una noche buena son
         * centenares de cobros y no hace falta traerlos para sumarlos.
         *
         * Se recorren los valores del enum en orden en vez de los resultados
         * para que el desglose salga siempre en el mismo orden, aunque una
         * noche no haya habido ningún pago con Plin.
         */
        Map<MetodoDePago, Long> sumas = new LinkedHashMap<>();
        for (Object[] fila : ventas.sumarPorMetodoDePago(id)) {
            sumas.put((MetodoDePago) fila[0], ((Number) fila[1]).longValue());
        }

        List<TotalPorMetodo> desglose = java.util.Arrays.stream(MetodoDePago.values())
                .filter(sumas::containsKey)
                .map(m -> new TotalPorMetodo(m, m.etiqueta(), m.esEfectivo(), sumas.get(m)))
                .toList();

        long efectivo = desglose.stream()
                .filter(TotalPorMetodo::esEfectivo)
                .mapToLong(TotalPorMetodo::totalCentimos)
                .sum();
        long otros = desglose.stream()
                .filter(t -> !t.esEfectivo())
                .mapToLong(TotalPorMetodo::totalCentimos)
                .sum();

        List<VentaDeBarra> cobros = ventas.buscarPorArqueo(id);

        return ResponseEntity.ok(new FichaDeArqueo(
                ArqueoEnLista.de(arqueo),
                desglose,
                efectivo,
                otros,
                cobros.size(),
                // Los últimos, no todos: la ficha sirve para hacerse una idea,
                // no para auditar cobro a cobro.
                cobros.stream().limit(40).map(CobroEnLista::de).toList()));
    }
}
