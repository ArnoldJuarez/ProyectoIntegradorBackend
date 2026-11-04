package pe.ssiomama.backend.noches;

/** Algo de la noche que el negocio no admite. El mensaje sale tal cual con un 409. */
public class NocheRechazada extends RuntimeException {

    public NocheRechazada(String mensaje) {
        super(mensaje);
    }
}
