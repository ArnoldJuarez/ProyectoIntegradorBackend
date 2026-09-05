package pe.ssiomama.backend.publico;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ssiomama.backend.eventos.EstadoDeEvento;
import pe.ssiomama.backend.eventos.Evento;

/**
 * Consultas de la web pública.
 *
 * <p>El filtro por estado vive <b>aquí</b>, no en el controlador. Así no hay
 * ninguna forma de pedir la cartelera y olvidarse de excluir los borradores: la
 * consulta no los devuelve nunca.
 *
 * <h2>Por qué son dos consultas y no una</h2>
 *
 * <p>Una noche tiene dos listas: sus tipos de entrada y su cartel. Pedir las dos
 * con {@code join fetch} en la misma consulta falla con
 * {@code MultipleBagFetchException}, y no es un capricho de Hibernate: el join
 * de dos colecciones produce el producto cartesiano —tres tipos por dos artistas
 * son seis filas— y desde ahí no hay forma de saber cuáles eran las de verdad.
 *
 * <p>La salida es lanzar dos consultas. La segunda parece no servir para nada,
 * porque su resultado se tira; lo que hace es rellenar la colección de las
 * <b>mismas instancias</b> que ya trajo la primera, porque viven en el contexto
 * de persistencia. Sigue siendo un número fijo de consultas, no una por noche.
 */
public interface CarteleraRepository extends JpaRepository<Evento, String> {

    /**
     * Las noches que todavía no han terminado, de la más cercana en adelante.
     *
     * <p>Se comparan contra {@code terminaA} y no contra {@code empiezaA}: una
     * noche que empezó a las 23:30 y acaba a las 6:00 sigue siendo la de hoy a
     * las dos de la mañana, y desaparecería de la agenda justo cuando más gente
     * la está mirando.
     *
     * <p>{@code SOLD_OUT} entra: una noche agotada se sigue anunciando, con su
     * cartel de agotado. Los borradores y las canceladas, no.
     */
    @Query("""
            select distinct e from Evento e
            join fetch e.sala
            left join fetch e.tiposDeEntrada
            where e.estado in :estados
              and e.terminaA >= current_timestamp
            order by e.empiezaA asc
            """)
    List<Evento> buscarProximasConEntradas(@Param("estados") Collection<EstadoDeEvento> estados);

    /**
     * Una noche publicada, por su slug.
     *
     * <p>Aquí sí entran las terminadas: el enlace de una noche pasada tiene que
     * seguir funcionando —lo comparte la gente— aunque ya no se venda.
     */
    @Query("""
            select distinct e from Evento e
            join fetch e.sala
            left join fetch e.tiposDeEntrada
            where e.slug = :slug
              and e.estado <> :borrador
            """)
    Optional<Evento> buscarPublicaPorSlugConEntradas(
            @Param("slug") String slug, @Param("borrador") EstadoDeEvento borrador);

    /**
     * Rellena el cartel de unas noches ya cargadas.
     *
     * <p>El valor devuelto no se usa: el efecto está en que las colecciones
     * {@code cartel} de esas mismas instancias quedan inicializadas.
     */
    @Query("""
            select distinct e from Evento e
            left join fetch e.cartel c
            left join fetch c.artista
            where e in :noches
            """)
    List<Evento> cargarCartelDe(@Param("noches") List<Evento> noches);

    // ── Lo que usan los controladores ──────────────────────────────

    /**
     * Lo que se anuncia: a la venta y agotado.
     *
     * <p>Va aquí y no incrustado en el HQL porque los valores del enum tienen
     * que viajar como parámetro. Ver la nota en {@code Evento.estado}.
     */
    Set<EstadoDeEvento> ANUNCIABLES =
            Set.of(EstadoDeEvento.PUBLISHED, EstadoDeEvento.SOLD_OUT);

    default List<Evento> buscarProximas() {
        List<Evento> proximas = buscarProximasConEntradas(ANUNCIABLES);
        if (!proximas.isEmpty()) {
            cargarCartelDe(proximas);
        }
        return proximas;
    }

    default Optional<Evento> buscarPublicaPorSlug(String slug) {
        Optional<Evento> noche =
                buscarPublicaPorSlugConEntradas(slug, EstadoDeEvento.DRAFT);
        noche.ifPresent(e -> cargarCartelDe(List.of(e)));
        return noche;
    }
}
