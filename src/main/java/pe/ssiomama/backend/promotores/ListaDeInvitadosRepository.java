package pe.ssiomama.backend.promotores;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ssiomama.backend.auth.Usuario;

public interface ListaDeInvitadosRepository extends JpaRepository<ListaDeInvitados, String> {

    /** Todas las listas con su noche, su promotor y sus filas, de la más reciente a la más vieja. */
    @Query("""
            select distinct l from ListaDeInvitados l
            join fetch l.evento e
            join fetch l.promotor
            left join fetch l.invitados
            order by e.empiezaA desc
            """)
    List<ListaDeInvitados> buscarTodasConInvitados();

    /** Las listas de una noche. */
    @Query("""
            select distinct l from ListaDeInvitados l
            join fetch l.promotor
            left join fetch l.invitados
            where l.evento.slug = :slug
            order by l.nombre
            """)
    List<ListaDeInvitados> buscarDeLaNoche(@Param("slug") String slug);

    /** Personal con código de promotor, aunque todavía no tenga ninguna lista. */
    @Query("""
            select u from Usuario u
            where u.codigoDePromotor is not null
            order by u.nombre
            """)
    List<Usuario> buscarPromotores();

    interface VentaDePromotor {
        String getPromotorId();

        long getTotal();

        long getPedidos();
    }

    /**
     * Lo pagado con el código de cada promotor.
     *
     * <p>Nativa porque la entidad {@code Pedido} no mapea {@code promotorId}: la
     * compra todavía no recoge el código, y este es el único sitio que lo lee.
     * El estado va como literal SQL, que PostgreSQL convierte al enum solo.
     */
    @Query(value = """
            select "promotorId" as promotorId,
                   coalesce(sum("totalCentimos"), 0) as total,
                   count(*) as pedidos
            from "Pedido"
            where estado = 'PAGADO' and "promotorId" is not null
            group by "promotorId"
            """, nativeQuery = true)
    List<VentaDePromotor> ventasPorPromotor();
}
