package pe.ssiomama.backend.reservados;

/**
 * Qué es una zona — enum {@code TipoZona} de PostgreSQL.
 *
 * <p>Decide cómo se dibuja en el plano y en 3D. Antes se deducía del nombre, y
 * renombrar una zona la cambiaba de aspecto sin avisar.
 */
public enum TipoDeZona {
    PISTA,
    VIP,
    BARRA,
    OTRA
}
