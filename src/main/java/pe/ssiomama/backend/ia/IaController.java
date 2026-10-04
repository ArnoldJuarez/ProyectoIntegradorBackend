package pe.ssiomama.backend.ia;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.ssiomama.backend.auth.Usuario;

/**
 * La capa de IA vista desde el panel: lanzar agentes, revisar lo que proponen y
 * ver lo que cuesta.
 *
 * <p>Las corridas del banco de pruebas quedan fuera de todo lo que se ve aquí:
 * son laboratorio, y mezclarlas inflaría el coste del negocio.
 */
@RestController
@RequestMapping("/api/ia")
public class IaController {

    private static final Logger log = LoggerFactory.getLogger(IaController.class);

    private final Agentes agentes;
    private final RegistroDeIa registro;
    private final AplicadorDePropuestas aplicador;
    private final MotoresDeIa motores;

    public IaController(
            Agentes agentes, RegistroDeIa registro, AplicadorDePropuestas aplicador, MotoresDeIa motores) {
        this.agentes = agentes;
        this.registro = registro;
        this.aplicador = aplicador;
        this.motores = motores;
    }

    // ── Agentes ───────────────────────────────────────────────────

    public record AgenteEnLista(String clave, String nombre, String descripcion, String modelo) {}

    @GetMapping("/agentes")
    public List<AgenteEnLista> agentes() {
        return agentes.todos().stream()
                .map(d -> new AgenteEnLista(
                        d.clave().etiqueta(),
                        d.clave().nombre(),
                        d.descripcion(),
                        (d.prosa() ? motores.prosa() : motores.numeros()).modelo()))
                .toList();
    }

    /** Lanza un agente. Tarda lo que tarde el modelo: de segundos a un minuto. */
    @PostMapping("/agentes/{clave}")
    public Agentes.Resultado ejecutar(@PathVariable String clave, @AuthenticationPrincipal Usuario quien) {
        ClaveDeAgente c = ClaveDeAgente.deEtiqueta(clave)
                .filter(k -> k != ClaveDeAgente.CONSERJE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el agente «" + clave + "»."));
        return agentes.ejecutar(c, Disparador.MANUAL, quien.getId());
    }

    // ── Propuestas ────────────────────────────────────────────────

    public record PropuestaEnLista(
            String id,
            String tipo,
            String tipoNombre,
            String agente,
            String estado,
            JsonNode datos,
            Instant creadoEn,
            String revisadaPor,
            boolean aplicada,
            int costeCentimos,
            int tokens) {

        static PropuestaEnLista de(RegistroDeIa.Propuesta p) {
            return new PropuestaEnLista(
                    p.id(),
                    p.tipo().name(),
                    p.tipo().nombre(),
                    ClaveDeAgente.deEtiqueta(p.agente()).map(ClaveDeAgente::nombre).orElse(p.agente()),
                    p.estado(),
                    p.datos(),
                    p.creadoEn(),
                    p.revisadaPor(),
                    p.aplicadaEn() != null,
                    p.costeCentimos(),
                    p.tokens());
        }
    }

    public record Bandeja(
            long pendientes, long ejecuciones, long costeCentimos, long tokens, List<PropuestaEnLista> propuestas) {}

    @GetMapping("/propuestas")
    public Bandeja propuestas() {
        List<RegistroDeIa.Ejecucion> operacion = deOperacion(registro.ejecuciones());
        List<PropuestaEnLista> propuestas = registro.propuestas(30).stream().map(PropuestaEnLista::de).toList();
        return new Bandeja(
                propuestas.stream().filter(p -> p.estado().equals("PENDIENTE")).count(),
                operacion.size(),
                operacion.stream().mapToLong(RegistroDeIa.Ejecucion::costeCentimos).sum(),
                operacion.stream().mapToLong(e -> e.tokensEntrada() + e.tokensSalida()).sum(),
                propuestas);
    }

    public enum Decision {
        APROBAR,
        DESCARTAR
    }

    public record DecisionSobrePropuesta(@NotNull(message = "Falta la decisión") Decision decision) {}

    public record Desenlace(String estado, boolean aplicada, String mensaje, List<String> avisos) {}

    /**
     * Aprobar o descartar. Es la única puerta por la que lo que produce la IA
     * llega a tener efecto.
     *
     * <p>Se marca aprobada <b>antes</b> de ejecutar: si la ejecución falla, la
     * decisión humana queda registrada igual y el fallo se ve, en vez de
     * perderse.
     */
    @PostMapping("/propuestas/{id}")
    public Desenlace decidir(
            @PathVariable String id,
            @Valid @RequestBody DecisionSobrePropuesta cuerpo,
            @AuthenticationPrincipal Usuario quien) {

        var propuesta = registro.propuesta(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos esa propuesta."));

        boolean aprobar = cuerpo.decision() == Decision.APROBAR;
        if (!registro.revisar(id, aprobar ? "APROBADA" : "RECHAZADA", quien.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa propuesta ya estaba revisada.");
        }
        if (!aprobar) {
            return new Desenlace("RECHAZADA", false, "Propuesta descartada.", List.of());
        }

        try {
            var r = aplicador.aplicar(propuesta.tipo(), propuesta.datos(), propuesta.ejecucionId());
            if (r.aplicada()) registro.marcarAplicada(id);
            return new Desenlace("APROBADA", r.aplicada(), r.resumen(), r.avisos());
        } catch (RuntimeException e) {
            log.error("No se pudo aplicar la propuesta {}", id, e);
            return new Desenlace("APROBADA", false,
                    "Aprobada, pero no se ha podido ejecutar. Revísalo a mano.", List.of(String.valueOf(e.getMessage())));
        }
    }

    // ── Métricas ──────────────────────────────────────────────────

    public record FilaDeAgente(String agente, int veces, int exitoPorcentaje, String mediana, String tokensMedios) {}

    public record FilaDeModelo(String modelo, int corridas, int exitoPorcentaje, String mediana, String intentosMedios) {}

    public record EjecucionReciente(
            String id, String agente, String modelo, String lanzadaPor, Instant cuando, String estado, int propuestas) {}

    public record Metricas(
            int ejecuciones,
            Integer tasaDeAceptacion,
            int aprobadas,
            int revisadas,
            int pendientes,
            long costeCentimos,
            long tokens,
            Integer minutosHastaDecidir,
            List<FilaDeAgente> porAgente,
            List<FilaDeModelo> bancoDePruebas,
            List<EjecucionReciente> ultimas) {}

    private static final Map<String, String> DISPARADOR = Map.of(
            "manual", "Manual", "scheduled", "Programada", "chat", "Chat público", "benchmark", "Banco de pruebas");

    @GetMapping("/metricas")
    public Metricas metricas() {
        List<RegistroDeIa.Ejecucion> todas = registro.ejecuciones();
        List<RegistroDeIa.Ejecucion> operacion = deOperacion(todas);
        List<RegistroDeIa.Ejecucion> banco = todas.stream().filter(e -> e.disparador().equals("benchmark")).toList();
        List<RegistroDeIa.Propuesta> propuestas = registro.propuestas(10_000);

        List<RegistroDeIa.Propuesta> revisadas = propuestas.stream().filter(p -> !p.estado().equals("PENDIENTE")).toList();
        long aprobadas = revisadas.stream().filter(p -> p.estado().equals("APROBADA")).count();
        // Cuánto tarda una persona en decidir. Si sube, la propuesta no se
        // entiende de un vistazo y hay que revisar cómo se presenta.
        Double minutos = mediana(revisadas.stream()
                .filter(p -> p.revisadaEn() != null)
                .map(p -> Duration.between(p.creadoEn(), p.revisadaEn()).toSeconds() / 60.0)
                .toList());

        List<FilaDeAgente> porAgente = agrupar(operacion, RegistroDeIa.Ejecucion::agente).entrySet().stream()
                .map(g -> new FilaDeAgente(
                        ClaveDeAgente.deEtiqueta(g.getKey()).map(ClaveDeAgente::nombre).orElse(g.getKey()),
                        g.getValue().size(), exito(g.getValue()), medianaDeTiempo(g.getValue()), tokensMedios(g.getValue())))
                .toList();
        List<FilaDeModelo> porModelo = agrupar(banco, e -> e.modelo() == null ? "?" : e.modelo()).entrySet().stream()
                .map(g -> new FilaDeModelo(g.getKey(), g.getValue().size(), exito(g.getValue()),
                        medianaDeTiempo(g.getValue()), intentosMedios(g.getValue())))
                .toList();
        List<EjecucionReciente> ultimas = operacion.stream().limit(12)
                .map(e -> new EjecucionReciente(
                        e.id(),
                        ClaveDeAgente.deEtiqueta(e.agente()).map(ClaveDeAgente::nombre).orElse(e.agente()),
                        e.modelo(),
                        e.lanzadaPor() != null ? e.lanzadaPor() : DISPARADOR.getOrDefault(e.disparador(), "—"),
                        e.iniciadaEn(),
                        e.estado(),
                        e.propuestas()))
                .toList();

        return new Metricas(
                operacion.size(),
                revisadas.isEmpty() ? null : (int) Math.round(aprobadas * 100.0 / revisadas.size()),
                (int) aprobadas,
                revisadas.size(),
                propuestas.size() - revisadas.size(),
                operacion.stream().mapToLong(RegistroDeIa.Ejecucion::costeCentimos).sum(),
                operacion.stream().mapToLong(e -> e.tokensEntrada() + e.tokensSalida()).sum(),
                minutos == null ? null : (int) Math.round(minutos),
                porAgente,
                porModelo,
                ultimas);
    }

    // ── Agregación ────────────────────────────────────────────────

    private static List<RegistroDeIa.Ejecucion> deOperacion(List<RegistroDeIa.Ejecucion> todas) {
        return todas.stream().filter(e -> !e.disparador().equals("benchmark")).toList();
    }

    /** Agrupado y ordenado de más a menos frecuente. */
    private static Map<String, List<RegistroDeIa.Ejecucion>> agrupar(
            List<RegistroDeIa.Ejecucion> filas, Function<RegistroDeIa.Ejecucion, String> clave) {
        return filas.stream().collect(Collectors.groupingBy(clave)).entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<RegistroDeIa.Ejecucion>> e) -> e.getValue().size()).reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static List<RegistroDeIa.Ejecucion> completadas(List<RegistroDeIa.Ejecucion> filas) {
        return filas.stream().filter(f -> f.estado().equals("COMPLETADA")).toList();
    }

    private static int exito(List<RegistroDeIa.Ejecucion> filas) {
        return (int) Math.round(completadas(filas).size() * 100.0 / filas.size());
    }

    /** Mediana y no media: una sola corrida lenta desplaza la media entera. */
    private static String medianaDeTiempo(List<RegistroDeIa.Ejecucion> filas) {
        Double m = mediana(completadas(filas).stream()
                .filter(f -> f.terminadaEn() != null)
                .map(f -> Duration.between(f.iniciadaEn(), f.terminadaEn()).toMillis() / 1000.0)
                .toList());
        if (m == null) return "—";
        return m >= 60 ? String.format("%.1f min", m / 60) : String.format("%.1f s", m);
    }

    private static String tokensMedios(List<RegistroDeIa.Ejecucion> filas) {
        var ok = completadas(filas);
        if (ok.isEmpty()) return "—";
        return String.valueOf(Math.round(ok.stream().mapToInt(f -> f.tokensEntrada() + f.tokensSalida()).average().orElse(0)));
    }

    private static String intentosMedios(List<RegistroDeIa.Ejecucion> filas) {
        var ok = completadas(filas);
        if (ok.isEmpty()) return "—";
        return String.format("%.2f", ok.stream().mapToInt(RegistroDeIa.Ejecucion::intentos).average().orElse(0));
    }

    private static Double mediana(List<Double> valores) {
        if (valores.isEmpty()) return null;
        List<Double> orden = new ArrayList<>(valores);
        orden.sort(Double::compare);
        int centro = orden.size() / 2;
        return orden.size() % 2 == 1 ? orden.get(centro) : (orden.get(centro - 1) + orden.get(centro)) / 2;
    }
}
