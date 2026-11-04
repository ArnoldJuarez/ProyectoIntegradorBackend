package pe.ssiomama.backend.local;

/**
 * Qué es un elemento de seguridad del plano. Texto con {@code check} en la
 * tabla, como las incidencias: es una lista que crece (mañana, un rociador).
 */
public enum TipoDeElemento {
    EXTINTOR,
    BOTIQUIN,
    LUZ_EMERGENCIA,
    /** Pulsador manual de alarma. */
    ALARMA,
    /** Zona segura en caso de sismo: junto a un muro o columna, lejos del vidrio. */
    ZONA_SEGURA,
    /** Fuera del local: adonde va la gente una vez evacuada. */
    PUNTO_REUNION
}
