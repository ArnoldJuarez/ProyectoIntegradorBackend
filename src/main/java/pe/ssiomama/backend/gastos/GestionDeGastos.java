package pe.ssiomama.backend.gastos;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.eventos.Evento;
import pe.ssiomama.backend.eventos.EventoRepository;

/**
 * Apuntar, corregir y borrar gastos.
 *
 * <p>Antes los gastos solo existían en la base —venían del sistema anterior— y
 * el informe post-evento los restaba, pero nadie podía apuntar uno nuevo: el
 * margen de cada noche salía con los costes de hace meses.
 *
 * <p>Un gasto se puede borrar: a diferencia de una venta, no hay un ticket ni
 * un arqueo que dependa de él, y un importe mal tecleado se arregla antes
 * quitándolo que dejando un contra-asiento que nadie entiende.
 */
@Service
public class GestionDeGastos {

    private final GastoRepository gastos;
    private final CategoriaDeGastoRepository categorias;
    private final EventoRepository eventos;

    public GestionDeGastos(
            GastoRepository gastos, CategoriaDeGastoRepository categorias, EventoRepository eventos) {
        this.gastos = gastos;
        this.categorias = categorias;
        this.eventos = eventos;
    }

    /**
     * @param eventoSlug la noche a la que va, o nulo si es un gasto general
     */
    public record DatosDeGasto(
            Long categoriaId, String eventoSlug, int importeCentimos, String descripcion, Instant fecha) {}

    @Transactional
    public Gasto apuntar(DatosDeGasto d, Usuario quien) {
        comprobar(d);
        return gastos.save(Gasto.apuntar(
                categoria(d.categoriaId()), noche(d.eventoSlug()), d.importeCentimos(), d.descripcion(),
                d.fecha() == null ? Instant.now() : d.fecha(), quien));
    }

    @Transactional
    public Gasto corregir(Long id, DatosDeGasto d) {
        comprobar(d);
        Gasto g = gastos.findById(id).orElseThrow(() -> new GastoRechazado("Ese gasto no existe."));
        g.cambiar(categoria(d.categoriaId()), noche(d.eventoSlug()), d.importeCentimos(), d.descripcion(),
                d.fecha() == null ? g.getFecha() : d.fecha());
        return g;
    }

    @Transactional
    public void borrar(Long id) {
        Gasto g = gastos.findById(id).orElseThrow(() -> new GastoRechazado("Ese gasto no existe."));
        gastos.delete(g);
    }

    @Transactional
    public CategoriaDeGasto crearCategoria(String nombre) {
        categorias.findFirstByNombreIgnoreCase(nombre.trim()).ifPresent(c -> {
            throw new GastoRechazado("Ya existe la categoría «" + c.getNombre() + "».");
        });
        return categorias.save(CategoriaDeGasto.crear(nombre));
    }

    private void comprobar(DatosDeGasto d) {
        if (d.importeCentimos() <= 0) {
            throw new GastoRechazado("El importe tiene que ser mayor que cero.");
        }
        if (d.fecha() != null && d.fecha().isAfter(Instant.now().plusSeconds(90L * 24 * 3600))) {
            throw new GastoRechazado("Esa fecha está a más de tres meses vista: ¿es un error de tecleo?");
        }
    }

    private CategoriaDeGasto categoria(Long id) {
        return (id == null ? java.util.Optional.<CategoriaDeGasto>empty() : categorias.findById(id))
                .orElseThrow(() -> new GastoRechazado("Esa categoría no existe."));
    }

    private Evento noche(String slug) {
        if (slug == null || slug.isBlank()) return null;
        return eventos.findBySlug(slug).orElseThrow(() -> new GastoRechazado("Esa noche no existe."));
    }
}
