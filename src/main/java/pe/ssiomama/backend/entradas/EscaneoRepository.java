package pe.ssiomama.backend.entradas;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a {@code "Acceso"}: quién ha entrado ya y por dónde. */
public interface EscaneoRepository extends JpaRepository<Escaneo, String> {

    /** Cuánta gente lleva dentro una noche. Es el aforo en vivo. */
    long countByEventoId(String eventoId);

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
    List<Escaneo> buscarUltimosDe(@Param("eventoId") String eventoId, Pageable pagina);

    /**
     * Cuántos han entrado por cada puerta.
     *
     * <p>Devuelve el nombre y el recuento. Es lo que dice si la cola está
     * repartida o si todo el mundo se amontona en un solo acceso.
     */
    @Query("""
            select coalesce(p.nombre, 'Sin registrar'), count(e)
            from Escaneo e
            left join e.puerta p
            where e.evento.id = :eventoId
            group by p.nombre
            order by count(e) desc
            """)
    List<Object[]> contarPorPuerta(@Param("eventoId") String eventoId);
}
