package pe.ssiomama.backend.clientes;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a {@code "Campania"}. */
public interface CampaniaRepository extends JpaRepository<Campania, Long> {

    /**
     * Las campañas de la más reciente a la más antigua, con su segmento cargado.
     *
     * <p>El {@code join fetch} evita una consulta por fila solo para pintar a
     * quién va dirigida. Con {@code open-in-view} desactivado no es que fuera
     * lento: fallaría al serializar.
     */
    @Query("""
            select c from Campania c
            join fetch c.segmento
            order by c.creadoEn desc
            """)
    List<Campania> buscarRecientes();

    @Query("select c from Campania c join fetch c.segmento where c.id = :id")
    Optional<Campania> buscarConSegmento(@Param("id") Long id);

    /** Cuántas campañas apuntan a un segmento. Decide si se puede borrar. */
    long countBySegmentoId(Long segmentoId);
}
