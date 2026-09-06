package pe.ssiomama.backend.entradas;

import java.security.SecureRandom;

/**
 * Lo que cuesta comprar, aparte del precio de la entrada.
 *
 * <p>Está aquí y no repartido por los controladores por una razón concreta: el
 * navegador enseña la comisión antes de comprar y el servidor la vuelve a
 * calcular al crear el pedido. Si fueran dos fórmulas, tarde o temprano dirían
 * cosas distintas y el cliente vería un total y pagaría otro.
 *
 * <p><b>El total nunca llega desde el navegador.</b> Lo que viaja es qué se
 * quiere y cuánto; el precio lo pone el servidor leyendo la base. Un total que
 * viene del cliente es un total que se puede editar con las herramientas del
 * navegador.
 */
public final class Tarifas {

    /**
     * Comisión de gestión: un 5% del subtotal.
     *
     * <p>Heredado del sistema anterior. Cambiarlo afecta solo a los pedidos
     * nuevos: los ya cerrados guardan su comisión en la fila, precisamente para
     * que una subida de tarifas no reescriba lo que alguien pagó.
     */
    private static final double PORCENTAJE_DE_GESTION = 0.05;

    /** Cuántos caracteres aleatorios lleva el código de pedido. */
    private static final int LARGO_DEL_CODIGO = 6;

    /**
     * Sin vocales ni caracteres que se confundan al dictarlos.
     *
     * <p>Fuera la O y el 0, la I y el 1, la S y el 5. Este código se lee por
     * teléfono cuando alguien llama porque no le llegó el correo, y cada par
     * ambiguo es una llamada que dura el doble.
     */
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRTUVWXYZ2346789";

    private static final SecureRandom AZAR = new SecureRandom();

    private Tarifas() {
        // Solo métodos estáticos.
    }

    /**
     * La comisión que corresponde a un subtotal.
     *
     * <p>Se redondea al céntimo más cercano. Truncar hacia abajo regalaría medio
     * céntimo por pedido, que da igual, pero haría que la comisión mostrada y la
     * cobrada difirieran en los importes justos.
     */
    public static int comisionDeGestion(int subtotalCentimos) {
        return (int) Math.round(subtotalCentimos * PORCENTAJE_DE_GESTION);
    }

    /**
     * Un código de pedido nuevo, del tipo {@code DSC-4KQ2P1}.
     *
     * <p>Va con {@link SecureRandom} y no con {@code Math.random()}: el código
     * es lo único que hace falta para ver las entradas de un pedido en
     * {@code /entradas/DSC-XXXXXX}, así que adivinarlo es ver las entradas de
     * otro. Con un generador predecible, quien acabe de comprar puede deducir
     * los códigos vecinos.
     *
     * <p>Aun así no es un secreto fuerte —seis caracteres—, y por eso la página
     * pública no enseña más que las entradas: ni el correo del comprador ni su
     * teléfono.
     */
    public static String nuevoCodigoDePedido() {
        StringBuilder codigo = new StringBuilder("DSC-");
        for (int i = 0; i < LARGO_DEL_CODIGO; i++) {
            codigo.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }
}
