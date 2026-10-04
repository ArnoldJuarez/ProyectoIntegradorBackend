package pe.ssiomama.backend.ia;

/** No hay nada sobre lo que trabajar: sin noches publicadas no hay promo que redactar. */
public class AgenteSinDatos extends RuntimeException {

    public AgenteSinDatos(String mensaje) {
        super(mensaje);
    }
}
