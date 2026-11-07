package pe.ssiomama.backend.promotores;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a {@code "LiquidacionPromotor"}. */
public interface LiquidacionRepository extends JpaRepository<Liquidacion, Long> {

    @Query("""
            select l from Liquidacion l left join fetch l.registradaPor
            where l.promotor.id = :promotorId
            order by l.pagadaEn desc
            """)
    List<Liquidacion> deUnPromotor(@Param("promotorId") Long promotorId);
}
