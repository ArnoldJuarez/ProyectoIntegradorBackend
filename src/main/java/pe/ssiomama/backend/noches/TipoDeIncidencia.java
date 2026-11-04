package pe.ssiomama.backend.noches;

/**
 * De qué va una incidencia. Se guarda como texto con un {@code check} en la
 * tabla, no como tipo enum de PostgreSQL: es una lista que la sala querrá
 * ampliar, y añadir un valor a un enum nativo pide una migración con cuidado.
 */
public enum TipoDeIncidencia {
    /** Peleas, gente expulsada, objetos prohibidos. */
    SEGURIDAD,
    /** Alguien que se encuentra mal, una caída, una ambulancia. */
    SALUD,
    /** Cortes de luz, el sonido, una barra sin hielo, una rotura. */
    OPERACION,
    /** Quejas y reclamaciones del público. */
    CLIENTE,
    /** Ausencias, retrasos o problemas con el equipo. */
    PERSONAL,
    OTRA
}
