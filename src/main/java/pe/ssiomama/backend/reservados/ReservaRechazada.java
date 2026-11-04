package pe.ssiomama.backend.reservados;

/**
 * Una operación con reservas que el negocio no admite: el box ya está pedido,
 * el grupo no cabe, se intenta sentar a quien canceló.
 *
 * <p>El mensaje va escrito para quien lo lee —el cliente en la web o quien
 * atiende el teléfono en el panel— y sale tal cual con un 409.
 */
public class ReservaRechazada extends RuntimeException {

    public ReservaRechazada(String mensaje) {
        super(mensaje);
    }
}
