package pe.ssiomama.backend.clientes;

/**
 * Por dónde ocurrió. Refleja el enum nativo {@code InteractionChannel}.
 *
 * <p>No se reutiliza el canal de campañas: una interacción puede nacer en la
 * puerta del local, y una campaña nunca.
 */
public enum CanalDeInteraccion {
    WEB,
    WHATSAPP,
    CORREO,
    TELEFONO,
    INSTAGRAM,
    EN_PERSONA
}
