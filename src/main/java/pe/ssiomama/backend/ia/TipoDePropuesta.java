package pe.ssiomama.backend.ia;

/** Qué propone un agente — enum {@code TipoPropuesta} de PostgreSQL. */
public enum TipoDePropuesta {
    TEXTO_CAMPANIA("Textos de promoción"),
    CAMPANIA("Campaña de CRM"),
    ORDEN_COMPRA("Pedido a proveedor"),
    CAMBIO_PRECIO("Cambio de precios"),
    INFORME("Informe");

    private final String nombre;

    TipoDePropuesta(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }
}
