package pe.ssiomama.backend.caja;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArqueoRepository extends JpaRepository<ArqueoDeCaja, String> {

    /**
     * Los arqueos, del más reciente al más antiguo.
     *
     * <p>El {@code join fetch} del responsable evita una consulta por fila solo
     * para pintar el nombre de quien llevaba la caja.
     *
     * <p>Antes eran dos saltos —{@code responsable} era un empleado y de ahí se
     * llegaba al usuario— porque el esquema separaba las dos tablas. Ahora
     * {@code Usuario} guarda también los datos de empleado, así que basta uno.
     */
    @Query("""
            select a from ArqueoDeCaja a
            join fetch a.sala
            join fetch a.responsable
            order by a.abiertaEn desc
            """)
    List<ArqueoDeCaja> buscarRecientes(Pageable pagina);

    /** Un arqueo concreto, con todo lo que hace falta para su ficha. */
    @Query("""
            select a from ArqueoDeCaja a
            join fetch a.sala
            join fetch a.responsable
            where a.id = :id
            """)
    Optional<ArqueoDeCaja> buscarPorIdConDetalle(@Param("id") String id);
}
