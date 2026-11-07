package pe.ssiomama.backend.panel;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ssiomama.backend.caja.ArqueoDeCaja;
import pe.ssiomama.backend.caja.ArqueoRepository;
import pe.ssiomama.backend.caja.VentaDeBarraRepository;
import pe.ssiomama.backend.entradas.EscaneoRepository;
import pe.ssiomama.backend.entradas.EstadoDePedido;
import pe.ssiomama.backend.entradas.PedidoRepository;
import pe.ssiomama.backend.eventos.EstadoDeEvento;
import pe.ssiomama.backend.eventos.Evento;
import pe.ssiomama.backend.eventos.EventoRepository;
import pe.ssiomama.backend.inventario.Producto;
import pe.ssiomama.backend.inventario.ProductoRepository;
import pe.ssiomama.backend.reservados.ReservaRepository;

/**
 * Las dos pantallas que resumen: la portada del panel y la noche en curso.
 *
 * <h2>Por qué existen si todo esto ya se ve en otro sitio</h2>
 *
 * <p>Ninguna de las dos añade capacidad: los ingresos están en pedidos, el stock
 * bajo en almacén y el aforo en la puerta. Lo que añaden es <b>no tener que
 * sumar a mano</b>.
 *
 * <p>La pregunta que se hace la jefa de sala a las dos de la mañana —«¿cómo va
 * la noche?»— obligaba a abrir tres pantallas y hacer la cuenta de cabeza. Esa
 * cuenta hecha a las dos de la mañana es exactamente la que sale mal.
 *
 * <h2>Van en su propio paquete</h2>
 *
 * <p>{@code panel} no es un dominio: no tiene entidades ni reglas propias. Es
 * una vista que cruza cinco dominios, y meterla en cualquiera de ellos haría que
 * ese dominio dependiera de los otros cuatro solo para pintar una cifra.
 */
@RestController
@RequestMapping("/api/panel")
public class PanelController {

    /** Cuánto hacia atrás mira la cifra de ingresos de la portada. */
    private static final Duration VENTANA_DE_INGRESOS = Duration.ofDays(30);

    /**
     * El mismo margen que usa la puerta para decidir qué noche está abierta.
     *
     * <p>Tiene que coincidir: si la torre de control dijera que la noche es una
     * y el escáner admitiera otra, quien mira la pantalla vería rechazos que no
     * se explican con lo que tiene delante.
     */
    private static final Duration MARGEN_DE_APERTURA = Duration.ofHours(6);

    /** En qué tramos se reparte el ritmo de entrada. */
    private static final Duration TRAMO = Duration.ofMinutes(15);

    /** Cuántas referencias por reponer se enseñan antes de remitir a almacén. */
    private static final int REPOSICIONES_EN_PORTADA = 6;

    /** Cuántos pedidos recientes caben en la portada. */
    private static final int PEDIDOS_EN_PORTADA = 5;

    private final EventoRepository eventos;
    private final PedidoRepository pedidos;
    private final ProductoRepository productos;
    private final ArqueoRepository arqueos;
    private final VentaDeBarraRepository ventas;
    private final EscaneoRepository escaneos;
    private final ReservaRepository reservas;

    public PanelController(
            EventoRepository eventos,
            PedidoRepository pedidos,
            ProductoRepository productos,
            ArqueoRepository arqueos,
            VentaDeBarraRepository ventas,
            EscaneoRepository escaneos,
            ReservaRepository reservas) {
        this.eventos = eventos;
        this.pedidos = pedidos;
        this.productos = productos;
        this.arqueos = arqueos;
        this.ventas = ventas;
        this.escaneos = escaneos;
        this.reservas = reservas;
    }

    // ── Lo que sale ───────────────────────────────────────────────

    /**
     * Una de las cuatro cifras grandes de la portada.
     *
     * @param tono cómo pintarla. Va aquí y no en el navegador porque si una
     *     cifra es buena o mala lo decide el negocio: siete referencias bajo
     *     mínimo es malo, cero propuestas pendientes no lo es.
     */
    public record Cifra(String etiqueta, String valor, String pie, String tono) {}

    public record NocheEnPortada(
            String slug,
            String titulo,
            Instant puertasA,
            int vendidas,
            int aforoALaVenta,
            int porcentajeVendido,
            long recaudadoCentimos) {

        static NocheEnPortada de(Evento e, long recaudado) {
            return new NocheEnPortada(
                    e.getSlug(),
                    e.getTitulo(),
                    e.getPuertasA(),
                    e.getVendidas(),
                    e.getAforoALaVenta(),
                    e.getPorcentajeVendido(),
                    recaudado);
        }
    }

    public record ArqueoEnPortada(
            Long id,
            String sala,
            String responsable,
            Integer diferenciaCentimos,
            boolean estaAbierta,
            Instant abiertaEn) {

        static ArqueoEnPortada de(ArqueoDeCaja a) {
            return new ArqueoEnPortada(
                    a.getId(),
                    a.getSala().getNombre(),
                    a.getResponsable().getNombre(),
                    a.getDiferenciaCentimos(),
                    a.estaAbierta(),
                    a.getAbiertaEn());
        }
    }

    public record ReposicionEnPortada(
            Long productoId, String producto, int quedan, int umbral, int faltan) {

        static ReposicionEnPortada de(Producto p) {
            return new ReposicionEnPortada(
                    p.getId(),
                    p.getNombre(),
                    p.getStock(),
                    p.getAlertaDeStock(),
                    p.faltanParaElUmbral());
        }
    }

    public record PedidoEnPortada(
            String codigo, String cliente, String noche, int totalCentimos, Instant creadoEn) {}

    /**
     * La portada del panel.
     *
     * @param proxima la siguiente noche programada, o nulo si no hay ninguna
     * @param ultima la última ya celebrada, para comparar
     */
    public record Resumen(
            List<Cifra> cifras,
            NocheEnPortada proxima,
            NocheEnPortada ultima,
            long asistenciaDeLaUltima,
            ArqueoEnPortada ultimoArqueo,
            List<ReposicionEnPortada> hayQueReponer,
            int totalPorReponer,
            List<PedidoEnPortada> ultimosPedidos) {}

    /** Un tramo del ritmo de entrada. */
    public record TramoDeEntrada(Instant desde, int personas) {}

    public record ReservaEnLaNoche(
            String mesa, String zona, String cliente, int personas, String estado) {}

    /**
     * Cuánta gente ha entrado por cada acceso.
     *
     * <p>No es una curiosidad: dice si la cola está repartida o si todo el
     * mundo se amontona en una sola puerta teniendo otra libre al lado. Es lo
     * que se mira para mandar a alguien a abrir la segunda.
     *
     * @param caudalPorMinuto cuánta gente admite esa puerta por minuto, para
     *     poder comparar lo que entra con lo que aguanta
     */
    public record AccesoPorPuerta(String puerta, long personas, int caudalPorMinuto) {}

    /**
     * La noche en curso, mientras ocurre.
     *
     * @param enCurso en falso, lo que se enseña es la próxima y los números son
     *     los de una noche que todavía no ha empezado
     * @param gastoPorAsistenteCentimos lo que deja en barra cada persona que
     *     entró. Es la cifra que dice si la noche está yendo bien de verdad:
     *     mucha gente que no consume es aforo ocupado sin ingreso.
     */
    public record TorreDeControl(
            boolean hayNoche,
            boolean enCurso,
            String slug,
            String titulo,
            String sala,
            Instant puertasA,
            Instant terminaA,
            int aforoDeSala,
            int vendidas,
            long dentro,
            long taquillaCentimos,
            long barraCentimos,
            long consumiciones,
            long gastoPorAsistenteCentimos,
            List<TramoDeEntrada> ritmoDeEntrada,
            List<ReservaEnLaNoche> reservados,
            List<AccesoPorPuerta> porPuerta) {

        static TorreDeControl sinNoche() {
            return new TorreDeControl(
                    false, false, null, null, null, null, null,
                    0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
        }
    }

    // ── Endpoints ─────────────────────────────────────────────────

    /** Lo que hay que mirar antes de abrir esta noche. */
    @GetMapping("/resumen")
    @Transactional(readOnly = true)
    public Resumen resumen() {
        Instant ahora = Instant.now();

        var facturado = pedidos.facturadoDesde(
                EstadoDePedido.PAGADO, ahora.minus(VENTANA_DE_INGRESOS));
        long ingresos = facturado.getTotal();
        long cuantosPedidos = facturado.getPedidos();

        var proxima = eventos.buscarProximaNoche(EstadoDeEvento.PUBLICADO, ahora).orElse(null);
        var ultima = eventos.buscarUltimaCelebrada(ahora).orElse(null);

        List<Producto> bajoMinimo = productos.buscarConStock().stream()
                .filter(Producto::necesitaReposicion)
                .toList();

        long cajasAbiertas = arqueos.countByCerradaEnIsNull();

        return new Resumen(
                List.of(
                        new Cifra(
                                "Ingresos 30 días",
                                String.valueOf(ingresos),
                                "%d %s pagados".formatted(
                                        cuantosPedidos, cuantosPedidos == 1 ? "pedido" : "pedidos"),
                                "acento"),
                        new Cifra(
                                "Vendidas próxima",
                                proxima == null ? "0" : String.valueOf(proxima.getVendidas()),
                                proxima == null
                                        ? "no hay noche programada"
                                        : "de %d a la venta".formatted(proxima.getAforoALaVenta()),
                                "neutro"),
                        new Cifra(
                                "Bajo mínimo",
                                String.valueOf(bajoMinimo.size()),
                                "referencias que reponer",
                                bajoMinimo.isEmpty() ? "bien" : "mal"),
                        new Cifra(
                                "Cajas sin cerrar",
                                String.valueOf(cajasAbiertas),
                                cajasAbiertas == 0 ? "todo cuadrado" : "nadie contó ese dinero",
                                cajasAbiertas == 0 ? "bien" : "mal")),
                proxima == null
                        ? null
                        : NocheEnPortada.de(
                                proxima,
                                pedidos.taquillaDe(proxima.getId(), EstadoDePedido.PAGADO)),
                ultima == null
                        ? null
                        : NocheEnPortada.de(
                                ultima, pedidos.taquillaDe(ultima.getId(), EstadoDePedido.PAGADO)),
                ultima == null ? 0 : escaneos.countByEventoId(ultima.getId()),
                arqueos.buscarRecientes(PageRequest.of(0, 1)).stream()
                        .findFirst()
                        .map(ArqueoEnPortada::de)
                        .orElse(null),
                bajoMinimo.stream()
                        .limit(REPOSICIONES_EN_PORTADA)
                        .map(ReposicionEnPortada::de)
                        .toList(),
                bajoMinimo.size(),
                pedidos.buscarRecientes(PageRequest.of(0, PEDIDOS_EN_PORTADA)).stream()
                        .map(p -> new PedidoEnPortada(
                                p.getCodigo(),
                                p.getCliente().getNombreCompleto(),
                                p.getEvento().getTitulo(),
                                p.getTotalCentimos(),
                                p.getCreadoEn()))
                        .toList());
    }

    /** Cómo va la noche: aforo, taquilla, barra y reservados en una pantalla. */
    @GetMapping("/noche")
    @Transactional(readOnly = true)
    public TorreDeControl noche() {
        Instant ahora = Instant.now();

        var abierta = eventos.buscarNocheAbierta(
                EstadoDeEvento.PUBLICADO, ahora.plus(MARGEN_DE_APERTURA), ahora);

        if (abierta.isPresent()) {
            return conCifras(abierta.get(), true);
        }

        return eventos.buscarProximaNoche(EstadoDeEvento.PUBLICADO, ahora)
                .map(e -> conCifras(e, false))
                .orElseGet(TorreDeControl::sinNoche);
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    private TorreDeControl conCifras(Evento noche, boolean enCurso) {
        long dentro = escaneos.countByEventoId(noche.getId());
        long barra = ventas.facturadoEnLaNoche(noche.getId());

        return new TorreDeControl(
                true,
                enCurso,
                noche.getSlug(),
                noche.getTitulo(),
                noche.getSala().getNombre(),
                noche.getPuertasA(),
                noche.getTerminaA(),
                noche.getSala().getAforo(),
                noche.getVendidas(),
                dentro,
                pedidos.taquillaDe(noche.getId(), EstadoDePedido.PAGADO),
                barra,
                ventas.consumicionesDeLaNoche(noche.getId()),
                // Se divide entre quien entró, no entre quien compró: el gasto
                // en barra solo lo hace quien está dentro.
                dentro == 0 ? 0 : barra / dentro,
                enCurso ? repartirEnTramos(escaneos.horasDeEntrada(noche.getId())) : List.of(),
                reservas.buscarDeLaNoche(noche.getId()).stream()
                        .map(r -> new ReservaEnLaNoche(
                                r.getMesa().getNombre(),
                                r.getMesa().getZona() == null
                                        ? null
                                        : r.getMesa().getZona().getNombre(),
                                r.getCliente().getNombreCompleto(),
                                r.getPersonas(),
                                r.getEstado().name()))
                        .toList(),
                escaneos.contarPorPuerta(noche.getId()).stream()
                        .map(f -> new AccesoPorPuerta(
                                (String) f[0], (Long) f[1], (Integer) f[2]))
                        .toList());
    }

    /**
     * Reparte los accesos en tramos de cuarto de hora.
     *
     * <p>Los tramos vacíos se incluyen con cero. Si se saltaran, una cola que
     * se corta durante media hora se vería como dos tramos seguidos y el bajón
     * desaparecería del gráfico — que es justo lo que se está mirando.
     */
    private List<TramoDeEntrada> repartirEnTramos(List<Instant> entradas) {
        if (entradas.isEmpty()) {
            return List.of();
        }

        Instant primera = entradas.getFirst();
        Instant ultima = entradas.getLast();

        List<TramoDeEntrada> tramos = new ArrayList<>();
        for (Instant corte = primera; !corte.isAfter(ultima); corte = corte.plus(TRAMO)) {
            Instant fin = corte.plus(TRAMO);
            final Instant desde = corte;

            int cuantos = (int) entradas.stream()
                    .filter(e -> !e.isBefore(desde) && e.isBefore(fin))
                    .count();

            tramos.add(new TramoDeEntrada(desde, cuantos));
        }
        return tramos;
    }
}
