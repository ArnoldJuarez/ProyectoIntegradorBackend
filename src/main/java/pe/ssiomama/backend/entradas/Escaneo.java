package pe.ssiomama.backend.entradas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.eventos.Evento;
import pe.ssiomama.backend.eventos.Puerta;

/**
 * El momento en que una entrada pasó por la puerta — tabla {@code "Acceso"}.
 *
 * <p>Es una tabla aparte y no un par de columnas en la entrada porque registra
 * un <b>hecho</b>: quién escaneó, por qué puerta y a qué hora. Eso es lo que
 * permite responder cuando alguien discute en la entrada, y lo que da el aforo
 * en vivo.
 *
 * <p>{@code entradaId} es único: la base impide dos escaneos de la misma entrada
 * aunque dos porteros lo intenten a la vez. Esa restricción es la que de verdad
 * evita que alguien pase dos veces; la comprobación en el código solo sirve para
 * dar un mensaje entendible.
 */
@Entity
@Table(name = "Acceso")
public class Escaneo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entradaId", nullable = false, unique = true)
    private Entrada entrada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eventoId", nullable = false)
    private Evento evento;

    /** Qué portero lo escaneó. Se guarda para poder preguntarle. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escaneadoPorId", nullable = false)
    private Usuario escaneadoPor;

    /**
     * Por dónde entró. Puede faltar: si la sala no tiene puertas registradas se
     * deja a nulo antes que rechazar a alguien que sí tiene su entrada.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "puertaId")
    private Puerta puerta;

    @Column(name = "escaneadoEn", nullable = false)
    private Instant escaneadoEn;

    protected Escaneo() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /**
     * Registra el paso de una entrada por la puerta.
     *
     * <p>El id lo pone la base al insertar (columna identity).
     *
     * @param puerta por dónde pasó, o {@code null} si la sala no tiene ninguna
     */
    public static Escaneo registrar(
            Entrada entrada, Usuario portero, Puerta puerta, Instant cuando) {
        Escaneo e = new Escaneo();
        e.entrada = entrada;
        e.evento = entrada.getEvento();
        e.escaneadoPor = portero;
        e.puerta = puerta;
        e.escaneadoEn = cuando;
        return e;
    }

    public Long getId() {
        return id;
    }

    public Entrada getEntrada() {
        return entrada;
    }

    public Evento getEvento() {
        return evento;
    }

    public Usuario getEscaneadoPor() {
        return escaneadoPor;
    }

    public Puerta getPuerta() {
        return puerta;
    }

    public Instant getEscaneadoEn() {
        return escaneadoEn;
    }
}
