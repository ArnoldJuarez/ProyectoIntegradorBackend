package pe.ssiomama.backend.entradas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.caja.MetodoDePago;
import pe.ssiomama.backend.clientes.ClienteRepository;
import pe.ssiomama.backend.clientes.FidelidadRepository;
import pe.ssiomama.backend.entradas.Compra.Comprador;
import pe.ssiomama.backend.entradas.Compra.Peticion;
import pe.ssiomama.backend.eventos.EventoRepository;
import pe.ssiomama.backend.eventos.TipoDeEntradaRepository;

/**
 * La compra de entradas, contra los datos reales.
 *
 * <p>Toda la clase va en una transacción que se deshace al terminar: los pedidos
 * que se crean aquí no quedan en la base, y el cupo que apartan vuelve solo.
 *
 * <p>Lo que <b>no</b> prueba este archivo es la concurrencia: dentro de una
 * transacción de test no hay dos transacciones que se pisen. De eso se ocupa
 * {@code CompraConcurrenteTest}, que escribe de verdad.
 */
@SpringBootTest
@Transactional
class CompraTest {

    /**
     * La noche contra la que se compra.
     *
     * <p>Sirve cualquiera del seed <b>porque el reloj lo pone la prueba</b>: el
     * servicio recibe el instante como parámetro y aquí se le pasa un momento
     * dentro de la ventana de venta de esa noche — ver {@link #mientrasSeVende}.
     *
     * <p>Antes se usaba {@code Instant.now()} y la suite tenía fecha de
     * caducidad: el día que el calendario pasó esta noche, su venta se cerró
     * sola y diecisiete pruebas se pusieron en rojo sin que nadie tocara el
     * código.
     */
    private static final String NOCHE = "viernes-urbano";

    /** Otra distinta, para el intento de mezclar tipos de dos eventos. */
    private static final String OTRA_NOCHE = "sabado-de-show";

    /** Ya pasó y está cerrada. */
    private static final String NOCHE_CERRADA = "aniversario-ssiomama";

    /** Prefijo propio para no pisar los correos de otros tests. */
    private static final String CORREO = "compra-%s@ejemplo.test";

    @Autowired
    private Compra compra;

    @Autowired
    private TipoDeEntradaRepository tipos;

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private PedidoRepository pedidos;

    @Autowired
    private EntradaRepository entradas;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private FidelidadRepository fidelidad;

    @Autowired
    private FirmaDeEntrada firma;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    // ── Apoyo ─────────────────────────────────────────────────────

    /** El id de un tipo de entrada de esa noche, por su nombre. */
    private String idDeTipo(String slug, String nombre) {
        return jdbc.queryForObject(
                """
                select t.id from "TipoEntrada" t
                join "Evento" v on v.id = t."eventoId"
                where v.slug = ? and t.nombre = ?
                """,
                String.class,
                slug,
                nombre);
    }

    /**
     * Un instante en el que esa noche todavía se vende.
     *
     * <p>La venta se corta al empezar la noche, así que una hora antes siempre
     * está dentro. Al fijarlo desde la prueba, el resultado no cambia por mucho
     * que pase el tiempo real.
     */
    private Instant mientrasSeVende(String slug) {
        return eventos.findBySlug(slug).orElseThrow().getEmpiezaA().minus(Duration.ofHours(1));
    }

    private Comprador alguienNuevo() {
        return new Comprador(
                "Ana", "Prueba", CORREO.formatted(System.nanoTime()), "999888777");
    }

    /** Simula una petición nueva: sin esto, lo escrito no se relee de la base. */
    private void comoUnaPeticionNueva() {
        em.flush();
        em.clear();
    }

    // ── Abrir el pedido ───────────────────────────────────────────

    /**
     * Lo esencial: el cupo se aparta al abrir el pedido, no al pagarlo. Si se
     * esperara al pago, dos personas podrían llegar a la pasarela con la última
     * entrada.
     */
    @Test
    @DisplayName("abrir un pedido aparta el cupo, antes de cobrar nada")
    void abrirApartaElCupo() {
        String general = idDeTipo(NOCHE, "General");
        int vendidasAntes = tipos.findById(general).orElseThrow().getVendidas();

        var abierto = compra.abrir(
                NOCHE, alguienNuevo(), List.of(new Peticion(general, 3)), mientrasSeVende(NOCHE));

        comoUnaPeticionNueva();

        assertThat(tipos.findById(general).orElseThrow().getVendidas())
                .as("el cupo se aparta con el pedido pendiente")
                .isEqualTo(vendidasAntes + 3);

        // Y el pedido queda pendiente, no pagado.
        var pedido = pedidos.buscarPorCodigoConLineas(abierto.codigo()).orElseThrow();
        assertThat(pedido.getEstado()).isEqualTo(EstadoDePedido.PENDIENTE);
        assertThat(pedido.getEntradas()).as("todavía no hay entradas emitidas").isEmpty();
    }

    /**
     * El precio no llega desde el navegador: se lee de la base. Es lo que evita
     * que alguien compre una VIP por un sol editando la petición.
     */
    @Test
    @DisplayName("el precio y la comision los calcula el servidor")
    void elPrecioLoPoneElServidor() {
        String vip = idDeTipo(NOCHE, "VIP");
        int precio = tipos.findById(vip).orElseThrow().getPrecioCentimos();

        var abierto = compra.abrir(
                NOCHE, alguienNuevo(), List.of(new Peticion(vip, 2)), mientrasSeVende(NOCHE));

        int subtotalEsperado = precio * 2;
        assertThat(abierto.subtotalCentimos()).isEqualTo(subtotalEsperado);
        assertThat(abierto.comisionCentimos())
                .isEqualTo(Tarifas.comisionDeGestion(subtotalEsperado));
        assertThat(abierto.totalCentimos())
                .isEqualTo(abierto.subtotalCentimos() + abierto.comisionCentimos());
        assertThat(abierto.entradas()).isEqualTo(2);
    }

    @Test
    @DisplayName("varios tipos en un mismo pedido suman bien")
    void variosTipos() {
        String general = idDeTipo(NOCHE, "General");
        String vip = idDeTipo(NOCHE, "VIP");
        int pGeneral = tipos.findById(general).orElseThrow().getPrecioCentimos();
        int pVip = tipos.findById(vip).orElseThrow().getPrecioCentimos();

        var abierto = compra.abrir(
                NOCHE,
                alguienNuevo(),
                List.of(new Peticion(general, 2), new Peticion(vip, 1)),
                mientrasSeVende(NOCHE));

        assertThat(abierto.subtotalCentimos()).isEqualTo(pGeneral * 2 + pVip);
        assertThat(abierto.entradas()).isEqualTo(3);
    }

    // ── Lo que no se puede comprar ────────────────────────────────

    @Test
    @DisplayName("no se venden mas entradas de las que quedan")
    void noSeVendeDeMas() {
        String vip = idDeTipo(NOCHE, "VIP");
        // Se deja el tipo con dos disponibles. Por SQL porque el panel es quien
        // ajusta aforos, y esta entidad no expone ese cambio.
        jdbc.update("update \"TipoEntrada\" set cantidad = vendidas + 2 where id = ?", vip);
        comoUnaPeticionNueva();

        assertThatThrownBy(() -> compra.abrir(
                        NOCHE, alguienNuevo(), List.of(new Peticion(vip, 4)), mientrasSeVende(NOCHE)))
                .isInstanceOf(CompraRechazada.class)
                // El mensaje dice cuántas quedan: «no disponible» a secas hace
                // que la persona lo intente otra vez con lo mismo.
                .hasMessageContaining("Solo quedan 2");
    }

    @Test
    @DisplayName("agotado se dice agotado, no «quedan 0»")
    void agotado() {
        String vip = idDeTipo(NOCHE, "VIP");
        jdbc.update("update \"TipoEntrada\" set cantidad = vendidas where id = ?", vip);
        comoUnaPeticionNueva();

        assertThatThrownBy(() -> compra.abrir(
                        NOCHE, alguienNuevo(), List.of(new Peticion(vip, 1)), mientrasSeVende(NOCHE)))
                .isInstanceOf(CompraRechazada.class)
                .hasMessageContaining("agotado");
    }

    @Test
    @DisplayName("se respeta el maximo por pedido")
    void maximoPorPedido() {
        String general = idDeTipo(NOCHE, "General");
        int maximo = tipos.findById(general).orElseThrow().getMaximoPorPedido();

        assertThatThrownBy(() -> compra.abrir(
                        NOCHE,
                        alguienNuevo(),
                        List.of(new Peticion(general, maximo + 1)),
                        mientrasSeVende(NOCHE)))
                .isInstanceOf(CompraRechazada.class)
                .hasMessageContaining("Máximo %d".formatted(maximo));
    }

    @Test
    @DisplayName("una noche que no esta a la venta no vende")
    void nocheCerrada() {
        String general = idDeTipo(NOCHE_CERRADA, "General");

        assertThatThrownBy(() -> compra.abrir(
                        NOCHE_CERRADA, alguienNuevo(), List.of(new Peticion(general, 1)),
                        mientrasSeVende(NOCHE)))
                .isInstanceOf(CompraRechazada.class)
                .hasMessageContaining("no está a la venta");
    }

    /**
     * El intento que de verdad importa: pedir la noche cara con el id de un tipo
     * de entrada barato de otra noche. Sin el filtro por evento, se compraría
     * una VIP al precio de una General.
     */
    @Test
    @DisplayName("un tipo de entrada de otro evento no cuela")
    void tipoDeOtroEvento() {
        String deLaOtraNoche = idDeTipo(OTRA_NOCHE, "General");

        assertThatThrownBy(() -> compra.abrir(
                        NOCHE, alguienNuevo(), List.of(new Peticion(deLaOtraNoche, 1)),
                        mientrasSeVende(NOCHE)))
                .isInstanceOf(CompraRechazada.class)
                .hasMessageContaining("ya no está disponible");
    }

    @Test
    @DisplayName("un pedido sin lineas no es un pedido")
    void sinLineas() {
        assertThatThrownBy(() -> compra.abrir(NOCHE, alguienNuevo(), List.of(), mientrasSeVende(NOCHE)))
                .isInstanceOf(CompraRechazada.class);
    }

    /** Un fallo a mitad no puede dejar cupo apartado de las líneas buenas. */
    @Test
    @DisplayName("si una linea falla, ninguna aparta cupo")
    void falloParcialNoApartaNada() {
        String general = idDeTipo(NOCHE, "General");
        String vip = idDeTipo(NOCHE, "VIP");
        jdbc.update("update \"TipoEntrada\" set cantidad = vendidas where id = ?", vip);
        comoUnaPeticionNueva();

        int generalAntes = tipos.findById(general).orElseThrow().getVendidas();

        assertThatThrownBy(() -> compra.abrir(
                        NOCHE,
                        alguienNuevo(),
                        List.of(new Peticion(general, 2), new Peticion(vip, 1)),
                        mientrasSeVende(NOCHE)))
                .isInstanceOf(CompraRechazada.class);

        assertThat(tipos.findById(general).orElseThrow().getVendidas())
                .as("la línea buena no debe haber apartado nada")
                .isEqualTo(generalAntes);
    }

    // ── El comprador ──────────────────────────────────────────────

    @Test
    @DisplayName("quien vuelve a comprar no se duplica como cliente")
    void elClienteNoSeDuplica() {
        String general = idDeTipo(NOCHE, "General");
        Comprador ana = alguienNuevo();

        compra.abrir(NOCHE, ana, List.of(new Peticion(general, 1)), mientrasSeVende(NOCHE));
        comoUnaPeticionNueva();
        compra.abrir(NOCHE, ana, List.of(new Peticion(general, 1)), mientrasSeVende(NOCHE));
        comoUnaPeticionNueva();

        long cuantos = jdbc.queryForObject(
                "select count(*) from \"Cliente\" where lower(correo) = ?",
                Long.class,
                ana.email().toLowerCase());

        assertThat(cuantos).isEqualTo(1);
    }

    // ── Pagar ─────────────────────────────────────────────────────

    /**
     * Lo que se emite al pagar tiene que servir en la puerta. Por eso el test no
     * se conforma con contar entradas: comprueba que la firma del QR se valida
     * con el mismo componente que usa el escáner.
     */
    @Test
    @DisplayName("pagar emite las entradas y sus QR son validos")
    void pagarEmiteEntradasValidas() {
        String general = idDeTipo(NOCHE, "General");
        var abierto = compra.abrir(
                NOCHE, alguienNuevo(), List.of(new Peticion(general, 2)), mientrasSeVende(NOCHE));
        comoUnaPeticionNueva();

        var hecho = compra.confirmarPago(abierto.codigo(), MetodoDePago.TARJETA, Instant.now());
        comoUnaPeticionNueva();

        assertThat(hecho.entradasEmitidas()).isEqualTo(2);
        assertThat(hecho.yaEstabaPagado()).isFalse();

        var emitidas = entradas.buscarPorCodigoDePedido(abierto.codigo());
        assertThat(emitidas).hasSize(2);
        assertThat(emitidas).allSatisfy(e -> {
            assertThat(e.getEstado()).isEqualTo(EstadoDeEntrada.VALIDA);
            assertThat(e.getTitular()).isNotBlank();
            // Y lo importante: el escáner sabrá leerlo.
            assertThat(firma.decodificar(e.getCodigo()))
                    .as("el QR emitido tiene que validar en la puerta")
                    .isEqualTo(e.getId());
        });
    }

    /**
     * Volver atrás en el navegador y darle otra vez a pagar no debería emitir el
     * doble de entradas ni parecer un error.
     */
    @Test
    @DisplayName("pagar dos veces no emite el doble")
    void pagarDosVeces() {
        String general = idDeTipo(NOCHE, "General");
        var abierto = compra.abrir(
                NOCHE, alguienNuevo(), List.of(new Peticion(general, 2)), mientrasSeVende(NOCHE));
        comoUnaPeticionNueva();

        compra.confirmarPago(abierto.codigo(), MetodoDePago.TARJETA, Instant.now());
        comoUnaPeticionNueva();

        var segunda = compra.confirmarPago(abierto.codigo(), MetodoDePago.TARJETA, Instant.now());
        comoUnaPeticionNueva();

        assertThat(segunda.yaEstabaPagado()).isTrue();
        assertThat(entradas.buscarPorCodigoDePedido(abierto.codigo())).hasSize(2);
    }

    @Test
    @DisplayName("pagar actualiza la ficha del cliente y le abona sus puntos")
    void pagarActualizaAlCliente() {
        String general = idDeTipo(NOCHE, "General");
        Comprador ana = alguienNuevo();

        var abierto = compra.abrir(NOCHE, ana, List.of(new Peticion(general, 2)), mientrasSeVende(NOCHE));
        comoUnaPeticionNueva();

        var antes = clientes.findByEmailIgnoreCase(ana.email()).orElseThrow();
        int visitasAntes = antes.getVisitas();
        int gastadoAntes = antes.getGastadoCentimos();

        compra.confirmarPago(abierto.codigo(), MetodoDePago.TARJETA, Instant.now());
        comoUnaPeticionNueva();

        var despues = clientes.findByEmailIgnoreCase(ana.email()).orElseThrow();
        assertThat(despues.getVisitas()).isEqualTo(visitasAntes + 1);
        assertThat(despues.getGastadoCentimos())
                .isEqualTo(gastadoAntes + abierto.totalCentimos());
        assertThat(despues.getUltimaVisita()).isNotNull();

        // Un punto por sol.
        assertThat(fidelidad.saldoDe(despues.getId()))
                .isEqualTo(Math.round((float) abierto.totalCentimos() / 100));
    }

    @Test
    @DisplayName("un pedido que no existe no se puede pagar")
    void pagarUnoQueNoExiste() {
        assertThatThrownBy(() ->
                        compra.confirmarPago("DSC-NADA", MetodoDePago.TARJETA, Instant.now()))
                .isInstanceOf(CompraRechazada.class)
                .hasMessageContaining("No encontramos");
    }
}
