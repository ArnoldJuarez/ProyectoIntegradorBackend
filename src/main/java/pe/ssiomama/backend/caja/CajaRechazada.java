package pe.ssiomama.backend.caja;

/**
 * La operación de caja no se puede hacer, y el motivo se le puede contar a quien
 * está en la barra.
 *
 * <p>Separa lo que es una respuesta del negocio —«la caja está cerrada», «esa
 * caja es de otra persona»— de lo que es un fallo. Las dos abortan la
 * transacción, pero solo la primera tiene un mensaje que sirva de algo fuera del
 * servidor, y por eso su texto <b>se enseña tal cual</b>: nada de ids, nombres
 * de tabla ni trazas.
 *
 * <p>Se convierte en un 409 en {@code ManejadorDeErrores}, igual que
 * {@code CompraRechazada}. 409 y no 400 porque la petición estaba bien formada:
 * lo que no admite lo que pide es el estado del mundo.
 */
public class CajaRechazada extends RuntimeException {

    public CajaRechazada(String motivo) {
        super(motivo);
    }
}
