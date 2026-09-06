package pe.ssiomama.backend.inventario;

import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.eventos.Sala;
import pe.ssiomama.backend.eventos.SalaRepository;

/**
 * Dar de alta productos y mover existencias a mano.
 *
 * <h2>Es la otra mitad de la barra</h2>
 *
 * <p>El punto de venta hace bajar el stock solo, pero nada lo subía. Sin esto, a
 * la tercera noche el inventario entero está en negativo y deja de servir para
 * lo único que sirve: decidir qué hay que pedir.
 *
 * <h2>Los tres tipos que se registran a mano no son lo mismo</h2>
 *
 * <ul>
 *   <li><b>Compra</b> — llegó mercancía. Suma, y lleva el precio al que se
 *       compró, que es lo que luego da el margen de verdad.
 *   <li><b>Merma</b> — se rompió, se caducó o se derramó. Resta, y hay que poder
 *       contarla aparte: una merma alta es un problema de manejo, no de ventas.
 *   <li><b>Ajuste</b> — el recuento no cuadra con el sistema. Puede ir en los dos
 *       sentidos, y es el único que admite un negativo directamente.
 * </ul>
 *
 * <p><b>{@code VENTA} no se acepta aquí.</b> Esa la escribe el punto de venta y
 * nadie más. Si se pudiera registrar a mano habría ventas sin dinero detrás, y
 * el arqueo de caja dejaría de cuadrar con el inventario — que es justo la
 * comprobación que hace útiles a los dos.
 */
@Service
public class GestionDeAlmacen {

    /** Lo que se puede registrar desde el panel. Ni venta ni traspaso. */
    private static final Set<TipoDeMovimiento> A_MANO =
            Set.of(TipoDeMovimiento.COMPRA, TipoDeMovimiento.MERMA, TipoDeMovimiento.AJUSTE);

    private final ProductoRepository productos;
    private final CategoriaRepository categorias;
    private final MovimientoRepository movimientos;
    private final SalaRepository salas;

    public GestionDeAlmacen(
            ProductoRepository productos,
            CategoriaRepository categorias,
            MovimientoRepository movimientos,
            SalaRepository salas) {
        this.productos = productos;
        this.categorias = categorias;
        this.movimientos = movimientos;
        this.salas = salas;
    }

    // ── Lo que llega del formulario ───────────────────────────────

    public record DatosDeProducto(
            String nombre,
            String categoriaId,
            UnidadDeProducto unidad,
            int costeCentimos,
            int precioCentimos,
            int alertaDeStock,
            boolean activo) {}

    /**
     * Un movimiento a mano.
     *
     * @param unidades tal y como las escribe la persona. En compra y merma
     *     piensa en «doce botellas» sin signo; en ajuste el signo sí importa.
     * @param costeUnitarioCentimos lo pagado por unidad. Solo se usa en compra.
     */
    public record DatosDeMovimiento(
            TipoDeMovimiento tipo,
            String salaId,
            int unidades,
            Integer costeUnitarioCentimos,
            String nota) {}

    /** Cómo queda el producto después de moverlo. */
    public record MovimientoHecho(
            String movimientoId, String producto, int cantidad, int stockResultante) {}

    // ── Productos ─────────────────────────────────────────────────

    /** Da de alta un producto, con su SKU generado y cero existencias. */
    @Transactional
    public Producto crear(DatosDeProducto datos) {
        comprobar(datos);

        CategoriaDeProducto categoria = categorias.findById(datos.categoriaId())
                .orElseThrow(() -> new AlmacenRechazado("Esa categoría no existe."));

        String sku = Skus.libre(datos.nombre(), candidato -> productos.existsBySku(candidato));

        return productos.save(Producto.crear(
                sku,
                datos.nombre().trim(),
                categoria,
                datos.unidad(),
                datos.costeCentimos(),
                datos.precioCentimos(),
                datos.alertaDeStock(),
                datos.activo()));
    }

    /** Cambia la ficha. El SKU y el stock no se tocan desde aquí. */
    @Transactional
    public Producto editar(String productoId, DatosDeProducto datos) {
        comprobar(datos);

        Producto producto = productos.findById(productoId)
                .orElseThrow(() -> new AlmacenRechazado("Ese producto no existe."));

        CategoriaDeProducto categoria = categorias.findById(datos.categoriaId())
                .orElseThrow(() -> new AlmacenRechazado("Esa categoría no existe."));

        producto.actualizar(
                datos.nombre().trim(),
                categoria,
                datos.unidad(),
                datos.costeCentimos(),
                datos.precioCentimos(),
                datos.alertaDeStock(),
                datos.activo());

        return producto;
    }

    // ── Movimientos ───────────────────────────────────────────────

    /**
     * Registra un movimiento y mueve el saldo.
     *
     * <p>Las dos cosas van juntas y en la misma transacción a propósito: el
     * stock es un saldo cacheado del histórico, y si se pudieran separar
     * acabarían diciendo cosas distintas.
     */
    @Transactional
    public MovimientoHecho mover(String productoId, DatosDeMovimiento datos, Usuario quien) {
        if (!A_MANO.contains(datos.tipo())) {
            // Un mensaje que explica el porqué, no un «tipo no válido».
            throw new AlmacenRechazado(
                    "Las ventas las registra la barra al cobrar, no se anotan a mano.");
        }
        if (datos.unidades() == 0) {
            throw new AlmacenRechazado("La cantidad no puede ser cero.");
        }

        Producto producto = productos.findById(productoId)
                .orElseThrow(() -> new AlmacenRechazado("Ese producto no existe."));

        Sala sala = salas.findById(datos.salaId())
                .orElseThrow(() -> new AlmacenRechazado("Esa sala no existe."));

        int conSigno = conSuSigno(datos.tipo(), datos.unidades());
        int coste = costeDe(datos, producto);

        var movimiento = movimientos.save(MovimientoDeInventario.aMano(
                producto, sala, datos.tipo(), conSigno, coste, datos.nota(), quien));

        producto.moverStock(conSigno);

        // Una compra actualiza el coste de referencia del producto. Va después
        // de crear el movimiento para que el histórico guarde lo que se pagó de
        // verdad esta vez, y la ficha lo que vale a partir de ahora.
        if (datos.tipo() == TipoDeMovimiento.COMPRA
                && datos.costeUnitarioCentimos() != null
                && datos.costeUnitarioCentimos() > 0) {
            producto.apuntarCosteDeCompra(datos.costeUnitarioCentimos());
        }

        return new MovimientoHecho(
                movimiento.getId(), producto.getNombre(), conSigno, producto.getStock());
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    /**
     * El signo lo pone el servidor, no quien rellena el formulario.
     *
     * <p>En compra y merma la persona escribe «12» y piensa en doce botellas;
     * que una sume y la otra reste es una regla del negocio, no algo que deba
     * recordar a las once de la noche. Solo el ajuste respeta el signo tal cual,
     * porque ahí el sentido es justamente lo que se está declarando.
     */
    private int conSuSigno(TipoDeMovimiento tipo, int unidades) {
        return switch (tipo) {
            case COMPRA -> Math.abs(unidades);
            case MERMA -> -Math.abs(unidades);
            default -> unidades;
        };
    }

    /**
     * Lo pagado por unidad.
     *
     * <p>En merma y ajuste va a cero, y no al coste del producto: no se pagó
     * nada por romper una botella. Meterle el coste de la ficha inflaría el
     * valor movido del histórico con dinero que nunca salió.
     */
    private int costeDe(DatosDeMovimiento datos, Producto producto) {
        if (datos.tipo() != TipoDeMovimiento.COMPRA) {
            return 0;
        }
        return datos.costeUnitarioCentimos() == null || datos.costeUnitarioCentimos() <= 0
                ? producto.getCosteCentimos()
                : datos.costeUnitarioCentimos();
    }

    private void comprobar(DatosDeProducto datos) {
        if (datos.costeCentimos() < 0 || datos.precioCentimos() < 0) {
            throw new AlmacenRechazado("Ni el coste ni el precio pueden ser negativos.");
        }
        if (datos.alertaDeStock() < 0) {
            throw new AlmacenRechazado("El umbral de reposición no puede ser negativo.");
        }
    }
}
