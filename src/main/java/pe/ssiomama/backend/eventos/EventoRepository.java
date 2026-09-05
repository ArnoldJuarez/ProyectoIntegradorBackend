package pe.ssiomama.backend.eventos;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoRepository extends JpaRepository<Evento, String> {

    /**
     * Las noches del panel, con sus entradas y su sala ya cargadas.
     *
     * <p>El {@code join fetch} está para evitar el problema de las N+1
     * consultas: sin él, calcular el aforo vendido de cada noche dispararía una
     * consulta por noche, y con {@code open-in-view} desactivado ni siquiera
     * eso — saltaría {@code LazyInitializationException} al serializar.
     *
     * <p>{@code distinct} porque el join con los tipos de entrada multiplica las
     * filas: una noche con tres tipos vendría tres veces.
     */
    @Query("""
            select distinct e from Evento e
            join fetch e.sala
            left join fetch e.tiposDeEntrada
            order by e.empiezaA desc
            """)
    List<Evento> buscarTodosConDetalle();

    /** Una noche por su slug, con todo lo que hace falta para su ficha. */
    @Query("""
            select distinct e from Evento e
            join fetch e.sala
            left join fetch e.tiposDeEntrada
            where e.slug = :slug
            """)
    Optional<Evento> buscarPorSlugConDetalle(@Param("slug") String slug);
}
