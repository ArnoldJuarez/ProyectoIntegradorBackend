package pe.ssiomama.backend.auth;

import java.util.Set;

/**
 * Rol del usuario. Refleja el tipo enum nativo {@code Rol} de PostgreSQL.
 *
 * <p>Los nombres coinciden exactamente con las etiquetas del tipo en la base:
 * PostgreSQL rechaza cualquier valor que no esté en su lista.
 *
 * <h2>Cambió respecto al esquema anterior</h2>
 *
 * <p>No fue solo traducir: el rol {@code CUSTOMER} desapareció. Y tiene sentido
 * — un cliente no es personal de la sala, y sus datos viven en la tabla
 * {@code Cliente}, no en {@code Usuario}.
 *
 * <pre>
 *   ADMIN    → DIRECCION      DOOR  → PUERTA
 *   MANAGER  → GERENCIA       BAR   → BARRA
 *   PROMOTER → PROMOTOR       STAFF → PERSONAL
 *   CUSTOMER → (eliminado)
 * </pre>
 */
public enum Rol {
    /** Dirección del local. Ve todo. */
    DIRECCION,
    /** Jefatura de sala. */
    GERENCIA,
    /** Promotores: traen gente y cobran comisión. */
    PROMOTOR,
    /** Porteros: solo el escáner de entradas. */
    PUERTA,
    /** Barra: solo el punto de venta. */
    BARRA,
    /** Resto del personal, sin acceso al panel. */
    PERSONAL;

    /** Quién entra al panel de gestión. */
    public static final Set<Rol> PANEL = Set.of(DIRECCION, GERENCIA);

    /**
     * Quién da de alta personal y reparte roles.
     *
     * <p>Solo dirección: quien puede crear cuentas puede darse a sí mismo
     * cualquier permiso, así que esto no se delega en la jefatura de sala.
     */
    public static final Set<Rol> EQUIPO = Set.of(DIRECCION);

    /** Quién puede validar entradas en la puerta. */
    public static final Set<Rol> EN_LA_PUERTA = Set.of(DIRECCION, GERENCIA, PUERTA);

    /**
     * Quién puede cobrar en la barra.
     *
     * <p>Dirección y jefatura de sala entran también, y no por jerarquía: a las
     * tres de la mañana, cuando el de barra libra o se le cae la tablet, alguien
     * tiene que poder seguir cobrando.
     */
    public static final Set<Rol> EN_LA_BARRA = Set.of(DIRECCION, GERENCIA, BARRA);

    /** El nombre que espera Spring Security para un rol: {@code ROLE_DIRECCION}. */
    public String comoAutoridad() {
        return "ROLE_" + name();
    }
}
