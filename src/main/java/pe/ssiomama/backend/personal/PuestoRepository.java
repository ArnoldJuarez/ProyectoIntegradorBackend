package pe.ssiomama.backend.personal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a {@code "Puesto"}. */
public interface PuestoRepository extends JpaRepository<Puesto, Long> {

    List<Puesto> findAllByOrderByNombreAsc();

    /** Las barras del local, las activas primero. */
    List<Puesto> findByEsDeBarraTrueOrderByActivoDescNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);
}
