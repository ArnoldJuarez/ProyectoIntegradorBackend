package pe.ssiomama.backend.entradas;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntradaRepository extends JpaRepository<Entrada, String> {

    /**
     * Una entrada por su id, con lo que hace falta para decidir en la puerta.
     *
     * <p>Trae el escaneo si lo hay: es lo que permite decir «entró a las 01:14»
     * en vez de un escueto «ya usada», que es lo que hace discutir a la gente.
     */
    @Query("""
            select e from Entrada e
            join fetch e.evento
            join fetch e.tipoDeEntrada
            left join fetch e.escaneo esc
            left join fetch esc.escaneadoPor
            where e.id = :id
            """)
    Optional<Entrada> buscarPorIdParaLaPuerta(@Param("id") String id);

    /** Las entradas de un pedido, para enseñarlas con su QR. */
    @Query("""
            select e from Entrada e
            join fetch e.tipoDeEntrada
            left join fetch e.escaneo
            where e.pedido.codigo = :codigo
            order by e.emitidaEn asc
            """)
    List<Entrada> buscarPorCodigoDePedido(@Param("codigo") String codigo);

    /**
     * Cuántas entradas hay en cada estado para una noche.
     *
     * <p>Se agrega en la base: una noche llena son cientos de filas y el panel
     * solo quiere los totales.
     */
    @Query("""
            select e.estado, count(e)
            from Entrada e
            where e.evento.slug = :slug
            group by e.estado
            """)
    List<Object[]> contarPorEstado(@Param("slug") String slug);

    /** Cuántas han pasado ya por la puerta. Es el aforo en vivo. */
    @Query("""
            select count(e) from Entrada e
            where e.evento.slug = :slug and e.escaneo is not null
            """)
    long contarEscaneadas(@Param("slug") String slug);

    /**
     * Cuántas entradas de una noche están en alguno de esos estados.
     *
     * <p>Los estados van como <b>parámetro</b> y nunca como literal dentro del
     * HQL. Para un literal, Hibernate deduce el nombre del tipo enum de
     * PostgreSQL del nombre de la clase Java y emite {@code ::EstadoDeEntrada}
     * contra un tipo que en la base se llama {@code EstadoEntrada}. Leer
     * funciona igual, así que el fallo no aparece hasta que alguien ejecuta esta
     * consulta concreta. Ver la nota en {@code Evento.getEstado()}.
     */
    @Query("""
            select count(e) from Entrada e
            where e.evento.slug = :slug and e.estado in :estados
            """)
    long contarPorEstados(
            @Param("slug") String slug,
            @Param("estados") java.util.Collection<EstadoDeEntrada> estados);

    /**
     * Cuánta gente puede presentarse en la puerta con una entrada de esa noche.
     *
     * <p>Las válidas más las ya usadas. Las anuladas y devueltas no cuentan
     * porque nadie va a venir con ellas, y es justo esta cifra la que decide si
     * se puede mover la fecha de un evento.
     */
    default long contarVigentesDe(String slug) {
        return contarPorEstados(slug, List.of(EstadoDeEntrada.VALIDA, EstadoDeEntrada.USADA));
    }
}
