package pe.ssiomama.backend.clientes;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteraccionRepository extends JpaRepository<Interaccion, String> {

    /**
     * El historial de un cliente, de lo más reciente a lo más antiguo.
     *
     * <p>Va paginado porque no tiene tope natural: alguien que lleva dos años
     * viniendo puede acumular cientos de líneas, y la ficha solo necesita las
     * últimas para contar quién es.
     */
    List<Interaccion> findByClienteIdOrderByOcurrioEnDesc(String clienteId, Pageable pagina);
}
