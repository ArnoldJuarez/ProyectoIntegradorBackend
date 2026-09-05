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
}
