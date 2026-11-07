package pe.ssiomama.backend.entradas;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

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

    /**
     * El pedido que se va a cobrar, <b>bloqueado</b>.
     *
     * <p>Sin el bloqueo, dos pulsaciones del botón de pagar casi a la vez —o dos
     * pestañas abiertas— leerían las dos que el pedido está pendiente y las dos
     * emitirían el juego completo de entradas. El cliente acabaría con el doble,
     * y el aforo diría que entraron el doble de personas de las que compraron.
     *
     * <p>La comprobación de «ya está pagado» que hay en el código no basta por sí
     * sola: entre leerla y escribir el estado hay un hueco, y es justo ahí donde
     * entra la segunda pulsación. Lo que cierra el hueco es esto.
     *
     * <p>No trae las líneas con {@code join fetch} a propósito: bloquear con
     * {@code join fetch} bloquearía también las filas unidas —los tipos de
     * entrada, el cliente— y eso frenaría a compradores que no tienen nada que
     * ver con este pedido. Las líneas se cargan después, ya dentro de la
     * transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.codigo = :codigo")
    Optional<Pedido> bloquearParaCobrar(@Param("codigo") String codigo);

    /**
     * Lo facturado por entradas desde una fecha, y en cuántos pedidos.
     *
     * <p>Solo cuenta lo pagado: un pendiente aparta cupo pero todavía no es
     * dinero, y sumarlo haría que los ingresos del panel bajaran solos cada vez
     * que alguien abandona una compra.
     *
     * <p>Devuelve dos cifras en una sola consulta porque siempre se enseñan
     * juntas: «S/ 612 en 70 pedidos» dice mucho más que cualquiera de las dos
     * por separado.
     *
     * <p>La proyección no es decoración: con {@code Object[]} como tipo de
     * retorno, Spring Data devuelve un array cuyo único elemento es a su vez el
     * array de columnas, y el cast a número revienta en tiempo de ejecución con
     * un mensaje que no menciona la consulta.
     */
    interface Facturado {
        long getTotal();

        long getPedidos();
    }

    @Query("""
            select coalesce(sum(p.totalCentimos), 0) as total, count(p) as pedidos
            from Pedido p
            where p.estado = :pagado and p.pagadoEn >= :desde
            """)
    Facturado facturadoDesde(
            @Param("pagado") EstadoDePedido pagado, @Param("desde") Instant desde);

    /** Lo facturado por entradas de una noche concreta. */
    @Query("""
            select coalesce(sum(p.totalCentimos), 0)
            from Pedido p
            where p.evento.id = :eventoId and p.estado = :pagado
            """)
    long taquillaDe(@Param("eventoId") Long eventoId, @Param("pagado") EstadoDePedido pagado);
}
