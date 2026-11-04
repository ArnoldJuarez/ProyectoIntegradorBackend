package pe.ssiomama.backend.reservados;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Zona física del local: pista, barra, reservados, terraza — tabla
 * {@code "Zone"}.
 *
 * <p>Antes era un texto suelto repetido en cada mesa. Como tabla propia lleva su
 * aforo parcial y sirve para agrupar los reservados por sitio.
 *
 * <p>Las columnas del plano ({@code x}, {@code y}, {@code width}, {@code depth},
 * {@code height}, {@code hue}) no se mapean todavía: son de la vista 3D, que
 * está aparcada. {@code validate} solo exige que las columnas mapeadas existan.
 */
@Entity
@Table(name = "Zona")
public class Zona {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Aforo de la zona. 0 = no se controla por separado. */
    @Column(name = "aforo", nullable = false)
    private int aforo;

    /** Orden en que se pinta en el plano y en los listados. */
    @Column(name = "orden", nullable = false)
    private int orden;

    // ── El plano del local ────────────────────────────────────────

    /** Solo lectura: las zonas no cambian de sala desde la aplicación. */
    @Column(name = "salaId", nullable = false, insertable = false, updatable = false)
    private String salaId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo", nullable = false)
    private TipoDeZona tipo;

    /** Rectángulo en metros, con el origen en la esquina del plano. */
    @Column(name = "x", nullable = false)
    private double x;

    @Column(name = "y", nullable = false)
    private double y;

    @Column(name = "ancho", nullable = false)
    private double ancho;

    @Column(name = "fondo", nullable = false)
    private double fondo;

    /** Altura libre. Solo la usa la vista 3D. */
    @Column(name = "alto", nullable = false)
    private double alto;

    /** Tono HSL en el plano y en 3D. -1 = derivarlo del tipo. */
    @Column(name = "tono", nullable = false)
    private int tono;

    /** En qué planta está: 0 la baja, 1 la alta. */
    @Column(name = "piso", nullable = false)
    private int piso;

    protected Zona() {
        // Hibernate necesita un constructor sin argumentos.
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public int getAforo() {
        return aforo;
    }

    public int getOrden() {
        return orden;
    }

    public String getSalaId() {
        return salaId;
    }

    public TipoDeZona getTipo() {
        return tipo;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getAncho() {
        return ancho;
    }

    public double getFondo() {
        return fondo;
    }

    public double getAlto() {
        return alto;
    }

    public int getTono() {
        return tono;
    }

    public int getPiso() {
        return piso;
    }

    /** Cambia dónde está y qué es. El nombre y el orden no se tocan desde el plano. */
    public void dibujar(
            TipoDeZona tipo, double x, double y, double ancho, double fondo, double alto, int aforo) {
        this.tipo = tipo;
        this.x = x;
        this.y = y;
        this.ancho = ancho;
        this.fondo = fondo;
        this.alto = alto;
        this.aforo = aforo;
    }

    /** Igual, cambiando también de planta. */
    public void dibujar(
            TipoDeZona tipo, double x, double y, double ancho, double fondo, double alto, int aforo, int piso) {
        dibujar(tipo, x, y, ancho, fondo, alto, aforo);
        this.piso = piso;
    }
}
