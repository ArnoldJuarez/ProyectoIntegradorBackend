package pe.ssiomama.backend.ia;

/**
 * Por qué corrió un agente — enum {@code DisparadorAgente} de PostgreSQL.
 *
 * <p>{@link #BANCO_PRUEBAS} existe para separar el laboratorio de la operación:
 * decenas de corridas seguidas contra modelos distintos inflarían el coste del
 * negocio y contaminarían la comparación entre modelos.
 */
public enum Disparador {
    MANUAL("manual"),
    PROGRAMADO("scheduled"),
    CHAT("chat"),
    BANCO_PRUEBAS("benchmark");

    private final String etiqueta;

    Disparador(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
