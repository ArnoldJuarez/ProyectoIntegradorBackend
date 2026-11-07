package pe.ssiomama.backend.promotores;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a {@code "Invitado"}. */
public interface InvitadoRepository extends JpaRepository<Invitado, Long> {

    /** Una fila con su lista y la noche de la lista. */
    @Query("select i from Invitado i join fetch i.lista l join fetch l.evento where i.id = :id")
    Optional<Invitado> buscarConLista(@Param("id") Long id);
}
