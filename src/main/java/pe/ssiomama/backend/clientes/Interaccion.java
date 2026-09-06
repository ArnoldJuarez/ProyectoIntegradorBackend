package pe.ssiomama.backend.clientes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Algo que pasó con un cliente — tabla {@code "Interaction"}.
 *
 * <p>Es el historial que hace útil al CRM: sin él, la ficha de un cliente son
 * cuatro contadores sin contexto. Aquí caen las compras, las visitas, las
 * campañas que se le enviaron y las notas que escribe el personal.
 *
 * <p>La columna {@code metadata} ({@code jsonb}) no se mapea todavía: no hay
 * ninguna pantalla que la lea. {@code ddl-auto: validate} comprueba que las
 * columnas mapeadas existan, no que estén todas mapeadas, así que dejarla fuera
 * no rompe nada y evita arrastrar el manejo de JSON antes de necesitarlo.
 */
@Entity
@Table(name = "Interaccion")
public class Interaccion {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clienteId", nullable = false)
    private Cliente cliente;

    /** Recuerda pasar el valor como parámetro en las consultas, nunca como literal. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo", nullable = false)
    private TipoDeInteraccion tipo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "canal")
    private CanalDeInteraccion canal;

    @Column(name = "resumen", nullable = false)
    private String resumen;

    @Column(name = "ocurrioEn", nullable = false)
    private Instant ocurrioEn;

    protected Interaccion() {
        // Hibernate necesita un constructor sin argumentos.
    }

    public static Interaccion registrar(
            Cliente cliente, TipoDeInteraccion tipo, CanalDeInteraccion canal, String resumen) {
        Interaccion i = new Interaccion();
        i.id = UUID.randomUUID().toString();
        i.cliente = cliente;
        i.tipo = tipo;
        i.canal = canal;
        i.resumen = resumen;
        i.ocurrioEn = Instant.now();
        return i;
    }

    public String getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public TipoDeInteraccion getTipo() {
        return tipo;
    }

    public CanalDeInteraccion getCanal() {
        return canal;
    }

    public String getResumen() {
        return resumen;
    }

    public Instant getOcurrioEn() {
        return ocurrioEn;
    }
}
