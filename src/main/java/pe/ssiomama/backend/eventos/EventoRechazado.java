package pe.ssiomama.backend.eventos;

/**
 * Un cambio en la programación que no se puede hacer, con el motivo escrito para
 * quien lo intenta.
 *
 * <p>Mismo criterio que {@code CompraRechazada} y {@code CajaRechazada}: separa
 * lo que es una respuesta del negocio —«hay 24 entradas emitidas para esta
 * fecha»— de lo que es un fallo del programa. Se convierte en un 409 en
 * {@code ManejadorDeErrores} y su texto se enseña tal cual.
 */
public class EventoRechazado extends RuntimeException {

    public EventoRechazado(String motivo) {
        super(motivo);
    }
}
