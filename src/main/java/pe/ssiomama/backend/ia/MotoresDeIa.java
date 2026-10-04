package pe.ssiomama.backend.ia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Los dos motores de la capa de IA.
 *
 * <p><b>Números</b> es el de los agentes de fondo: pedidos, precios. <b>Prosa</b>
 * es el del conserje y de todo lo que va a leer una persona. Medido en el
 * sistema anterior: un modelo pequeño acierta los números y el formato, pero su
 * español suena a traducción automática, así que la prosa puede ir a otro. Si
 * no se configura el segundo, cae en el primero.
 */
@Component
public class MotoresDeIa {

    private static final Logger log = LoggerFactory.getLogger(MotoresDeIa.class);

    private final Motor numeros;
    private final Motor prosa;
    private final double precioEntrada;
    private final double precioSalida;

    public MotoresDeIa(
            @Value("${ssiomama.ia.base-url}") String baseUrl,
            @Value("${ssiomama.ia.modelo}") String modelo,
            @Value("${ssiomama.ia.api-key}") String apiKey,
            @Value("${ssiomama.ia.chat-base-url}") String chatBaseUrl,
            @Value("${ssiomama.ia.chat-modelo}") String chatModelo,
            @Value("${ssiomama.ia.chat-api-key}") String chatApiKey,
            @Value("${ssiomama.ia.precio-entrada}") double precioEntrada,
            @Value("${ssiomama.ia.precio-salida}") double precioSalida) {
        this.numeros = new Motor(baseUrl, apiKey, modelo);
        this.prosa = new Motor(chatBaseUrl, chatApiKey, chatModelo);
        this.precioEntrada = precioEntrada;
        this.precioSalida = precioSalida;
        // Arranca igual: el resto del sistema no depende de la IA. Pero que se vea.
        if (apiKey.isBlank() || chatApiKey.isBlank()) {
            log.warn("La IA está apagada: falta ssiomama.ia.api-key en application-local.yml (o AI_API_KEY).");
        }
    }

    public Motor numeros() {
        return numeros;
    }

    public Motor prosa() {
        return prosa;
    }

    /**
     * Lo que cuesta una llamada, en céntimos.
     *
     * <p>Los precios van en céntimos por millón de tokens. En las capas
     * gratuitas es cero, y el registro sigue sirviendo para saber cuánto trabajo
     * costó cada respuesta.
     */
    public int costeCentimos(int tokensEntrada, int tokensSalida) {
        return (int) Math.round(tokensEntrada / 1_000_000.0 * precioEntrada
                + tokensSalida / 1_000_000.0 * precioSalida);
    }
}
