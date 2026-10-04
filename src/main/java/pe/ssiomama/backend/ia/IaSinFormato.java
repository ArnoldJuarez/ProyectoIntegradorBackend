package pe.ssiomama.backend.ia;

/** El modelo no devolvió un JSON que cumpla el esquema, ni a la tercera. */
public class IaSinFormato extends RuntimeException {

    public IaSinFormato(String mensaje) {
        super(mensaje);
    }
}
