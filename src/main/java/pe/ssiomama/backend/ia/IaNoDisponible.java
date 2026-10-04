package pe.ssiomama.backend.ia;

/**
 * Falta la clave, el proveedor no contesta, la clave no vale o el modelo no
 * existe.
 *
 * <p>El mensaje está escrito para quien lo lee en el panel y dice qué mirar: no
 * es lo mismo «no hay conexión» que «esa clave no vale».
 */
public class IaNoDisponible extends RuntimeException {

    public IaNoDisponible(String mensaje) {
        super(mensaje);
    }
}
