package pe.ssiomama.backend.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.auth.UsuarioRepository;
import pe.ssiomama.backend.eventos.SalaRepository;
import pe.ssiomama.backend.inventario.GestionDeAlmacen.DatosDeMovimiento;
import pe.ssiomama.backend.inventario.GestionDeAlmacen.DatosDeProducto;

/**
 * El almacén: alta de productos y movimientos a mano.
 *
 * <p>Toda la clase va en una transacción que se deshace al terminar: los
 * productos que se dan de alta aquí y el stock que mueven no quedan en la base.
 */
@SpringBootTest
@Transactional
class GestionDeAlmacenTest {

    @Autowired
    private GestionDeAlmacen almacen;

    @Autowired
    private ProductoRepository productos;

    @Autowired
    private CategoriaRepository categorias;

    @Autowired
    private SalaRepository salas;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    // ── Apoyo ─────────────────────────────────────────────────────

    private Usuario direccion() {
        return usuarios.findByEmailIgnoreCase("admin@ssiomama.test").orElseThrow();
    }

    private Long unaCategoria() {
        return categorias.findAllByOrderByNombreAsc().getFirst().getId();
    }

    private Long unaSala() {
        return salas.findAllByOrderByAforoDesc().getFirst().getId();
    }

    private Long unProducto() {
        return jdbc.queryForObject(
                "select id from \"Producto\" where activo = true order by nombre limit 1",
                Long.class);
    }

    private DatosDeProducto ficha(String nombre) {
        return new DatosDeProducto(
                nombre, unaCategoria(), UnidadDeProducto.BOTELLA, 800, 1500, 6, true);
    }

    private DatosDeMovimiento movimiento(TipoDeMovimiento tipo, int unidades, Integer coste) {
        return new DatosDeMovimiento(tipo, unaSala(), unidades, coste, "Prueba");
    }

    private void comoUnaPeticionNueva() {
        em.flush();
        em.clear();
    }

    // ── Alta de productos ─────────────────────────────────────────

    /**
     * Un producto nace sin existencias. Meter mercancía es una compra, y tiene
     * que serlo: si el alta pudiera fijar el stock, habría unidades sin ninguna
     * fila que explique de dónde salieron.
     */
    @Test
    @DisplayName("un producto nuevo nace con cero unidades y su sku generado")
    void altaDeProducto() {
        var creado = almacen.crear(ficha("Cerveza de Prueba"));

        assertThat(creado.getStock()).isZero();
        assertThat(creado.getSku()).startsWith("CERVEZA-DE-PRUEBA-");
        assertThat(creado.getNombre()).isEqualTo("Cerveza de Prueba");
        assertThat(creado.getPrecioCentimos()).isEqualTo(1500);
        assertThat(creado.isActivo()).isTrue();
    }

    @Test
    @DisplayName("dos productos con el mismo nombre no chocan de sku")
    void skuRepetido() {
        var uno = almacen.crear(ficha("Cerveza de Prueba"));
        comoUnaPeticionNueva();
        var otro = almacen.crear(ficha("Cerveza de Prueba"));

        assertThat(uno.getSku()).isNotEqualTo(otro.getSku());
    }

    /**
     * El SKU puede estar impreso en una etiqueta de almacén, y el stock es un
     * saldo que solo mueven los movimientos: ninguno de los dos se toca al
     * editar la ficha.
     */
    @Test
    @DisplayName("editar no toca ni el sku ni el stock")
    void editarNoTocaSkuNiStock() {
        var creado = almacen.crear(ficha("Producto Editable"));
        Long id = creado.getId();
        String sku = creado.getSku();
        comoUnaPeticionNueva();

        almacen.mover(id, movimiento(TipoDeMovimiento.COMPRA, 20, 900), direccion());
        comoUnaPeticionNueva();

        var editado = almacen.editar(
                id,
                new DatosDeProducto(
                        "Producto Renombrado", unaCategoria(), UnidadDeProducto.CAJA,
                        1000, 2000, 12, false));
        comoUnaPeticionNueva();

        assertThat(editado.getSku()).isEqualTo(sku);
        assertThat(editado.getStock()).isEqualTo(20);
        assertThat(editado.getNombre()).isEqualTo("Producto Renombrado");
        assertThat(editado.getUnidad()).isEqualTo(UnidadDeProducto.CAJA);
        assertThat(editado.isActivo()).isFalse();
    }

    @Test
    @DisplayName("una categoria inventada se rechaza")
    void categoriaInventada() {
        assertThatThrownBy(() -> almacen.crear(new DatosDeProducto(
                        "Sin Categoría", 999_999_999L, UnidadDeProducto.UNIDAD, 100, 200, 5, true)))
                .isInstanceOf(AlmacenRechazado.class)
                .hasMessageContaining("categoría no existe");
    }

    // ── Movimientos ───────────────────────────────────────────────

    @Test
    @DisplayName("una compra suma y actualiza el coste de referencia")
    void compra() {
        Long id = unProducto();
        int stockAntes = productos.findById(id).orElseThrow().getStock();

        var hecho = almacen.mover(id, movimiento(TipoDeMovimiento.COMPRA, 24, 950), direccion());
        comoUnaPeticionNueva();

        assertThat(hecho.cantidad()).isEqualTo(24);
        assertThat(hecho.stockResultante()).isEqualTo(stockAntes + 24);

        var producto = productos.findById(id).orElseThrow();
        assertThat(producto.getStock()).isEqualTo(stockAntes + 24);
        // Si el proveedor sube el precio y el coste guardado se queda en el
        // viejo, el margen que calcula el panel se infla solo.
        assertThat(producto.getCosteCentimos()).isEqualTo(950);
    }

    /**
     * En una merma la persona escribe «3» pensando en tres botellas rotas. Que
     * eso reste es una regla del negocio, no algo que deba recordar a las once
     * de la noche.
     */
    @Test
    @DisplayName("una merma resta aunque se escriba en positivo")
    void mermaEnPositivo() {
        Long id = unProducto();
        int stockAntes = productos.findById(id).orElseThrow().getStock();

        var hecho = almacen.mover(id, movimiento(TipoDeMovimiento.MERMA, 3, null), direccion());
        comoUnaPeticionNueva();

        assertThat(hecho.cantidad()).isEqualTo(-3);
        assertThat(productos.findById(id).orElseThrow().getStock()).isEqualTo(stockAntes - 3);
    }

    @Test
    @DisplayName("un ajuste respeta el signo que se escribe")
    void ajusteConSigno() {
        Long id = unProducto();
        int stockAntes = productos.findById(id).orElseThrow().getStock();

        almacen.mover(id, movimiento(TipoDeMovimiento.AJUSTE, -5, null), direccion());
        comoUnaPeticionNueva();
        assertThat(productos.findById(id).orElseThrow().getStock()).isEqualTo(stockAntes - 5);

        almacen.mover(id, movimiento(TipoDeMovimiento.AJUSTE, 8, null), direccion());
        comoUnaPeticionNueva();
        assertThat(productos.findById(id).orElseThrow().getStock()).isEqualTo(stockAntes - 5 + 8);
    }

    /**
     * No se pagó nada por romper una botella. Meterle el coste de la ficha
     * inflaría el valor movido del histórico con dinero que nunca salió.
     */
    @Test
    @DisplayName("merma y ajuste no llevan coste")
    void sinCosteEnMermaYAjuste() {
        Long id = unProducto();

        var merma = almacen.mover(id, movimiento(TipoDeMovimiento.MERMA, 2, null), direccion());
        comoUnaPeticionNueva();

        int coste = jdbc.queryForObject(
                "select \"costeUnitarioCentimos\" from \"MovimientoStock\" where id = ?",
                Integer.class,
                merma.movimientoId());

        assertThat(coste).isZero();
    }

    @Test
    @DisplayName("una compra sin coste usa el de la ficha y no lo cambia")
    void compraSinCoste() {
        Long id = unProducto();
        int costeAntes = productos.findById(id).orElseThrow().getCosteCentimos();

        var hecho = almacen.mover(id, movimiento(TipoDeMovimiento.COMPRA, 10, null), direccion());
        comoUnaPeticionNueva();

        int enElMovimiento = jdbc.queryForObject(
                "select \"costeUnitarioCentimos\" from \"MovimientoStock\" where id = ?",
                Integer.class,
                hecho.movimientoId());

        assertThat(enElMovimiento).isEqualTo(costeAntes);
        assertThat(productos.findById(id).orElseThrow().getCosteCentimos()).isEqualTo(costeAntes);
    }

    @Test
    @DisplayName("el movimiento queda firmado por quien lo hizo")
    void quedaFirmado() {
        Long id = unProducto();

        var hecho = almacen.mover(id, movimiento(TipoDeMovimiento.COMPRA, 5, 700), direccion());
        comoUnaPeticionNueva();

        var fila = jdbc.queryForMap(
                """
                select "usuarioId", referencia, "salaId", tipo::text as tipo
                from "MovimientoStock" where id = ?
                """,
                hecho.movimientoId());

        assertThat(fila.get("usuarioId")).isEqualTo(direccion().getId());
        assertThat(fila.get("referencia")).isEqualTo("Prueba");
        assertThat(fila.get("salaId")).isEqualTo(unaSala());
        assertThat(fila.get("tipo")).isEqualTo("COMPRA");
    }

    // ── Lo que no se puede hacer ──────────────────────────────────

    /**
     * La comprobación que de verdad importa de este archivo: una venta la
     * escribe el punto de venta al cobrar. Si se pudiera anotar a mano habría
     * ventas sin dinero detrás, y el arqueo dejaría de cuadrar con el almacén.
     */
    @Test
    @DisplayName("una venta no se puede registrar a mano")
    void ventaAMano() {
        assertThatThrownBy(() -> almacen.mover(
                        unProducto(), movimiento(TipoDeMovimiento.VENTA, 3, null), direccion()))
                .isInstanceOf(AlmacenRechazado.class)
                .hasMessageContaining("las registra la barra");
    }

    @Test
    @DisplayName("un traspaso tampoco, todavia no esta migrado")
    void traspasoAMano() {
        assertThatThrownBy(() -> almacen.mover(
                        unProducto(),
                        movimiento(TipoDeMovimiento.TRANSFERENCIA, 3, null),
                        direccion()))
                .isInstanceOf(AlmacenRechazado.class);
    }

    @Test
    @DisplayName("mover cero no es mover")
    void cantidadCero() {
        assertThatThrownBy(() -> almacen.mover(
                        unProducto(), movimiento(TipoDeMovimiento.AJUSTE, 0, null), direccion()))
                .isInstanceOf(AlmacenRechazado.class)
                .hasMessageContaining("cero");
    }

    @Test
    @DisplayName("un producto que no existe se rechaza")
    void productoInventado() {
        assertThatThrownBy(() -> almacen.mover(
                        999_999_999L, movimiento(TipoDeMovimiento.COMPRA, 1, null), direccion()))
                .isInstanceOf(AlmacenRechazado.class)
                .hasMessageContaining("producto no existe");
    }

    /**
     * El stock puede quedar negativo: es la misma decisión que en la barra. Un
     * ajuste que lo deja bajo cero es información —hay un descuadre— y esconderlo
     * no lo arregla.
     */
    @Test
    @DisplayName("un ajuste puede dejar el stock en negativo")
    void ajusteANegativo() {
        var creado = almacen.crear(ficha("Producto sin Stock"));
        comoUnaPeticionNueva();

        var hecho = almacen.mover(
                creado.getId(), movimiento(TipoDeMovimiento.AJUSTE, -4, null), direccion());

        assertThat(hecho.stockResultante()).isEqualTo(-4);
    }
}
