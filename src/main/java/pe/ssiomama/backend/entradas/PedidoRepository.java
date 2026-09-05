package pe.ssiomama.backend.entradas;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, String> {

    /**
     * Los pedidos recientes, con su cliente y su noche ya cargados.
     *
     * <p>No trae las líneas ni las entradas: en un listado no se enseñan, y
     * traerlas sería el problema de las N+1 consultas por partida doble.
     */
    @Query("""
            select p from Pedido p
            join fetch p.cliente
            join fetch p.evento
            order by p.creadoEn desc
            """)
    List<Pedido> buscarRecientes(Pageable pagina);

    /** Un pedido por el código que ve el cliente, con todo su detalle. */
    @Query("""
            select distinct p from Pedido p
            join fetch p.cliente
            join fetch p.evento
            left join fetch p.lineas l
            left join fetch l.tipoDeEntrada
            where p.codigo = :codigo
            """)
    Optional<Pedido> buscarPorCodigoConLineas(@Param("codigo") String codigo);
}
