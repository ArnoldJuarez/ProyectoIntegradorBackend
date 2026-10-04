package pe.ssiomama.backend.ia;

import java.util.Arrays;
import java.util.Optional;

/**
 * Qué agente corrió — enum {@code ClaveAgente} de PostgreSQL.
 *
 * <p>Las etiquetas de la base llevan guiones ({@code crm-segmenter}), que no
 * caben en un nombre de enum de Java. Por eso cada valor guarda la suya, y es
 * la misma que viaja en la URL: {@code POST /api/ia/agentes/crm-segmenter}.
 */
public enum ClaveDeAgente {
    MARKETING("marketing", "Marketing"),
    SEGMENTADOR_CRM("crm-segmenter", "Segmentador de CRM"),
    INVENTARIO("inventory", "Inventario"),
    INVENTARIO_ML("inventory-ml", "Inventario (con modelo)"),
    PRECIOS("pricing", "Precios"),
    INFORMES("reporter", "Informe post-evento"),
    CONSERJE("concierge", "Conserje");

    private final String etiqueta;
    private final String nombre;

    ClaveDeAgente(String etiqueta, String nombre) {
        this.etiqueta = etiqueta;
        this.nombre = nombre;
    }

    /** Como está escrito en la base y en la URL. */
    public String etiqueta() {
        return etiqueta;
    }

    /** Como se lee en pantalla. */
    public String nombre() {
        return nombre;
    }

    public static Optional<ClaveDeAgente> deEtiqueta(String etiqueta) {
        return Arrays.stream(values()).filter(c -> c.etiqueta.equals(etiqueta)).findFirst();
    }
}
