package pe.ssiomama.backend.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * El correo se guarda tal cual se escribió, así que la búsqueda ignora
     * mayúsculas: nadie recuerda si se registró como Admin@ o admin@.
     */
    Optional<Usuario> findByEmailIgnoreCase(String email);

    /** El promotor de un código. Los códigos se guardan en mayúsculas. */
    Optional<Usuario> findByCodigoDePromotor(String codigo);
}
