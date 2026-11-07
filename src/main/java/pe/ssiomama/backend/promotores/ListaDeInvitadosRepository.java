package pe.ssiomama.backend.promotores;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ssiomama.backend.auth.Usuario;

public interface ListaDeInvitadosRepository extends JpaRepository<ListaDeInvitados, Long> {

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

    /** Una lista con su noche, su promotor y sus filas. */
    @Query("""
            select distinct l from ListaDeInvitados l
            join fetch l.evento
            join fetch l.promotor
            left join fetch l.invitados
            where l.id = :id
            """)
    java.util.Optional<ListaDeInvitados> buscarConInvitados(@Param("id") Long id);

    /** Personal con código de promotor, aunque todavía no tenga ninguna lista. */
    @Query("""
            select u from Usuario u
            where u.codigoDePromotor is not null
            order by u.nombre
            """)
    List<Usuario> buscarPromotores();
}
