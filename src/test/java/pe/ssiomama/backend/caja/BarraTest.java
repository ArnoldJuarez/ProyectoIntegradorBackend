package pe.ssiomama.backend.caja;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.auth.UsuarioRepository;
import pe.ssiomama.backend.caja.Barra.LineaPedida;
import pe.ssiomama.backend.inventario.ProductoRepository;

/**
 * La caja de la barra, contra los datos reales.
 *
 * <p>Toda la clase va en una transacción que se deshace al terminar: las cajas
 * que se abren y las ventas que se cobran aquí no quedan en la base, y el stock
 * que descuentan vuelve solo.
 */
@SpringBootTest
@Transactional
class BarraTest {

    @Autowired
    private Barra barra;

    @Autowired
    private ArqueoRepository arqueos;

    @Autowired
    private VentaDeBarraRepository ventas;

    @Autowired
    private ProductoRepository productos;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    // ── Apoyo ─────────────────────────────────────────────────────

    private Usuario deLaBarra() {
        return usuarios.findByEmailIgnoreCase("barra@ssiomama.test").orElseThrow();
    }

    private Usuario otraPersona() {
        return usuarios.findByEmailIgnoreCase("puerta@ssiomama.test").orElseThrow();
    }

    /** Un producto activo cualquiera, el primero por nombre. */
    private String unProducto() {
        return jdbc.queryForObject(
                "select id from \"Producto\" where activo = true order by nombre limit 1",
                String.class);
    }

    private String otroProducto() {
        return jdbc.queryForObject(
                "select id from \"Producto\" where activo = true order by nombre offset 1 limit 1",
                String.class);
    }

    /** Simula una petición nueva: sin esto, lo escrito no se relee de la base. */
    private void comoUnaPeticionNueva() {
        em.flush();
        em.clear();
    }

    // ── El turno ──────────────────────────────────────────────────

    @Test
    @DisplayName("abrir la caja deja el esperado igual al fondo")
    void abrirCaja() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);

        assertThat(turno.estaAbierta()).isTrue();
        // Al empezar, lo que debería haber en el cajón es lo que se metió.
        assertThat(turno.getEsperadoCentimos()).isEqualTo(20_000);
        assertThat(turno.getAperturaCentimos()).isEqualTo(20_000);
        assertThat(turno.getSala()).isNotNull();
    }

    /**
     * Volver a entrar es continuar el turno, no empezar otro. Sin esta regla,
     * cerrar la pestaña y volver abría una segunda caja y las ventas se
     * repartían entre las dos.
     */
    @Test
    @DisplayName("volver a abrir devuelve la caja que ya estaba, no una nueva")
    void volverAAbrirNoDuplica() {
        var primera = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();

        var segunda = barra.abrirTurno(deLaBarra(), 50_000);

        assertThat(segunda.getId()).isEqualTo(primera.getId());
        // Y el fondo del segundo intento se ignora: el turno ya tenía el suyo.
        assertThat(segunda.getAperturaCentimos()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("cerrar calcula el descuadre y lo deja congelado")
    void cerrarCalculaElDescuadre() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();

        // Faltan 50 céntimos en el cajón.
        var cerrada = barra.cerrarTurno(deLaBarra(), turno.getId(), 19_950);

        assertThat(cerrada.estaAbierta()).isFalse();
        assertThat(cerrada.getContadoCentimos()).isEqualTo(19_950);
        assertThat(cerrada.getDiferenciaCentimos()).isEqualTo(-50);
        assertThat(cerrada.cuadro()).isFalse();
        assertThat(cerrada.getCerradaEn()).isNotNull();
    }

    @Test
    @DisplayName("una caja cerrada no se cierra dos veces")
    void noSeCierraDosVeces() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();
        barra.cerrarTurno(deLaBarra(), turno.getId(), 20_000);
        comoUnaPeticionNueva();

        assertThatThrownBy(() -> barra.cerrarTurno(deLaBarra(), turno.getId(), 20_000))
                .isInstanceOf(CajaRechazada.class)
                .hasMessageContaining("ya estaba cerrada");
    }

    /** El cajón es de quien lo abrió: nadie cierra el turno de otro. */
    @Test
    @DisplayName("no se cierra la caja de otra persona")
    void cajaAjena() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();

        assertThatThrownBy(() -> barra.cerrarTurno(otraPersona(), turno.getId(), 20_000))
                .isInstanceOf(CajaRechazada.class)
                .hasMessageContaining("de otra persona");
    }

    // ── Cobrar ────────────────────────────────────────────────────

    /**
     * La regla que hace que el arqueo signifique algo: al cajón solo entra lo
     * que se paga en efectivo. Si la tarjeta sumara, el arqueo saldría siempre
     * corto por lo cobrado por otras vías.
     */
    @Test
    @DisplayName("el efectivo sube el cajon y la tarjeta no")
    void soloElEfectivoLlegaAlCajon() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();
        String producto = unProducto();

        var enEfectivo = barra.cobrar(
                deLaBarra(), turno.getId(), MetodoDePago.EFECTIVO, null,
                List.of(new LineaPedida(producto, 2)));
        comoUnaPeticionNueva();

        assertThat(enEfectivo.esperadoEnCajon()).isEqualTo(20_000 + enEfectivo.totalCentimos());

        var conTarjeta = barra.cobrar(
                deLaBarra(), turno.getId(), MetodoDePago.TARJETA, null,
                List.of(new LineaPedida(producto, 3)));
        comoUnaPeticionNueva();

        // La tarjeta no toca el cajón: el esperado sigue donde lo dejó el efectivo.
        assertThat(conTarjeta.esperadoEnCajon()).isEqualTo(20_000 + enEfectivo.totalCentimos());
    }

    @Test
    @DisplayName("cobrar descuenta el stock y deja su movimiento en negativo")
    void cobrarDescuentaYDejaRastro() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();

        String producto = unProducto();
        int stockAntes = productos.findById(producto).orElseThrow().getStock();

        var hecha = barra.cobrar(
                deLaBarra(), turno.getId(), MetodoDePago.EFECTIVO, null,
                List.of(new LineaPedida(producto, 4)));
        comoUnaPeticionNueva();

        assertThat(productos.findById(producto).orElseThrow().getStock())
                .isEqualTo(stockAntes - 4);

        // El movimiento explica la bajada, y su cantidad lleva el signo.
        var movimiento = jdbc.queryForMap(
                """
                select cantidad, tipo::text as tipo, referencia, "usuarioId"
                from "MovimientoStock" where referencia = ?
                """,
                "Venta " + hecha.id());

        assertThat(((Number) movimiento.get("cantidad")).intValue()).isEqualTo(-4);
        assertThat(movimiento.get("tipo")).isEqualTo("VENTA");
        assertThat(movimiento.get("usuarioId")).isEqualTo(deLaBarra().getId());
    }

    /**
     * En una barra se toca el mismo botón cinco veces seguidas. Si llegaran
     * cinco líneas del mismo producto se harían cinco descuentos separados;
     * sumadas, es un solo movimiento y el inventario queda legible.
     */
    @Test
    @DisplayName("las lineas repetidas se juntan en un solo movimiento")
    void lineasRepetidas() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();

        String producto = unProducto();
        int stockAntes = productos.findById(producto).orElseThrow().getStock();

        var hecha = barra.cobrar(
                deLaBarra(), turno.getId(), MetodoDePago.EFECTIVO, null,
                List.of(
                        new LineaPedida(producto, 1),
                        new LineaPedida(producto, 1),
                        new LineaPedida(producto, 1)));
        comoUnaPeticionNueva();

        assertThat(hecha.unidades()).isEqualTo(3);
        assertThat(productos.findById(producto).orElseThrow().getStock()).isEqualTo(stockAntes - 3);

        int movimientos = jdbc.queryForObject(
                "select count(*) from \"MovimientoStock\" where referencia = ?",
                Integer.class,
                "Venta " + hecha.id());
        assertThat(movimientos).as("un movimiento por producto, no uno por pulsación").isEqualTo(1);

        // Y una sola línea en el ticket, con la cantidad sumada.
        var venta = ventas.findById(hecha.id()).orElseThrow();
        assertThat(venta.getLineas()).hasSize(1);
        assertThat(venta.getLineas().getFirst().getCantidad()).isEqualTo(3);
    }

    /**
     * La decisión que más se discute de este archivo: el stock puede quedar
     * negativo y la venta sigue adelante. A las dos de la mañana la botella está
     * ahí aunque el sistema crea que no, y negarse a cobrar por un descuadre de
     * papel es perder dinero de verdad.
     */
    @Test
    @DisplayName("sin stock se cobra igual y el saldo queda en negativo")
    void sinStockSeCobraIgual() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();

        String producto = unProducto();
        jdbc.update("update \"Producto\" set stock = 0 where id = ?", producto);
        comoUnaPeticionNueva();

        var hecha = barra.cobrar(
                deLaBarra(), turno.getId(), MetodoDePago.EFECTIVO, null,
                List.of(new LineaPedida(producto, 3)));
        comoUnaPeticionNueva();

        assertThat(hecha.unidades()).isEqualTo(3);
        assertThat(productos.findById(producto).orElseThrow().getStock())
                .as("el negativo queda a la vista, que es donde se arregla")
                .isEqualTo(-3);
    }

    @Test
    @DisplayName("el precio se congela en la linea al cobrarla")
    void elPrecioSeCongela() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();

        String producto = unProducto();
        int precio = productos.findById(producto).orElseThrow().getPrecioCentimos();

        var hecha = barra.cobrar(
                deLaBarra(), turno.getId(), MetodoDePago.EFECTIVO, null,
                List.of(new LineaPedida(producto, 2)));
        comoUnaPeticionNueva();

        assertThat(hecha.totalCentimos()).isEqualTo(precio * 2);

        // Sube el precio después: el ticket de antes no cambia.
        jdbc.update("update \"Producto\" set \"precioCentimos\" = ? where id = ?", precio * 2, producto);
        comoUnaPeticionNueva();

        var venta = ventas.findById(hecha.id()).orElseThrow();
        assertThat(venta.getLineas().getFirst().getPrecioUnitarioCentimos()).isEqualTo(precio);
        assertThat(venta.getTotalCentimos()).isEqualTo(precio * 2);
    }

    @Test
    @DisplayName("varios productos en una venta suman bien")
    void variosProductos() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();

        String uno = unProducto();
        String otro = otroProducto();
        int precioUno = productos.findById(uno).orElseThrow().getPrecioCentimos();
        int precioOtro = productos.findById(otro).orElseThrow().getPrecioCentimos();

        var hecha = barra.cobrar(
                deLaBarra(), turno.getId(), MetodoDePago.EFECTIVO, null,
                List.of(new LineaPedida(uno, 2), new LineaPedida(otro, 1)));

        assertThat(hecha.totalCentimos()).isEqualTo(precioUno * 2 + precioOtro);
        assertThat(hecha.unidades()).isEqualTo(3);
    }

    // ── Lo que no se puede cobrar ─────────────────────────────────

    @Test
    @DisplayName("con la caja cerrada no se cobra")
    void cajaCerrada() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();
        barra.cerrarTurno(deLaBarra(), turno.getId(), 20_000);
        comoUnaPeticionNueva();

        assertThatThrownBy(() -> barra.cobrar(
                        deLaBarra(), turno.getId(), MetodoDePago.EFECTIVO, null,
                        List.of(new LineaPedida(unProducto(), 1))))
                .isInstanceOf(CajaRechazada.class)
                .hasMessageContaining("cerrada");
    }

    @Test
    @DisplayName("no se cobra en la caja de otra persona")
    void cobrarEnCajaAjena() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();

        assertThatThrownBy(() -> barra.cobrar(
                        otraPersona(), turno.getId(), MetodoDePago.EFECTIVO, null,
                        List.of(new LineaPedida(unProducto(), 1))))
                .isInstanceOf(CajaRechazada.class)
                .hasMessageContaining("de otra persona");
    }

    @Test
    @DisplayName("una venta vacia no es una venta")
    void ventaVacia() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();

        assertThatThrownBy(() -> barra.cobrar(
                        deLaBarra(), turno.getId(), MetodoDePago.EFECTIVO, null, List.of()))
                .isInstanceOf(CajaRechazada.class);
    }

    @Test
    @DisplayName("un producto que no existe corta la venta entera")
    void productoInventado() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();

        String bueno = unProducto();
        int stockAntes = productos.findById(bueno).orElseThrow().getStock();

        assertThatThrownBy(() -> barra.cobrar(
                        deLaBarra(), turno.getId(), MetodoDePago.EFECTIVO, null,
                        List.of(new LineaPedida(bueno, 1), new LineaPedida("no-existe", 1))))
                .isInstanceOf(CajaRechazada.class);

        comoUnaPeticionNueva();
        assertThat(productos.findById(bueno).orElseThrow().getStock())
                .as("la línea buena no debe haber descontado nada")
                .isEqualTo(stockAntes);
    }

    @Test
    @DisplayName("una caja que no existe se rechaza con su mensaje")
    void cajaInventada() {
        assertThatThrownBy(() -> barra.cobrar(
                        deLaBarra(), "no-existe", MetodoDePago.EFECTIVO, null,
                        List.of(new LineaPedida(unProducto(), 1))))
                .isInstanceOf(CajaRechazada.class)
                .hasMessageContaining("no existe");
    }

    // ── El arqueo cuadra ──────────────────────────────────────────

    /**
     * La cuenta entera de un turno: fondo más lo cobrado en efectivo, y ni un
     * céntimo de lo cobrado por otras vías.
     */
    @Test
    @DisplayName("al cerrar, el esperado es el fondo mas el efectivo cobrado")
    void laCuentaDelTurno() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();

        String producto = unProducto();
        var efectivo1 = barra.cobrar(
                deLaBarra(), turno.getId(), MetodoDePago.EFECTIVO, null,
                List.of(new LineaPedida(producto, 2)));
        comoUnaPeticionNueva();
        var conYape = barra.cobrar(
                deLaBarra(), turno.getId(), MetodoDePago.YAPE, null,
                List.of(new LineaPedida(producto, 5)));
        comoUnaPeticionNueva();
        var efectivo2 = barra.cobrar(
                deLaBarra(), turno.getId(), MetodoDePago.EFECTIVO, null,
                List.of(new LineaPedida(producto, 1)));
        comoUnaPeticionNueva();

        int esperado = 20_000 + efectivo1.totalCentimos() + efectivo2.totalCentimos();

        var cerrada = barra.cerrarTurno(deLaBarra(), turno.getId(), esperado);

        assertThat(cerrada.getEsperadoCentimos()).isEqualTo(esperado);
        assertThat(cerrada.cuadro()).as("contando justo lo esperado, la caja cuadra").isTrue();
        // El Yape se cobró pero no está en el cajón: va al banco.
        assertThat(conYape.totalCentimos()).isPositive();
        assertThat(cerrada.getEsperadoCentimos()).isLessThan(
                esperado + conYape.totalCentimos());
    }

    @Test
    @DisplayName("las ventas cuelgan del turno de quien las cobro")
    void lasVentasCuelganDelTurno() {
        var turno = barra.abrirTurno(deLaBarra(), 20_000);
        comoUnaPeticionNueva();

        var hecha = barra.cobrar(
                deLaBarra(), turno.getId(), MetodoDePago.EFECTIVO, null,
                List.of(new LineaPedida(unProducto(), 1)));
        comoUnaPeticionNueva();

        var venta = ventas.findById(hecha.id()).orElseThrow();
        assertThat(venta.getArqueo().getId()).isEqualTo(turno.getId());
        assertThat(arqueos.findById(turno.getId()).orElseThrow().getResponsable().getId())
                .isEqualTo(deLaBarra().getId());
    }
}
