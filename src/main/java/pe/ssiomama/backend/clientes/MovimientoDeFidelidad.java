package pe.ssiomama.backend.clientes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Un apunte de puntos — tabla {@code "MovimientoFidelidad"}.
 *
 * <p>Es un <b>libro de asientos</b>, no un saldo: cada línea dice cuántos puntos
 * se movieron y por qué, y el saldo es la suma. Guardar solo el saldo sería más
 * cómodo hasta el día en que un cliente pregunte de dónde salen sus puntos, o
 * haya que corregir un abono mal hecho sin poder deshacerlo.
 *
 * <p>{@link #puntos} puede ser negativo: canjear es un apunte más.
 */
@Entity
@Table(name = "MovimientoFidelidad")
public class MovimientoDeFidelidad {

    /**
     * Cuántos céntimos hay que gastar para ganar un punto.
     *
     * <p>Uno por sol. Es la regla del sistema anterior y se mantiene: cambiarla
     * ahora descuadraría los saldos que la gente ya tiene.
     */
    private static final int CENTIMOS_POR_PUNTO = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clienteId", nullable = false)
    private Cliente cliente;

    @Column(name = "puntos", nullable = false)
    private int puntos;

    /** En texto y para leer: «Compra DSC-4KQ2P1». */
    @Column(name = "motivo", nullable = false)
    private String motivo;

    /**
     * El pedido que lo originó, si lo hubo.
     *
     * <p>Se guarda el id suelto y no una relación a {@code Pedido} a propósito:
     * eso ataría el paquete de clientes al de entradas en las dos direcciones, y
     * el saldo de puntos no necesita saber nada del pedido más allá de a cuál
     * apuntar. Un ajuste manual no tiene pedido y la columna admite nulos.
     */
    @Column(name = "pedidoId")
    private Long pedidoId;

    @Column(name = "creadoEn", nullable = false)
    private Instant creadoEn;

    protected MovimientoDeFidelidad() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /**
     * Los puntos que deja una compra: uno por sol gastado.
     *
     * <p>Se redondea al entero más cercano y no hacia abajo, porque truncar
     * regala al local el céntimo de cada compra y a nadie le cuadra ver 24
     * puntos por 24,50 soles.
     */
    public static MovimientoDeFidelidad porCompra(
            Cliente cliente, Long idDelPedido, String codigoDelPedido, int totalCentimos) {
        MovimientoDeFidelidad m = new MovimientoDeFidelidad();
        m.cliente = cliente;
        m.puntos = Math.round((float) totalCentimos / CENTIMOS_POR_PUNTO);
        m.motivo = "Compra " + codigoDelPedido;
        m.pedidoId = idDelPedido;
        m.creadoEn = Instant.now();
        return m;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public int getPuntos() {
        return puntos;
    }

    public String getMotivo() {
        return motivo;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
