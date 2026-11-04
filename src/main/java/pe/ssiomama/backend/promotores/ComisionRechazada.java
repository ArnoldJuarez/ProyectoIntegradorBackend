package pe.ssiomama.backend.promotores;

/** Una liquidación que no procede. El mensaje sale tal cual con un 409. */
public class ComisionRechazada extends RuntimeException {

    public ComisionRechazada(String mensaje) {
        super(mensaje);
    }
}
