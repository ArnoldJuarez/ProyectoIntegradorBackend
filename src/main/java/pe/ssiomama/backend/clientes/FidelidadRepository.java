package pe.ssiomama.backend.clientes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a {@code "MovimientoFidelidad"}. */
public interface FidelidadRepository extends JpaRepository<MovimientoDeFidelidad, Long> {

    /**
     * Los puntos que le quedan a un cliente: la suma de sus apuntes.
     *
     * <p>{@code coalesce} porque sumar cero filas devuelve nulo, no cero, y un
     * cliente recién creado no tiene ninguno.
     */
    @Query("""
            select coalesce(sum(m.puntos), 0)
            from MovimientoDeFidelidad m
            where m.cliente.id = :clienteId
            """)
    int saldoDe(@Param("clienteId") Long clienteId);
}
