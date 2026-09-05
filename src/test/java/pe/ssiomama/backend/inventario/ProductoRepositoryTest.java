package pe.ssiomama.backend.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Prueba el mapeo contra la base <b>real</b>, la misma que usa el Next.js.
 *
 * <p>{@code replace = NONE} es la parte importante: por defecto {@code
 * @DataJpaTest} sustituye el datasource por uno en memoria, y entonces esto no
 * probaría nada de lo que interesa. Lo que se quiere verificar es justamente que
 * las entidades cuadran con las tablas que creó Prisma.
 *
 * <p>Que el contexto llegue a arrancar ya es medio test: con {@code
 * ddl-auto: validate}, si un nombre de tabla, un nombre de columna o un tipo no
 * coinciden, Hibernate falla al iniciar en vez de dar resultados raros.
 *
 * <p>Necesita PostgreSQL arriba y {@code DB_PASSWORD} en el entorno.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductoRepositoryTest {

    @Autowired
    private ProductoRepository productos;

    @Test
    @DisplayName("las entidades cuadran con el esquema de Prisma y devuelven datos")
    void leeProductosDeLaBaseReal() {
        List<Producto> todos = productos.findAll();

        assertThat(todos)
                .as("la base de la discoteca tiene productos cargados por el seed")
                .isNotEmpty();

        Producto uno = todos.getFirst();
        assertThat(uno.getId()).isNotBlank();
        assertThat(uno.getSku()).isNotBlank();
        assertThat(uno.getNombre()).isNotBlank();
    }

    /**
     * El enum nativo de PostgreSQL es el mapeo que más fácil se rompe: si
     * {@code @JdbcTypeCode(NAMED_ENUM)} no estuviera, esto reventaría al leer.
     */
    @Test
    @DisplayName("lee el enum nativo ProductUnit de PostgreSQL")
    void leeElEnumNativo() {
        assertThat(productos.findAll())
                .isNotEmpty()
                .allSatisfy(p -> assertThat(p.getUnidad()).isNotNull());
    }

    @Test
    @DisplayName("la relacion con la categoria resuelve")
    void resuelveLaCategoria() {
        Producto uno = productos.findAll().getFirst();
        // Al ser LAZY, esto fuerza la carga: si el @JoinColumn apuntara a una
        // columna que no existe, aqui es donde se ve.
        assertThat(uno.getCategoria()).isNotNull();
        assertThat(uno.getCategoria().getNombre()).isNotBlank();
    }

    @Test
    @DisplayName("la consulta derivada por campo activo funciona")
    void filtraPorActivo() {
        assertThat(productos.findByActivoTrueOrderByNombreAsc())
                .isNotEmpty()
                .allSatisfy(p -> assertThat(p.isActivo()).isTrue());
    }
}
