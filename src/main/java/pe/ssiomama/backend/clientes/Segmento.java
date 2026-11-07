package pe.ssiomama.backend.clientes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Un grupo de clientes definido por una condición — tabla {@code "Segmento"}.
 *
 * <h2>Guarda la regla, no la lista</h2>
 *
 * <p>Un segmento no contiene clientes: contiene la condición que los describe.
 * «Dormidos 60 días» no es una lista de treinta nombres, es
 * {@code sin venir desde hace más de 60 días}, y se evalúa cada vez que alguien
 * lo mira.
 *
 * <p>La diferencia importa el día de la campaña. Con una lista congelada, quien
 * volvió ayer sigue dentro y recibe un «te echamos de menos»; con la regla, salió
 * solo en cuanto compró.
 *
 * <h2>El JSON va mapeado como texto</h2>
 *
 * <p>{@code reglasJson} es una columna {@code jsonb} y aquí se mapea con
 * {@link SqlTypes#JSON}, que le dice a Hibernate que al otro lado hay JSON y no
 * un {@code varchar}. Se deja como {@code String} y lo interpreta
 * {@link ReglaDeSegmento} en el servicio: así una regla de un tipo que este
 * sistema todavía no entiende se puede leer y enseñar como «no reconocida», en
 * vez de reventar al cargar la entidad.
 */
@Entity
@Table(name = "Segmento")
public class Segmento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reglasJson", nullable = false)
    private String reglasJson;

    /**
     * Si se recalcula solo.
     *
     * <p>Todos los del sistema lo son. La columna existe para el día que haga
     * falta un segmento a mano —«los cincuenta del sorteo de septiembre»—, que
     * es una lista de verdad y no una regla.
     */
    @Column(name = "esDinamico", nullable = false)
    private boolean esDinamico;

    @Column(name = "actualizadoEn", nullable = false)
    private Instant actualizadoEn;

    protected Segmento() {
        // Hibernate necesita un constructor sin argumentos.
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getReglasJson() {
        return reglasJson;
    }

    public boolean isEsDinamico() {
        return esDinamico;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
