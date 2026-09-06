package pe.ssiomama.backend.inventario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.eventos.SalaRepository;

/**
 * Dar de alta productos y mover existencias desde el panel.
 *
 * <p>Va bajo {@code /api/inventario}, el mismo prefijo que ya sirve el catálogo
 * y el stock: es la misma sección vista por la misma gente, y lo que cambia es
 * el verbo.
 *
 * <p>El SKU no está en el formulario y no es un olvido: lo genera el servidor.
 * Es un campo único que nadie quiere teclear a las once de la noche, y pedirlo
 * garantizaba que se escribiera mal y que chocara.
 */
@RestController
@RequestMapping("/api/inventario")
public class AlmacenController {

    private final GestionDeAlmacen almacen;
    private final CategoriaRepository categorias;
    private final SalaRepository salas;

    public AlmacenController(
            GestionDeAlmacen almacen, CategoriaRepository categorias, SalaRepository salas) {
        this.almacen = almacen;
        this.categorias = categorias;
        this.salas = salas;
    }

    // ── Lo que entra ──────────────────────────────────────────────

    /**
     * La ficha de un producto.
     *
     * <p>El dinero llega en céntimos enteros, no en soles decimales: es como
     * viaja en todo el sistema, y convertir en un solo sitio —el navegador, al
     * pintarlo— evita que aparezcan totales de 330,00000000004.
     */
    public record FormularioDeProducto(
            @NotBlank(message = "Falta el nombre")
            @Size(min = 2, max = 120, message = "El nombre tiene entre 2 y 120 caracteres")
            String nombre,
            @NotBlank(message = "Elige una categoría") String categoriaId,
            @NotNull(message = "Falta la unidad") UnidadDeProducto unidad,
            @Min(value = 0, message = "El coste no puede ser negativo")
            @Max(value = 10_000_000, message = "Ese coste no parece real")
            int costeCentimos,
            @Min(value = 0, message = "El precio no puede ser negativo")
            @Max(value = 10_000_000, message = "Ese precio no parece real")
            int precioCentimos,
            @Min(value = 0, message = "El umbral no puede ser negativo")
            @Max(value = 10_000, message = "Ese umbral no parece real")
            int alertaDeStock,
            boolean activo) {}

    /**
     * Un movimiento a mano.
     *
     * @param unidades sin signo en compra y merma, con signo en ajuste. Quien lo
     *     resuelve es el servidor — ver {@code GestionDeAlmacen}.
     */
    public record FormularioDeMovimiento(
            @NotNull(message = "Falta el tipo de movimiento") TipoDeMovimiento tipo,
            @NotBlank(message = "Falta la sala") String salaId,
            @Min(value = -10_000, message = "Esa cantidad no parece real")
            @Max(value = 10_000, message = "Esa cantidad no parece real")
            int unidades,
            /** Lo pagado por unidad, en céntimos. Solo significa algo en una compra. */
            @Min(value = 0, message = "El coste no puede ser negativo")
            Integer costeUnitarioCentimos,
            @Size(max = 200, message = "La nota es demasiado larga") String nota) {}

    // ── Lo que sale ───────────────────────────────────────────────

    public record ProductoGuardado(
            String id, String sku, String nombre, int precioCentimos, boolean activo) {

        static ProductoGuardado de(Producto p) {
            return new ProductoGuardado(
                    p.getId(), p.getSku(), p.getNombre(), p.getPrecioCentimos(), p.isActivo());
        }
    }

    public record CategoriaParaElegir(String id, String nombre) {}

    public record SalaParaElegir(String id, String nombre) {}

    /** Lo que hace falta para rellenar los dos formularios. */
    public record OpcionesDeAlmacen(
            List<CategoriaParaElegir> categorias,
            List<SalaParaElegir> salas,
            List<UnidadDeProducto> unidades,
            /** Solo los que se registran a mano. La venta no está, y es a propósito. */
            List<TipoDeMovimiento> tiposDeMovimiento) {}

    // ── Endpoints ─────────────────────────────────────────────────

    @GetMapping("/opciones")
    @Transactional(readOnly = true)
    public OpcionesDeAlmacen opciones() {
        return new OpcionesDeAlmacen(
                categorias.findAllByOrderByNombreAsc().stream()
                        .map(c -> new CategoriaParaElegir(c.getId(), c.getNombre()))
                        .toList(),
                salas.findAllByOrderByAforoDesc().stream()
                        .map(s -> new SalaParaElegir(s.getId(), s.getNombre()))
                        .toList(),
                List.of(UnidadDeProducto.values()),
                List.of(TipoDeMovimiento.COMPRA, TipoDeMovimiento.MERMA, TipoDeMovimiento.AJUSTE));
    }

    @PostMapping("/productos")
    @Transactional
    public ResponseEntity<ProductoGuardado> crear(
            @Valid @RequestBody FormularioDeProducto formulario) {

        var producto = almacen.crear(aDatos(formulario));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductoGuardado.de(producto));
    }

    /**
     * Cambia la ficha de un producto.
     *
     * <p>{@code PUT} y no {@code PATCH} porque el formulario manda la ficha
     * entera: lo que llega es el estado completo que debe quedar. Con PATCH
     * habría que distinguir «no lo mandó» de «lo quiere a cero», que es como se
     * acaban poniendo precios a cero sin que nadie sepa por qué.
     */
    @PutMapping("/productos/{id}")
    @Transactional
    public ProductoGuardado editar(
            @PathVariable String id, @Valid @RequestBody FormularioDeProducto formulario) {
        return ProductoGuardado.de(almacen.editar(id, aDatos(formulario)));
    }

    /** Registra una compra, una merma o un ajuste, y mueve el saldo. */
    @PostMapping("/productos/{id}/movimientos")
    @Transactional
    public ResponseEntity<GestionDeAlmacen.MovimientoHecho> mover(
            @PathVariable String id,
            @Valid @RequestBody FormularioDeMovimiento formulario,
            @AuthenticationPrincipal Usuario quien) {

        var hecho = almacen.mover(
                id,
                new GestionDeAlmacen.DatosDeMovimiento(
                        formulario.tipo(),
                        formulario.salaId(),
                        formulario.unidades(),
                        formulario.costeUnitarioCentimos(),
                        formulario.nota()),
                quien);

        return ResponseEntity.status(HttpStatus.CREATED).body(hecho);
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    private GestionDeAlmacen.DatosDeProducto aDatos(FormularioDeProducto f) {
        return new GestionDeAlmacen.DatosDeProducto(
                f.nombre(),
                f.categoriaId(),
                f.unidad(),
                f.costeCentimos(),
                f.precioCentimos(),
                f.alertaDeStock(),
                f.activo());
    }
}
