package pe.ssiomama.backend.entradas;

/**
 * La compra no se puede hacer, y el motivo se le puede contar al comprador.
 *
 * <p>Existe para separar dos cosas que no son iguales: «quedan dos entradas y
 * pides tres» es una respuesta legítima del negocio, y «se cayó la base de
 * datos» es un fallo. Las dos abortan la transacción, pero solo la primera tiene
 * un mensaje que sirva de algo fuera del servidor.
 *
 * <p>Por eso el texto que lleva <b>se enseña tal cual</b> al comprador, y por
 * eso no debe contener nada interno: ni ids, ni nombres de tabla, ni trazas.
 *
 * <p>Se convierte en un 409 en {@code ManejadorDeErrores}. 409 y no 400 porque
 * la petición estaba bien formada: es el estado del mundo el que no admite lo
 * que pide —se agotaron mientras rellenaba el formulario—.
 */
public class CompraRechazada extends RuntimeException {

    public CompraRechazada(String motivo) {
        super(motivo);
    }
}
