package pe.ssiomama.backend.clientes;

/**
 * Una operación del CRM que no se puede hacer, con el motivo escrito para quien
 * la intenta.
 *
 * <p>Mismo criterio que {@code CompraRechazada}, {@code CajaRechazada},
 * {@code EventoRechazado} y {@code AlmacenRechazado}: separa una respuesta del
 * negocio de un fallo del programa. Se convierte en un 409 en
 * {@code ManejadorDeErrores} y su texto se enseña tal cual.
 */
public class CrmRechazado extends RuntimeException {

    public CrmRechazado(String motivo) {
        super(motivo);
    }
}
