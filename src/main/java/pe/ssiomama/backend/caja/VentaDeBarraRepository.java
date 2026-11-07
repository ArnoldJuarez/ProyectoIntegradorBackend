package pe.ssiomama.backend.caja;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaDeBarraRepository extends JpaRepository<VentaDeBarra, Long> {

    /** Los cobros de un arqueo, del más reciente al más antiguo. */
    @Query("""
            select v from VentaDeBarra v
            where v.arqueo.id = :idArqueo
            order by v.creadaEn desc
            """)
    List<VentaDeBarra> buscarPorArqueo(@Param("idArqueo") Long idArqueo);

    /**
     * Cuánto se cobró por cada método de pago en un arqueo.
     *
     * <p>Se agrega en la base y no en memoria porque una noche buena son
     * centenares de cobros, y traerlos todos para sumarlos en Java es mover
     * datos que no se van a enseñar.
     *
     * <p>Devuelve pares {@code [metodo, total]}. Es lo que permite separar el
     * efectivo —lo único que llega al cajón y por tanto lo único que explica un
     * descuadre— del resto.
     */
    @Query("""
            select v.metodoDePago, sum(v.totalCentimos)
            from VentaDeBarra v
            where v.arqueo.id = :idArqueo
            group by v.metodoDePago
            """)
    List<Object[]> sumarPorMetodoDePago(@Param("idArqueo") Long idArqueo);

    /**
     * Lo cobrado en barra durante una noche.
     *
     * <p>Cuelga del evento y no del turno de caja: una misma noche puede llevar
     * varias cajas —dos barras, o un relevo a media noche— y lo que interesa
     * aquí es lo que facturó la noche entera.
     */
    @Query("""
            select coalesce(sum(v.totalCentimos), 0)
            from VentaDeBarra v
            where v.evento.id = :eventoId
            """)
    long facturadoEnLaNoche(@Param("eventoId") Long eventoId);

    /** Cuántas consumiciones se sirvieron esa noche. */
    @Query("""
            select coalesce(sum(l.cantidad), 0)
            from LineaDeVenta l
            where l.venta.evento.id = :eventoId
            """)
    long consumicionesDeLaNoche(@Param("eventoId") Long eventoId);
}
