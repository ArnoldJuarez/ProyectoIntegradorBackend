package pe.ssiomama.backend.entradas;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

/**
 * Vuelve a intentar lo que PostgreSQL aborta por pisarse con otra transacción.
 *
 * <h2>Por qué hace falta</h2>
 *
 * <p>Una transacción {@code SERIALIZABLE} no espera a la otra: las deja correr y
 * al confirmar aborta a una de las dos con el error {@code 40001},
 * <i>«could not serialize access due to read/write dependencies»</i>. No es un
 * fallo del programa —es cómo funciona ese nivel de aislamiento— y la respuesta
 * correcta es <b>repetir la operación</b>, no dar error.
 *
 * <p>Sin esto, dos personas comprando a la vez producirían un 500 para una de
 * las dos aunque hubiera entradas de sobra. Con esto, la segunda se repite,
 * vuelve a leer el cupo ya actualizado y decide otra vez: o compra, o recibe un
 * «solo quedan 2» que sí se entiende.
 *
 * <h2>Por qué espera antes de repetir</h2>
 *
 * <p>Con dos compradores no haría falta: la que gana ya confirmó, así que
 * repetir de inmediato lee el estado nuevo y acierta. Pero cuando son veinte a
 * la vez —el minuto en que se abre la venta de una noche—, repetir sin esperar
 * hace que los reintentos vuelvan a chocar entre ellos, y hay compras que se
 * quedan fuera <b>habiendo entradas</b>.
 *
 * <p>Se midió: veinte compradores contra ocho entradas, sin espera, vendían
 * seis. La espera creciente y al azar los separa en el tiempo, y entonces se
 * venden las ocho. Está en {@code CompraConcurrenteTest}.
 *
 * <h2>Por qué es un componente aparte</h2>
 *
 * <p>Porque el reintento tiene que envolver la transacción <b>desde fuera</b>.
 * Metido dentro del método transaccional no serviría de nada: cuando salta el
 * conflicto, la transacción ya está condenada y lo que se haga dentro se
 * descarta igual. Y tampoco puede ser un método de {@code Compra} llamándose a
 * sí mismo, porque una llamada interna se salta el proxy de Spring y no abriría
 * transacción nueva.
 */
@Component
public class Reintentos {

    /**
     * Cuántas veces se intenta en total antes de rendirse.
     *
     * <p>Seis, que con la espera creciente da margen de sobra para un pico de
     * veinte compras simultáneas sin que la petición llegue a tardar medio
     * segundo.
     */
    private static final int INTENTOS = 6;

    /**
     * La base de la espera, en milisegundos.
     *
     * <p>Se dobla en cada intento —5, 10, 20, 40, 80— y de ese tramo se toma un
     * valor al azar. Lo del azar es la parte importante: si todos esperaran lo
     * mismo, volverían a salir juntos y chocarían otra vez, que es exactamente
     * lo que se quiere evitar.
     */
    private static final int ESPERA_BASE_MS = 5;

    /**
     * Ejecuta la operación y la repite si la base la aborta por conflicto.
     *
     * <p>{@link ConcurrencyFailureException} es la familia entera de conflictos
     * de concurrencia de Spring: el 40001 de serializable, los interbloqueos y
     * los fallos de bloqueo optimista. Todos se resuelven igual — repetir.
     *
     * <p>Lo que <b>no</b> se repite es una {@link CompraRechazada}: «se
     * agotaron» o «es de otra fecha» darían exactamente el mismo resultado al
     * segundo intento, y repetirlas solo retrasaría el mensaje que la persona
     * necesita leer. Como es una excepción distinta, sale por sí sola sin que
     * haya que filtrarla.
     */
    public <T> T conReintento(Supplier<T> operacion) {
        ConcurrencyFailureException ultimo = null;

        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                return operacion.get();
            } catch (ConcurrencyFailureException conflicto) {
                ultimo = conflicto;
                if (intento < INTENTOS) {
                    esperarUnPoco(intento);
                }
            }
        }

        // Agotados los intentos, se propaga. Que salga un 500 aquí es correcto:
        // significa que la base lleva rato sin poder resolver un conflicto, y
        // eso es un problema de verdad, no una compra que llegó tarde.
        throw ultimo;
    }

    /** Espera creciente y al azar, para que los reintentos no vuelvan a coincidir. */
    private void esperarUnPoco(int intento) {
        int tope = ESPERA_BASE_MS * (1 << (intento - 1));
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(tope) + 1L);
        } catch (InterruptedException cortado) {
            // Se vuelve a marcar el hilo como interrumpido y se deja de
            // reintentar: tragarse la interrupción es como se acaban teniendo
            // hilos que ignoran una parada del servidor.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reintento interrumpido", cortado);
        }
    }
}
