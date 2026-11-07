package pe.ssiomama.backend.inventario;

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
import pe.ssiomama.backend.auth.UsuarioRepository;
import pe.ssiomama.backend.inventario.GestionDeCompras.DatosDeOrden;
import pe.ssiomama.backend.inventario.GestionDeCompras.DatosDeProveedor;
import pe.ssiomama.backend.inventario.GestionDeCompras.LineaDeFormulario;

/**
 * Un pedido a proveedor de principio a fin, contra la base real: borrador,
 * enviado y recibido, y lo que recibir hace en el almacén.
 */
@SpringBootTest
@Transactional
class ComprasTest {

    @Autowired
    private GestionDeCompras compras;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    private Long proveedor() {
        return jdbc.queryForObject("select id from \"Proveedor\" order by nombre limit 1", Long.class);
    }

    private Long sala() {
        return jdbc.queryForObject("select id from \"Sala\" order by aforo desc limit 1", Long.class);
    }

    private List<Long> dosProductos() {
        return jdbc.queryForList("select id from \"Producto\" where activo order by sku limit 2", Long.class);
    }

    private int stock(Long productoId) {
        return jdbc.queryForObject("select stock from \"Producto\" where id = ?", Integer.class, productoId);
    }

    private OrdenDeCompra borrador() {
        List<Long> p = dosProductos();
        return compras.crear(new DatosDeOrden(proveedor(), null, List.of(
                new LineaDeFormulario(p.get(0), 12, 3500),
                new LineaDeFormulario(p.get(1), 24, 450))));
    }

    @Test
    @DisplayName("un pedido nace en borrador y su total es la suma de las líneas")
    void naceEnBorrador() {
        OrdenDeCompra orden = borrador();

        assertThat(orden.getEstado()).isEqualTo(EstadoDeOrden.BORRADOR);
        assertThat(orden.getTotalCentimos()).isEqualTo(12 * 3500 + 24 * 450);
        assertThat(orden.getCodigo()).matches("OC-[0-9]{4,}");
    }

    @Test
    @DisplayName("recibir mete la mercancía en el almacén, con el código del pedido en el histórico")
    void recibirSumaStock() {
        OrdenDeCompra orden = borrador();
        List<Long> p = dosProductos();
        em.flush();
        int antes0 = stock(p.get(0));
        int antes1 = stock(p.get(1));

        compras.enviar(orden.getId());
        compras.recibir(orden.getId(), sala(), usuarios.findByEmailIgnoreCase("admin@ssiomama.test").orElseThrow());
        em.flush();

        assertThat(stock(p.get(0))).isEqualTo(antes0 + 12);
        assertThat(stock(p.get(1))).isEqualTo(antes1 + 24);
        assertThat(jdbc.queryForObject("""
                select count(*) from "MovimientoStock"
                where tipo = 'COMPRA' and referencia like ? and "productoId" in (?, ?)
                """, Long.class, orden.getCodigo() + "%", p.get(0), p.get(1))).isEqualTo(2);
        assertThat(orden.getEstado()).isEqualTo(EstadoDeOrden.RECIBIDO);
        assertThat(orden.getRecibidoEn()).isNotNull();
    }

    @Test
    @DisplayName("un pedido recibido no se recibe otra vez ni se cancela")
    void recibidoEsFinal() {
        OrdenDeCompra orden = borrador();
        var admin = usuarios.findByEmailIgnoreCase("admin@ssiomama.test").orElseThrow();
        compras.recibir(orden.getId(), sala(), admin);

        assertThatThrownBy(() -> compras.recibir(orden.getId(), sala(), admin))
                .isInstanceOf(AlmacenRechazado.class).hasMessageContaining("ya se recibió");
        assertThatThrownBy(() -> compras.cancelar(orden.getId()))
                .isInstanceOf(AlmacenRechazado.class).hasMessageContaining("ajuste");
    }

    @Test
    @DisplayName("solo se edita un borrador: uno enviado es lo que tiene el proveedor")
    void soloSeEditaElBorrador() {
        OrdenDeCompra orden = borrador();
        List<Long> p = dosProductos();
        compras.editar(orden.getId(), new DatosDeOrden(proveedor(), null, List.of(new LineaDeFormulario(p.get(0), 6, 3500))));
        assertThat(orden.getTotalCentimos()).isEqualTo(6 * 3500);

        compras.enviar(orden.getId());
        assertThatThrownBy(() -> compras.editar(orden.getId(),
                new DatosDeOrden(proveedor(), null, List.of(new LineaDeFormulario(p.get(0), 1, 1)))))
                .isInstanceOf(AlmacenRechazado.class).hasMessageContaining("borrador");
    }

    @Test
    @DisplayName("el mismo producto dos veces se rechaza: es un error de tecleo")
    void productoRepetido() {
        Long p = dosProductos().get(0);
        assertThatThrownBy(() -> compras.crear(new DatosDeOrden(proveedor(), null, List.of(
                new LineaDeFormulario(p, 1, 100), new LineaDeFormulario(p, 2, 100)))))
                .isInstanceOf(AlmacenRechazado.class).hasMessageContaining("repetido");
    }

    @Test
    @DisplayName("un proveedor nuevo se da de alta y el nombre no se repite")
    void proveedores() {
        Proveedor nuevo = compras.crearProveedor(
                new DatosDeProveedor("Prueba Licores del Sur", "Ana", "ventas@licoresdelsur.pe", "054 123456"));
        assertThat(nuevo.getEmail()).isEqualTo("ventas@licoresdelsur.pe");

        assertThatThrownBy(() -> compras.crearProveedor(new DatosDeProveedor("prueba licores del sur", null, null, null)))
                .isInstanceOf(AlmacenRechazado.class).hasMessageContaining("Ya hay");
    }
}
