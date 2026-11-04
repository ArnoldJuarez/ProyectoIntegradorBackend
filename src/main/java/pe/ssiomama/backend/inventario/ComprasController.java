package pe.ssiomama.backend.inventario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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
import pe.ssiomama.backend.inventario.GestionDeCompras.DatosDeOrden;
import pe.ssiomama.backend.inventario.GestionDeCompras.DatosDeProveedor;
import pe.ssiomama.backend.inventario.GestionDeCompras.LineaDeFormulario;
import pe.ssiomama.backend.inventario.InventarioController.ProveedorEnLista;

/**
 * Pedidos a proveedor y alta de proveedores, desde el panel.
 *
 * <p>Los pedidos que deja el agente de inventario al aprobarlo aparecen aquí
 * como cualquier otro borrador: se revisan, se ajustan y se envían a mano. La IA
 * prepara el pedido; quien llama al proveedor sigue siendo una persona.
 */
@RestController
@RequestMapping("/api/inventario")
public class ComprasController {

    private final OrdenDeCompraRepository ordenes;
    private final GestionDeCompras gestion;

    public ComprasController(OrdenDeCompraRepository ordenes, GestionDeCompras gestion) {
        this.ordenes = ordenes;
        this.gestion = gestion;
    }

    // ── Lo que entra ──────────────────────────────────────────────

    public record LineaDelFormulario(
            @NotBlank(message = "Falta el producto de una línea") String productoId,
            @Min(value = 1, message = "Cada línea pide al menos una unidad")
            @Max(value = 100_000, message = "Esa cantidad no parece real")
            int cantidad,
            @Min(value = 0, message = "El coste no puede ser negativo")
            @Max(value = 10_000_000, message = "Ese coste no parece real")
            int costeUnitarioCentimos) {}

    public record FormularioDeOrden(
            @NotBlank(message = "Elige un proveedor") String proveedorId,
            Instant esperadoEn,
            @NotEmpty(message = "El pedido necesita al menos una línea")
            @Size(max = 80, message = "Demasiadas líneas para un solo pedido")
            List<@Valid LineaDelFormulario> lineas) {}

    /** Recibir: a qué sala entra la mercancía. */
    public record Recepcion(@NotBlank(message = "Elige dónde entra la mercancía") String salaId) {}

    public record FormularioDeProveedor(
            @NotBlank(message = "Falta el nombre")
            @Size(min = 2, max = 120, message = "El nombre tiene entre 2 y 120 caracteres")
            String nombre,
            @Size(max = 120) String personaDeContacto,
            @Email(message = "El correo no es válido") @Size(max = 160) String email,
            @Size(max = 30, message = "Ese teléfono es demasiado largo") String telefono) {}

    // ── Lo que sale ───────────────────────────────────────────────

    public record OrdenEnLista(
            String id,
            String codigo,
            String proveedor,
            EstadoDeOrden estado,
            int totalCentimos,
            int lineas,
            Instant creadoEn,
            Instant esperadoEn,
            Instant recibidoEn) {

        static OrdenEnLista de(OrdenDeCompra o) {
            return new OrdenEnLista(o.getId(), o.getCodigo(), o.getProveedor().getNombre(), o.getEstado(),
                    o.getTotalCentimos(), o.getLineas().size(), o.getCreadoEn(), o.getEsperadoEn(), o.getRecibidoEn());
        }
    }

    public record LineaEnFicha(
            String productoId, String sku, String producto, int cantidad, int costeUnitarioCentimos, long importeCentimos) {}

    public record FichaDeOrden(
            String id,
            String codigo,
            String proveedorId,
            String proveedor,
            EstadoDeOrden estado,
            int totalCentimos,
            Instant creadoEn,
            Instant esperadoEn,
            Instant recibidoEn,
            List<LineaEnFicha> lineas) {

        static FichaDeOrden de(OrdenDeCompra o) {
            return new FichaDeOrden(o.getId(), o.getCodigo(), o.getProveedor().getId(), o.getProveedor().getNombre(),
                    o.getEstado(), o.getTotalCentimos(), o.getCreadoEn(), o.getEsperadoEn(), o.getRecibidoEn(),
                    o.getLineas().stream()
                            .map(l -> new LineaEnFicha(l.getProducto().getId(), l.getProducto().getSku(),
                                    l.getProducto().getNombre(), l.getCantidad(), l.getCosteUnitarioCentimos(),
                                    l.importeCentimos()))
                            .toList());
        }
    }

    // ── Pedidos ───────────────────────────────────────────────────

    @GetMapping("/ordenes")
    @Transactional(readOnly = true)
    public List<OrdenEnLista> ordenes() {
        return ordenes.buscarTodos().stream().map(OrdenEnLista::de).toList();
    }

    @GetMapping("/ordenes/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<FichaDeOrden> ficha(@PathVariable String id) {
        return ordenes.buscarConLineas(id)
                .map(FichaDeOrden::de)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/ordenes")
    @Transactional
    public ResponseEntity<FichaDeOrden> crear(@Valid @RequestBody FormularioDeOrden f) {
        return ResponseEntity.status(HttpStatus.CREATED).body(FichaDeOrden.de(gestion.crear(aDatos(f))));
    }

    @PutMapping("/ordenes/{id}")
    @Transactional
    public FichaDeOrden editar(@PathVariable String id, @Valid @RequestBody FormularioDeOrden f) {
        return FichaDeOrden.de(gestion.editar(id, aDatos(f)));
    }

    @PostMapping("/ordenes/{id}/enviar")
    @Transactional
    public FichaDeOrden enviar(@PathVariable String id) {
        return FichaDeOrden.de(gestion.enviar(id));
    }

    @PostMapping("/ordenes/{id}/recibir")
    @Transactional
    public FichaDeOrden recibir(
            @PathVariable String id, @Valid @RequestBody Recepcion r, @AuthenticationPrincipal Usuario quien) {
        return FichaDeOrden.de(gestion.recibir(id, r.salaId(), quien));
    }

    @PostMapping("/ordenes/{id}/cancelar")
    @Transactional
    public FichaDeOrden cancelar(@PathVariable String id) {
        return FichaDeOrden.de(gestion.cancelar(id));
    }

    // ── Proveedores ───────────────────────────────────────────────

    @PostMapping("/proveedores")
    @Transactional
    public ResponseEntity<ProveedorEnLista> crearProveedor(@Valid @RequestBody FormularioDeProveedor f) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProveedorEnLista.de(gestion.crearProveedor(aDatos(f))));
    }

    @PutMapping("/proveedores/{id}")
    @Transactional
    public ProveedorEnLista editarProveedor(@PathVariable String id, @Valid @RequestBody FormularioDeProveedor f) {
        return ProveedorEnLista.de(gestion.editarProveedor(id, aDatos(f)));
    }

    private static DatosDeOrden aDatos(FormularioDeOrden f) {
        return new DatosDeOrden(f.proveedorId(), f.esperadoEn(), f.lineas().stream()
                .map(l -> new LineaDeFormulario(l.productoId(), l.cantidad(), l.costeUnitarioCentimos()))
                .toList());
    }

    private static DatosDeProveedor aDatos(FormularioDeProveedor f) {
        return new DatosDeProveedor(f.nombre(), f.personaDeContacto(), f.email(), f.telefono());
    }
}
