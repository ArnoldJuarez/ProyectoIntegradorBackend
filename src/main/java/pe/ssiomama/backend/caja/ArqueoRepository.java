package pe.ssiomama.backend.caja;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * El turno abierto de una persona, si lo tiene.
     *
     * <p>Es lo que hace que volver a entrar a la barra sea <b>continuar</b> el
     * turno y no empezar otro. Sin esto, cerrar la pestaña y volver abría una
     * segunda caja, las ventas se repartían entre las dos y al cuadrar no salía
     * nada.
     */
    @Query("""
            select a from ArqueoDeCaja a
            join fetch a.sala
            join fetch a.responsable
            where a.responsable.id = :usuarioId and a.cerradaEn is null
            order by a.abiertaEn desc
            limit 1
            """)
    Optional<ArqueoDeCaja> buscarAbiertoDe(@Param("usuarioId") String usuarioId);

    /**
     * El turno que se va a modificar, <b>bloqueado</b>.
     *
     * <p>Cobrar suma al efectivo esperado, y sumar es leer y escribir. Dos
     * cobros a la vez en la misma caja —dos tablets, o dos toques rápidos— se
     * pisarían el incremento y el cajón quedaría corto sin que nada lo dijera:
     * exactamente el descuadre que este turno existe para detectar.
     *
     * <p>Cerrar la caja usa el mismo bloqueo, y por eso una venta que llegue a
     * la vez que el cierre espera y se encuentra la caja ya cerrada, en vez de
     * colarse después del recuento.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ArqueoDeCaja a where a.id = :id")
    Optional<ArqueoDeCaja> bloquear(@Param("id") String id);

    /**
     * Cuántas cajas siguen abiertas.
     *
     * <p>Es una de las cuatro cifras de la portada del panel. Una caja sin
     * cerrar al día siguiente no es un detalle: significa que nadie contó el
     * dinero de esa noche, y cuanto más tarde en revisarse menos se acuerda
     * quien la llevaba.
     */
    long countByCerradaEnIsNull();
}
