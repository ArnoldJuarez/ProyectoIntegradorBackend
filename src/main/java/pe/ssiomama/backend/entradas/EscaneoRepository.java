package pe.ssiomama.backend.entradas;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a {@code "Acceso"}: quién ha entrado ya y por dónde. */
public interface EscaneoRepository extends JpaRepository<Escaneo, Long> {

    /** Cuánta gente lleva dentro una noche. Es el aforo en vivo. */
    long countByEventoId(Long eventoId);

    /**
     * Los últimos que han pasado, con el portero y la entrada ya cargados.
     *
     * <p>Alimenta la lista que el portero ve debajo del escáner: sirve para
     * comprobar de un vistazo que lo que acaba de escanear se registró, y para
     * localizar a alguien que discute («entró hace dos minutos, aquí está»).
     */
    @Query("""
            select e from Escaneo e
            join fetch e.entrada t
            join fetch t.tipoDeEntrada
            join fetch e.escaneadoPor
            left join fetch e.puerta
            where e.evento.id = :eventoId
            order by e.escaneadoEn desc
            """)
    List<Escaneo> buscarUltimosDe(@Param("eventoId") Long eventoId, Pageable pagina);

    /**
     * Cuántos han entrado por cada puerta.
     *
     * <p>Es lo que dice si la cola está repartida o si todo el mundo se
     * amontona en un solo acceso teniendo otro libre al lado.
     *
     * <p>Tres columnas por fila: nombre, cuántos pasaron y el caudal por minuto
     * que admite esa puerta. El caudal va aquí para poder comparar lo que entra
     * con lo que la puerta aguanta; en los accesos sin puerta registrada sale a
     * cero, que es lo que significa no saberlo.
     */
    @Query("""
            select coalesce(p.nombre, 'Sin registrar'), count(e),
                   coalesce(p.caudalPorMinuto, 0)
            from Escaneo e
            left join e.puerta p
            where e.evento.id = :eventoId
            group by p.nombre, p.caudalPorMinuto
            order by count(e) desc
            """)
    List<Object[]> contarPorPuerta(@Param("eventoId") Long eventoId);

    /**
     * A qué hora fue entrando la gente.
     *
     * <p>Devuelve los instantes de cada acceso, sin agrupar. El reparto en
     * tramos se hace en Java y no con {@code date_trunc} por una razón: el
     * tamaño del tramo es una decisión de presentación —quince minutos se lee
     * bien en una noche, una hora no— y bajarla a la consulta la deja escrita en
     * SQL, donde cambiarla obliga a tocar la base.
     *
     * <p>Son unos cientos de filas por noche: traerlas sale más barato que una
     * consulta por tramo.
     */
    @Query("""
            select e.escaneadoEn from Escaneo e
            where e.evento.id = :eventoId
            order by e.escaneadoEn asc
            """)
    List<java.time.Instant> horasDeEntrada(@Param("eventoId") Long eventoId);
}
