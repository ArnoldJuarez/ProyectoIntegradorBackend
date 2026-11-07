package pe.ssiomama.backend.gastos;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a {@code "Gasto"}. */
public interface GastoRepository extends JpaRepository<Gasto, Long> {

    /** Los gastos entre dos instantes, con todo lo que se enseña, el más reciente primero. */
    @Query("""
            select g from Gasto g
            join fetch g.categoria
            left join fetch g.evento
            left join fetch g.apuntadoPor
            where g.fecha >= :desde and g.fecha < :hasta
            order by g.fecha desc
            """)
    List<Gasto> buscarEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
