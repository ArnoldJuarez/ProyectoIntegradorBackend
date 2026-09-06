package pe.ssiomama.backend.eventos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Un acceso físico de la sala — tabla {@code "Puerta"}.
 *
 * <p>Existe para poder responder <b>por dónde</b> entró la gente. Antes el
 * escaneo guardaba el texto fijo «Puerta principal», que servía de poco: con
 * tres accesos y una cola que se atasca en uno, saber cuál es el que se atasca
 * es justo lo que hace falta.
 *
 * <p>Las salidas de emergencia están en la misma tabla porque son puertas de la
 * sala igual que las demás y el plano las necesita, pero <b>no se escanea por
 * ellas</b>: son de salida. De ahí {@link #esSalidaEmergencia}, que el buscador
 * de la puerta de entrada usa para descartarlas.
 *
 * <p>La geometría del plano ({@code x}, {@code y}, {@code ancho}) está en la
 * tabla y aquí no: la pantalla del local todavía no está migrada, y mapear
 * columnas que nadie lee solo añade sitios donde equivocarse.
 */
@Entity
@Table(name = "Puerta")
public class Puerta {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salaId", nullable = false)
    private Sala sala;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Si es de evacuación. Por estas no se entra. */
    @Column(name = "esSalidaEmergencia", nullable = false)
    private boolean esSalidaEmergencia;

    /**
     * Cuántas personas pasan por minuto cuando hay cola.
     *
     * <p>Es el dato que convierte «hay 200 esperando» en «son veinte minutos»,
     * y el que dice si compensa abrir un segundo acceso.
     */
    @Column(name = "caudalPorMinuto", nullable = false)
    private int caudalPorMinuto;

    /** Una puerta cerrada por obra sigue en la tabla, pero no se usa. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Puerta() {
        // Hibernate necesita un constructor sin argumentos.
    }

    public String getId() {
        return id;
    }

    public Sala getSala() {
        return sala;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isEsSalidaEmergencia() {
        return esSalidaEmergencia;
    }

    public int getCaudalPorMinuto() {
        return caudalPorMinuto;
    }

    public boolean isActivo() {
        return activo;
    }

    /** ¿Se puede escanear por aquí? Solo las abiertas que no son de evacuación. */
    public boolean admiteEntradas() {
        return activo && !esSalidaEmergencia;
    }
}
