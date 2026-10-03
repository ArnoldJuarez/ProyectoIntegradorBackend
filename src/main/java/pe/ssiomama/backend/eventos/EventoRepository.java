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

    /**
     * Una noche por su slug, <b>sin arrastrar sus tipos de entrada</b>.
     *
     * <p>La diferencia con {@link #buscarPorSlugConDetalle} no es de eficiencia,
     * es de corrección, y costó un test en rojo averiguarlo.
     *
     * <p>Al vender hay que bloquear los tipos de entrada con
     * {@code select ... for update}. Pero si antes se han cargado con un
     * {@code join fetch}, ya están en el contexto de persistencia, y entonces la
     * consulta con bloqueo <b>devuelve las instancias que ya había</b>: Hibernate
     * pide el bloqueo a la base, sí, pero no vuelve a leer la fila. El resultado
     * es que veinte compras simultáneas leen todas el mismo «vendidas» viejo y
     * se pisan los incrementos en silencio — sin ningún error, con veinte
     * pedidos creados y dos entradas descontadas.
     *
     * <p>Por eso vender empieza por aquí: la noche se carga suelta, y los tipos
     * los trae por primera vez la consulta que los bloquea.
     */
    Optional<Evento> findBySlug(String slug);

    /** ¿Está cogido ese slug? Lo usa el generador para no repetir direcciones. */
    boolean existsBySlug(String slug);

    /**
     * La última noche que ya se celebró.
     *
     * <p>Sirve para comparar: la portada enseña la próxima al lado de la
     * anterior, y una cifra sola no dice si va bien o mal.
     *
     * <p>No filtra por estado. Una noche que se canceló también «pasó», pero lo
     * que se busca aquí es la última de la que hay datos reales, y el estado se
     * enseña para que quien mira lo sepa.
     */
    @Query("""
            select e from Evento e
            join fetch e.sala
            where e.terminaA < :ahora
            order by e.empiezaA desc
            limit 1
            """)
    Optional<Evento> buscarUltimaCelebrada(@Param("ahora") Instant ahora);
}
