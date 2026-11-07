package pe.ssiomama.backend.inventario;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.Usuario;

/**
 * Pedidos a proveedor y proveedores, de principio a fin.
 *
 * <h2>Recibir es lo que importa</h2>
 *
 * <p>Un pedido enviado no cambia nada en el almacén: la mercancía todavía no
 * está. Al <b>recibirlo</b>, cada línea se registra como una compra con
 * {@link GestionDeAlmacen#mover}, el mismo camino que una compra anotada a mano.
 * Así hay un solo sitio que mueve stock y apunta el coste, y el pedido deja
 * rastro en el histórico con su código como referencia.
 *
 * <p>Se recibe entero o nada, en una transacción: si una línea fallara a mitad,
 * la mitad del pedido estaría en el almacén y la otra no, y el recuento del día
 * siguiente no cuadraría con nada.
 */
@Service
public class GestionDeCompras {

    private final OrdenDeCompraRepository ordenes;
    private final ProveedorRepository proveedores;
    private final ProductoRepository productos;
    private final GestionDeAlmacen almacen;

    public GestionDeCompras(
            OrdenDeCompraRepository ordenes,
            ProveedorRepository proveedores,
            ProductoRepository productos,
            GestionDeAlmacen almacen) {
        this.ordenes = ordenes;
        this.proveedores = proveedores;
        this.productos = productos;
        this.almacen = almacen;
    }

    // ── Lo que llega del formulario ───────────────────────────────

    public record LineaDeFormulario(Long productoId, int cantidad, int costeUnitarioCentimos) {}

    public record DatosDeOrden(Long proveedorId, Instant esperadoEn, List<LineaDeFormulario> lineas) {}

    public record DatosDeProveedor(String nombre, String personaDeContacto, String email, String telefono) {}

    // ── Pedidos ───────────────────────────────────────────────────

    @Transactional
    public OrdenDeCompra crear(DatosDeOrden datos) {
        Proveedor proveedor = proveedor(datos.proveedorId());
        OrdenDeCompra orden = OrdenDeCompra.borrador(proveedor, datos.esperadoEn());
        orden.rehacer(proveedor, datos.esperadoEn(), lineas(datos.lineas()));
        return ordenes.save(orden);
    }

    /** Solo un borrador se edita: uno enviado es lo que el proveedor ya tiene en la mano. */
    @Transactional
    public OrdenDeCompra editar(Long id, DatosDeOrden datos) {
        OrdenDeCompra orden = orden(id);
        if (orden.getEstado() != EstadoDeOrden.BORRADOR) {
            throw new AlmacenRechazado("Solo se edita un pedido en borrador. Este ya está "
                    + orden.getEstado().name().toLowerCase() + ".");
        }
        orden.rehacer(proveedor(datos.proveedorId()), datos.esperadoEn(), lineas(datos.lineas()));
        return orden;
    }

    @Transactional
    public OrdenDeCompra enviar(Long id) {
        OrdenDeCompra orden = orden(id);
        if (orden.getEstado() != EstadoDeOrden.BORRADOR) {
            throw new AlmacenRechazado("Solo se envía un pedido en borrador.");
        }
        if (orden.getLineas().isEmpty()) {
            throw new AlmacenRechazado("El pedido no tiene líneas: añade algo antes de enviarlo.");
        }
        orden.enviar();
        return orden;
    }

    /**
     * La mercancía llegó: entra en el almacén línea a línea.
     *
     * <p>Se admite recibir también un borrador —el proveedor de la esquina trae
     * las cajas sin que nadie le mandara nada formal—, pero no uno cancelado ni
     * uno ya recibido, que metería la mercancía dos veces.
     */
    @Transactional
    public OrdenDeCompra recibir(Long id, Long salaId, Usuario quien) {
        OrdenDeCompra orden = orden(id);
        if (orden.getEstado() == EstadoDeOrden.RECIBIDO) {
            throw new AlmacenRechazado("Este pedido ya se recibió: su mercancía ya está en el almacén.");
        }
        if (orden.getEstado() == EstadoDeOrden.CANCELADO) {
            throw new AlmacenRechazado("Un pedido cancelado no se recibe.");
        }
        if (orden.getLineas().isEmpty()) {
            throw new AlmacenRechazado("El pedido no tiene líneas que recibir.");
        }

        for (LineaDeOrden l : orden.getLineas()) {
            almacen.mover(l.getProducto().getId(), new GestionDeAlmacen.DatosDeMovimiento(
                    TipoDeMovimiento.COMPRA,
                    salaId,
                    l.getCantidad(),
                    l.getCosteUnitarioCentimos(),
                    orden.getCodigo() + " · " + orden.getProveedor().getNombre()), quien);
        }
        orden.recibir(Instant.now());
        return orden;
    }

    @Transactional
    public OrdenDeCompra cancelar(Long id) {
        OrdenDeCompra orden = orden(id);
        if (orden.getEstado() == EstadoDeOrden.RECIBIDO) {
            throw new AlmacenRechazado("Un pedido recibido ya no se cancela: su mercancía está en el almacén. "
                    + "Si sobra algo, regístralo como ajuste.");
        }
        if (orden.getEstado() == EstadoDeOrden.CANCELADO) {
            throw new AlmacenRechazado("Ese pedido ya estaba cancelado.");
        }
        orden.cancelar();
        return orden;
    }

    // ── Proveedores ───────────────────────────────────────────────

    @Transactional
    public Proveedor crearProveedor(DatosDeProveedor datos) {
        comprobarNombreLibre(datos.nombre(), null);
        return proveedores.save(Proveedor.crear(
                datos.nombre(), datos.personaDeContacto(), datos.email(), datos.telefono()));
    }

    @Transactional
    public Proveedor editarProveedor(Long id, DatosDeProveedor datos) {
        Proveedor proveedor = proveedor(id);
        comprobarNombreLibre(datos.nombre(), id);
        proveedor.actualizar(datos.nombre(), datos.personaDeContacto(), datos.email(), datos.telefono());
        return proveedor;
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    private OrdenDeCompra orden(Long id) {
        return ordenes.buscarConLineas(id).orElseThrow(() -> new AlmacenRechazado("Ese pedido no existe."));
    }

    private Proveedor proveedor(Long id) {
        return (id == null ? java.util.Optional.<Proveedor>empty() : proveedores.findById(id))
                .orElseThrow(() -> new AlmacenRechazado("Ese proveedor no existe."));
    }

    private List<OrdenDeCompra.LineaPedida> lineas(List<LineaDeFormulario> pedidas) {
        List<OrdenDeCompra.LineaPedida> lineas = new ArrayList<>();
        List<Long> vistos = new ArrayList<>();
        for (LineaDeFormulario l : pedidas == null ? List.<LineaDeFormulario>of() : pedidas) {
            if (l.cantidad() < 1) throw new AlmacenRechazado("Cada línea pide al menos una unidad.");
            if (l.costeUnitarioCentimos() < 0) throw new AlmacenRechazado("El coste no puede ser negativo.");
            // El mismo producto dos veces es un error de tecleo: se pide sumando en una sola línea.
            if (vistos.contains(l.productoId())) {
                throw new AlmacenRechazado("Hay un producto repetido en el pedido: júntalo en una sola línea.");
            }
            vistos.add(l.productoId());
            Producto producto = productos.findById(l.productoId())
                    .orElseThrow(() -> new AlmacenRechazado("Alguno de los productos no existe."));
            lineas.add(new OrdenDeCompra.LineaPedida(producto, l.cantidad(), l.costeUnitarioCentimos()));
        }
        return lineas;
    }

    private void comprobarNombreLibre(String nombre, Long exceptoId) {
        proveedores.findAllByOrderByNombreAsc().stream()
                .filter(p -> p.getNombre().equalsIgnoreCase(nombre.trim()))
                .filter(p -> !p.getId().equals(exceptoId))
                .findFirst()
                .ifPresent(p -> {
                    throw new AlmacenRechazado("Ya hay un proveedor llamado «" + p.getNombre() + "».");
                });
    }
}
