package pe.ssiomama.backend.reservados;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MesaVipRepository extends JpaRepository<MesaVip, Long> {

    /**
     * Los reservados que se pueden pedir, con su zona ya cargada.
     *
     * <p>Se ordenan por zona y luego por plazas: quien mira la lista busca «algo
     * para seis», no un nombre concreto.
     */
    @Query("""
            select m from MesaVip m
            left join fetch m.zona z
            where m.activa = true
            order by z.orden asc, m.plazas asc, m.nombre asc
            """)
    List<MesaVip> buscarDisponibles();
}
