package pe.ssiomama.backend.inventario;

/**
 * Una operación de almacén que no se puede hacer, con el motivo escrito para
 * quien la intenta.
 *
 * <p>Mismo criterio que {@code CompraRechazada}, {@code CajaRechazada} y
 * {@code EventoRechazado}: separa una respuesta del negocio de un fallo del
 * programa. Se convierte en un 409 en {@code ManejadorDeErrores} y su texto se
 * enseña tal cual.
 */
public class AlmacenRechazado extends RuntimeException {

    public AlmacenRechazado(String motivo) {
        super(motivo);
    }
}
