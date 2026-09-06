package pe.ssiomama.backend.eventos;

import java.time.Instant;
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

    /**
     * La noche que está abierta ahora mismo.
     *
     * <p>«Abierta» es la misma ventana que aplica el escáner: desde unas horas
     * antes de empezar hasta que termina. Tienen que coincidir, porque si la
     * pantalla dijera que la noche es una y el escáner admitiera otra, el
     * portero vería rechazos que no se explican con lo que tiene delante. Por
     * eso el margen lo pasa quien llama, en vez de repetirlo aquí.
     *
     * <p>El estado va como parámetro y no como literal: ver la nota en
     * {@link Evento#getEstado()} sobre el nombre del tipo enum.
     */
    @Query("""
            select e from Evento e
            join fetch e.sala
            where e.estado = :estado
              and e.empiezaA <= :limite
              and e.terminaA >= :ahora
            order by e.empiezaA asc
            limit 1
            """)
    Optional<Evento> buscarNocheAbierta(
            @Param("estado") EstadoDeEvento estado,
            @Param("limite") Instant limite,
            @Param("ahora") Instant ahora);

    /**
     * La siguiente noche que hay programada.
     *
     * <p>Es el recambio cuando no hay ninguna abierta: la pantalla de la puerta
     * en blanco no dice si es que no hay evento o si es que algo se rompió.
     * Enseñar «la próxima es el sábado» responde eso.
     */
    @Query("""
            select e from Evento e
            join fetch e.sala
            where e.estado = :estado and e.empiezaA >= :ahora
            order by e.empiezaA asc
            limit 1
            """)
    Optional<Evento> buscarProximaNoche(
            @Param("estado") EstadoDeEvento estado, @Param("ahora") Instant ahora);
}
