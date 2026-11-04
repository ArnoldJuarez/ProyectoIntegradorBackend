package pe.ssiomama.backend.promotores;

/** Un cambio en una lista de invitados que el negocio no admite. Sale tal cual con un 409. */
public class ListaRechazada extends RuntimeException {

    public ListaRechazada(String mensaje) {
        super(mensaje);
    }
}
