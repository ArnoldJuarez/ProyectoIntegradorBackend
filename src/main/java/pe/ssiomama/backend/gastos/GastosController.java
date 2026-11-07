package pe.ssiomama.backend.gastos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.eventos.EventoRepository;

/**
 * Los gastos de la casa, mes a mes.
 *
 * <p>El mes se cuenta en hora de Lima: un gasto apuntado el 31 a las 23:00 es
 * de ese mes, aunque en UTC ya sea el día 1 del siguiente.
 */
@RestController
@RequestMapping("/api/gastos")
public class GastosController {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final GastoRepository gastos;
    private final CategoriaDeGastoRepository categorias;
    private final EventoRepository eventos;
    private final GestionDeGastos gestion;

    public GastosController(
            GastoRepository gastos,
            CategoriaDeGastoRepository categorias,
            EventoRepository eventos,
            GestionDeGastos gestion) {
        this.gastos = gastos;
        this.categorias = categorias;
        this.eventos = eventos;
        this.gestion = gestion;
    }

    public record FormularioDeGasto(
            @NotNull(message = "Elige la categoría") Long categoriaId,
            String eventoSlug,
            @Min(value = 1, message = "El importe tiene que ser mayor que cero")
            @Max(value = 100_000_000, message = "Ese importe no parece real")
            int importeCentimos,
            @Size(max = 300, message = "La descripción es demasiado larga") String descripcion,
            Instant fecha) {}

    public record NuevaCategoria(
            @NotBlank(message = "Falta el nombre") @Size(min = 2, max = 60, message = "Entre 2 y 60 caracteres")
            String nombre) {}

    public record GastoEnLista(
            Long id,
            Instant fecha,
            Long categoriaId,
            String categoria,
            int importeCentimos,
            String descripcion,
            String noche,
            String nocheSlug,
            String apuntadoPor) {

        static GastoEnLista de(Gasto g) {
            return new GastoEnLista(
                    g.getId(),
                    g.getFecha(),
                    g.getCategoria().getId(),
                    g.getCategoria().getNombre(),
                    g.getImporteCentimos(),
                    g.getDescripcion(),
                    g.getEvento() == null ? null : g.getEvento().getTitulo(),
                    g.getEvento() == null ? null : g.getEvento().getSlug(),
                    g.getApuntadoPor() == null ? null : g.getApuntadoPor().getNombre());
        }
    }

    public record TotalDeCategoria(String categoria, long totalCentimos, int gastos) {}

    public record GastosDelMes(
            String mes, long totalCentimos, List<TotalDeCategoria> porCategoria, List<GastoEnLista> gastos) {}

    public record CategoriaParaElegir(Long id, String nombre, boolean activa) {}

    public record NocheParaElegir(String slug, String titulo, Instant empiezaA) {}

    public record OpcionesDeGasto(List<CategoriaParaElegir> categorias, List<NocheParaElegir> noches) {}

    /** Los gastos de un mes ({@code 2026-10}); sin mes, el actual. */
    @GetMapping
    @Transactional(readOnly = true)
    public GastosDelMes delMes(@RequestParam(required = false) String mes) {
        YearMonth m;
        try {
            m = mes == null || mes.isBlank() ? YearMonth.now(LIMA) : YearMonth.parse(mes);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mes va como 2026-10.");
        }
        Instant desde = m.atDay(1).atStartOfDay(LIMA).toInstant();
        Instant hasta = m.plusMonths(1).atDay(1).atStartOfDay(LIMA).toInstant();

        List<GastoEnLista> filas = gastos.buscarEntre(desde, hasta).stream().map(GastoEnLista::de).toList();
        Map<String, List<GastoEnLista>> agrupados = filas.stream()
                .collect(Collectors.groupingBy(GastoEnLista::categoria, LinkedHashMap::new, Collectors.toList()));
        List<TotalDeCategoria> porCategoria = agrupados.entrySet().stream()
                .map(e -> new TotalDeCategoria(e.getKey(),
                        e.getValue().stream().mapToLong(GastoEnLista::importeCentimos).sum(), e.getValue().size()))
                .sorted(Comparator.comparingLong(TotalDeCategoria::totalCentimos).reversed())
                .toList();

        return new GastosDelMes(m.toString(), filas.stream().mapToLong(GastoEnLista::importeCentimos).sum(),
                porCategoria, filas);
    }

    /** Las categorías y las noches recientes y próximas, para el formulario. */
    @GetMapping("/opciones")
    @Transactional(readOnly = true)
    public OpcionesDeGasto opciones() {
        Instant ahora = Instant.now();
        Instant desde = ahora.minus(Duration.ofDays(60));
        Instant hasta = ahora.plus(Duration.ofDays(45));
        return new OpcionesDeGasto(
                categorias.findAllByOrderByNombreAsc().stream()
                        .map(c -> new CategoriaParaElegir(c.getId(), c.getNombre(), c.isActiva()))
                        .toList(),
                eventos.buscarTodosConDetalle().stream()
                        .filter(e -> e.getEmpiezaA().isAfter(desde) && e.getEmpiezaA().isBefore(hasta))
                        .map(e -> new NocheParaElegir(e.getSlug(), e.getTitulo(), e.getEmpiezaA()))
                        .toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<GastoEnLista> apuntar(
            @Valid @RequestBody FormularioDeGasto f, @AuthenticationPrincipal Usuario quien) {
        return ResponseEntity.status(HttpStatus.CREATED).body(GastoEnLista.de(gestion.apuntar(aDatos(f), quien)));
    }

    @PutMapping("/{id}")
    @Transactional
    public GastoEnLista corregir(@PathVariable Long id, @Valid @RequestBody FormularioDeGasto f) {
        return GastoEnLista.de(gestion.corregir(id, aDatos(f)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> borrar(@PathVariable Long id) {
        gestion.borrar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/categorias")
    @Transactional
    public ResponseEntity<CategoriaParaElegir> crearCategoria(@Valid @RequestBody NuevaCategoria n) {
        CategoriaDeGasto c = gestion.crearCategoria(n.nombre());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CategoriaParaElegir(c.getId(), c.getNombre(), c.isActiva()));
    }

    private static GestionDeGastos.DatosDeGasto aDatos(FormularioDeGasto f) {
        return new GestionDeGastos.DatosDeGasto(
                f.categoriaId(), f.eventoSlug(), f.importeCentimos(), f.descripcion(), f.fecha());
    }
}
