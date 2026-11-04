package pe.ssiomama.backend.publico;

import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ssiomama.backend.eventos.Actuacion;
import pe.ssiomama.backend.eventos.EstadoDeEvento;
import pe.ssiomama.backend.eventos.Evento;
import pe.ssiomama.backend.eventos.TipoDeEntrada;

/**
 * La cartelera pública: lo que ve un cliente sin haber entrado a ningún sitio.
 *
 * <h2>Por qué no reutiliza los DTO del panel</h2>
 *
 * <p>Porque lo que se enseña es distinto, y confiar en «acordarse» de filtrar
 * campos es como se filtran datos internos. Con dos DTO separados, añadir un
 * campo al panel no lo publica en internet por descuido.
 *
 * <p>Aquí <b>no</b> salen: lo recaudado, cuántas entradas van vendidas en
 * crudo, ni las noches en borrador. Sí sale el porcentaje de aforo vendido
 * —anima a comprar— y cuántas quedan de cada tipo, que hace falta para vender.
 */
@RestController
@RequestMapping("/api/publico")
public class CarteleraController {

    private final CarteleraRepository cartelera;

    public CarteleraController(CarteleraRepository cartelera) {
        this.cartelera = cartelera;
    }

    public record ArtistaEnCartel(
            String nombre,
            String genero,
            String imagen,
            String instagram,
            Instant empiezaA,
            Instant terminaA,
            boolean cabezaDeCartel) {

        static ArtistaEnCartel de(Actuacion a) {
            return new ArtistaEnCartel(
                    a.getArtista().getNombre(),
                    a.getArtista().getGenero(),
                    a.getArtista().getImagen(),
                    a.getArtista().getInstagram(),
                    a.getEmpiezaA(),
                    a.getTerminaA(),
                    a.isCabezaDeCartel());
        }
    }

    /** Una noche en la agenda. Lo justo para decidir si vas. */
    public record NocheEnCartelera(
            String slug,
            String titulo,
            String descripcion,
            String portada,
            String sala,
            Instant puertasA,
            Instant empiezaA,
            EstadoDeEvento estado,
            int edadMinima,
            String vestimenta,
            int precioDesdeCentimos,
            boolean conListaGratis,
            int porcentajeVendido,
            List<ArtistaEnCartel> cartel) {

        static NocheEnCartelera de(Evento e) {
            return new NocheEnCartelera(
                    e.getSlug(),
                    e.getTitulo(),
                    e.getDescripcion(),
                    e.getPortada(),
                    e.getSala().getNombre(),
                    e.getPuertasA(),
                    e.getEmpiezaA(),
                    e.getEstado(),
                    e.getEdadMinima(),
                    e.getVestimenta(),
                    e.getPrecioDesdeCentimos(),
                    e.tieneListaGratis(),
                    e.getPorcentajeVendido(),
                    e.getCartel().stream().map(ArtistaEnCartel::de).toList());
        }
    }

    /**
     * Un tipo de entrada, tal y como lo ve quien compra.
     *
     * <p>Va {@code disponibles} pero no {@code vendidas}: quien compra necesita
     * saber si quedan, no cuántas se han ido.
     */
    public record EntradaALaVenta(
            String id,
            String nombre,
            String descripcion,
            int precioCentimos,
            int disponibles,
            int maximoPorPedido,
            int consumicionesIncluidas) {

        static EntradaALaVenta de(TipoDeEntrada t) {
            return new EntradaALaVenta(
                    t.getId(),
                    t.getNombre(),
                    t.getDescripcion(),
                    t.getPrecioCentimos(),
                    t.getDisponibles(),
                    t.getMaximoPorPedido(),
                    t.getConsumicionesIncluidas());
        }
    }

    /** La ficha pública de una noche. */
    public record FichaDeNoche(
            String slug,
            String titulo,
            String descripcion,
            String portada,
            /** Un archivo subido, un MP4 externo o un enlace de YouTube; casi siempre nulo. */
            String video,
            String sala,
            Instant puertasA,
            Instant empiezaA,
            Instant terminaA,
            EstadoDeEvento estado,
            int edadMinima,
            String vestimenta,
            int porcentajeVendido,
            List<ArtistaEnCartel> cartel,
            List<EntradaALaVenta> entradas) {

        static FichaDeNoche de(Evento e) {
            return new FichaDeNoche(
                    e.getSlug(),
                    e.getTitulo(),
                    e.getDescripcion(),
                    e.getPortada(),
                    e.getVideo(),
                    e.getSala().getNombre(),
                    e.getPuertasA(),
                    e.getEmpiezaA(),
                    e.getTerminaA(),
                    e.getEstado(),
                    e.getEdadMinima(),
                    e.getVestimenta(),
                    e.getPorcentajeVendido(),
                    e.getCartel().stream().map(ArtistaEnCartel::de).toList(),
                    // Solo los tipos abiertos: uno desactivado no se enseña
                    // siquiera agotado, simplemente no existe para el cliente.
                    e.getTiposDeEntrada().stream()
                            .filter(TipoDeEntrada::isActivo)
                            .map(EntradaALaVenta::de)
                            .toList());
        }
    }

    /** Las próximas noches, de la más cercana a la más lejana. */
    @GetMapping("/agenda")
    @Transactional(readOnly = true)
    public List<NocheEnCartelera> agenda() {
        return cartelera.buscarProximas().stream().map(NocheEnCartelera::de).toList();
    }

    /**
     * Una noche por su slug.
     *
     * <p>Una noche en borrador responde 404 igual que una inexistente: decir
     * «existe pero no puedes verla» confirmaría que se está preparando algo.
     */
    @GetMapping("/evento/{slug}")
    @Transactional(readOnly = true)
    public ResponseEntity<FichaDeNoche> noche(@PathVariable String slug) {
        return cartelera.buscarPublicaPorSlug(slug)
                .map(FichaDeNoche::de)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
