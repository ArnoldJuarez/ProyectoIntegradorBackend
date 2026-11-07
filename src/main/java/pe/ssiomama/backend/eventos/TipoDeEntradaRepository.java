package pe.ssiomama.backend.eventos;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a {@code "TipoEntrada"}. */
public interface TipoDeEntradaRepository extends JpaRepository<TipoDeEntrada, Long> {

    /**
     * Los tipos pedidos, <b>bloqueados</b> para vender.
     *
     * <h2>Por qué un bloqueo y no solo aislamiento</h2>
     *
     * <p>Vender es leer cuántas quedan y escribir cuántas van. Es la operación
     * que hay que proteger, y hay dos formas de hacerlo:
     *
     * <ul>
     *   <li><b>Serializable</b>: se deja correr a todos y la base aborta a quien
     *       se pise, con el error {@code 40001}. Funciona, pero con veinte
     *       compras simultáneas sobre la misma fila los reintentos vuelven a
     *       chocar entre ellos, y hay quien acaba viendo un error del servidor
     *       teniendo entradas disponibles. Se midió: le pasaba a 5 de 20.
     *   <li><b>Bloqueo pesimista</b> —esto—: quien llega segundo <b>espera</b> a
     *       que el primero termine, y entonces lee el número ya actualizado. No
     *       aborta a nadie. Para una fila concreta y muy disputada, que es
     *       exactamente el caso, es la herramienta que corresponde.
     * </ul>
     *
     * <p>El bloqueo dura lo que la transacción, que aquí son milisegundos.
     *
     * <h2>El orden no es decorativo</h2>
     *
     * <p>{@code order by t.id} evita un interbloqueo. Sin él, un pedido de
     * General+VIP y otro de VIP+General bloquearían las dos filas en orden
     * opuesto, cada uno esperando la que tiene el otro, y la base tendría que
     * matar a uno de los dos. Bloqueando siempre en el mismo orden eso no puede
     * pasar.
     *
     * <h2>Y el filtro por evento</h2>
     *
     * <p>No es una comodidad: sin él, alguien podría montar una petición con el
     * id de un evento barato y los tipos de entrada de otro, y comprar una VIP
     * al precio de la general. Quien llama compara cuántos pidió con cuántos
     * vuelven; si no coinciden, algo no encaja.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from TipoDeEntrada t
            where t.evento.id = :eventoId and t.id in :ids
            order by t.id
            """)
    List<TipoDeEntrada> bloquearParaVender(
            @Param("eventoId") Long eventoId, @Param("ids") List<Long> ids);

    /** Los mismos sin bloquear, para consultar sin intención de vender. */
    @Query("""
            select t from TipoDeEntrada t
            where t.evento.id = :eventoId and t.id in :ids
            """)
    List<TipoDeEntrada> buscarDelEvento(
            @Param("eventoId") Long eventoId, @Param("ids") List<Long> ids);

    /** Un tipo con su evento ya cargado. */
    @Query("select t from TipoDeEntrada t join fetch t.evento where t.id = :id")
    Optional<TipoDeEntrada> buscarConEvento(@Param("id") Long id);

    /** Todos los tipos de una noche, como entidades: para borrarlos con ella. */
    @Query("select t from TipoDeEntrada t where t.evento.id = :eventoId")
    List<TipoDeEntrada> buscarDeLaNoche(@Param("eventoId") Long eventoId);
}
