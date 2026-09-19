package pe.ssiomama.backend.clientes;

/**
 * Por dónde sale una campaña. Refleja el enum nativo {@code CanalCampania} de
 * PostgreSQL.
 *
 * <p>No es lo mismo que {@link CanalDeInteraccion}: aquel dice por dónde llegó
 * <b>un cliente</b> —incluido «en el local», que no es un canal de envío—, y
 * este por dónde sale <b>un mensaje</b> de la casa.
 */
public enum CanalDeCampania {
    CORREO,
    SMS,
    WHATSAPP,
    PUSH;

    public String etiqueta() {
        return switch (this) {
            case CORREO -> "Correo";
            case SMS -> "SMS";
            case WHATSAPP -> "WhatsApp";
            case PUSH -> "Notificación";
        };
    }
}
