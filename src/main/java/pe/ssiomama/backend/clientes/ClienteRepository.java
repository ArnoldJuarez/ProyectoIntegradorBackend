package pe.ssiomama.backend.clientes;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, String> {

    /**
     * Por correo, ignorando mayúsculas.
     *
     * <p>El correo se normaliza a minúsculas antes de guardar, pero la búsqueda
     * lo ignora igualmente: en la base ya hay filas del sistema anterior y no
     * conviene depender de que todas se guardaran igual.
     */
    Optional<Cliente> findByEmailIgnoreCase(String email);

    /**
     * La lista del panel, opcionalmente filtrada por nombre, apellido o correo.
     *
     * <p>Se ordena por última visita descendente, con los que nunca han venido
     * al final: quien abre esta pantalla busca a alguien de las últimas noches,
     * no a quien se apuntó al boletín hace ocho meses.
     *
     * <p>{@code lower(...) like} y no {@code ilike}: {@code ilike} es de
     * PostgreSQL y no existe en JPQL. Con el patrón ya en minúsculas desde el
     * servicio, hace el mismo trabajo y la consulta sigue siendo portable.
     */
    @Query("""
            select c from Cliente c
            where :patron is null
               or lower(c.nombre) like :patron
               or lower(coalesce(c.apellido, '')) like :patron
               or lower(c.email) like :patron
            order by c.ultimaVisita desc nulls last, c.creadoEn desc
            """)
    List<Cliente> buscar(@Param("patron") String patron, Pageable pagina);
}
