package pe.ssiomama.backend.auth;

import java.util.Set;

/**
 * Rol del usuario. Refleja el tipo enum nativo {@code Role} de PostgreSQL.
 *
 * <p>Los nombres tienen que coincidir exactamente con las etiquetas del tipo en
 * la base: PostgreSQL rechaza cualquier valor que no esté en su lista.
 */
public enum Rol {
    ADMIN,
    MANAGER,
    PROMOTER,
    DOOR,
    BAR,
    STAFF,
    CUSTOMER;

    /** Quién entra al panel de gestión. */
    public static final Set<Rol> PANEL = Set.of(ADMIN, MANAGER);

    /** Quién puede validar entradas en la puerta. */
    public static final Set<Rol> PUERTA = Set.of(ADMIN, MANAGER, DOOR);

    /**
     * Quién puede cobrar en la barra.
     *
     * <p>Dirección y jefa de sala entran también, y no por jerarquía: a las tres
     * de la mañana, cuando el de barra libra o se le cae la tablet, alguien
     * tiene que poder seguir cobrando.
     */
    public static final Set<Rol> BARRA = Set.of(ADMIN, MANAGER, BAR);

    /** El nombre que espera Spring Security para un rol: {@code ROLE_ADMIN}. */
    public String comoAutoridad() {
        return "ROLE_" + name();
    }
}
