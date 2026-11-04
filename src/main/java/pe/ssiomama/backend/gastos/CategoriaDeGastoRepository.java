package pe.ssiomama.backend.gastos;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a {@code "CategoriaGasto"}. */
public interface CategoriaDeGastoRepository extends JpaRepository<CategoriaDeGasto, String> {

    List<CategoriaDeGasto> findAllByOrderByNombreAsc();

    Optional<CategoriaDeGasto> findFirstByNombreIgnoreCase(String nombre);
}
