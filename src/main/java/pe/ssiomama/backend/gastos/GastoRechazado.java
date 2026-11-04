package pe.ssiomama.backend.gastos;

/** Un gasto que el negocio no admite. El mensaje sale tal cual con un 409. */
public class GastoRechazado extends RuntimeException {

    public GastoRechazado(String mensaje) {
        super(mensaje);
    }
}
