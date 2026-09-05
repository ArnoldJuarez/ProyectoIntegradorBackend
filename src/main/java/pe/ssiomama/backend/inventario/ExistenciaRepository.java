package pe.ssiomama.backend.inventario;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExistenciaRepository extends JpaRepository<Existencia, String> {

    /**
     * Todo el stock, con su producto, su categoría y su sala ya cargados.
     *
     * <p>El {@code join fetch} evita el problema de las N+1 consultas: sin él,
     * pintar la categoría de cada línea dispararía una consulta por fila. Y con
     * {@code open-in-view} desactivado no es que fuera lento, es que fallaría al
     * serializar.
     *
     * <p>Se ordena poniendo primero lo que hay que reponer —cantidad por debajo
     * del umbral del producto—, porque esa es la razón por la que alguien abre
     * esta pantalla un jueves por la tarde.
     */
    @Query("""
            select e from Existencia e
            join fetch e.producto p
            join fetch p.categoria
            join fetch e.sala
            where p.activo = true
            order by case when e.cantidad <= p.alertaDeStock then 0 else 1 end,
                     p.nombre asc
            """)
    List<Existencia> buscarTodasConDetalle();
}
