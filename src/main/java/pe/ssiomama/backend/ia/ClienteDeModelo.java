package pe.ssiomama.backend.ia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Habla con el modelo por la API de chat compatible con OpenAI.
 *
 * <p>Sin SDK: es un {@code POST /chat/completions} con JSON, y un cliente HTTP
 * propio es menos dependencia que mantener y deja ver exactamente qué se manda.
 */
@Component
public class ClienteDeModelo {

    /** Tres llamadas como mucho para conseguir un JSON que cumpla el esquema. */
    private static final int INTENTOS = 3;

    /**
     * Los proveedores que validan el esquema en su lado (Groq) devuelven un 400
     * en vez de la respuesta mal formada, y lo redactan de varias formas. Ese
     * rechazo cuenta como un intento más, igual que un fallo de validación
     * local: si no, el modelo local tendría tres oportunidades y el de la nube
     * una sola.
     */
    private static final Pattern RECHAZO_POR_ESQUEMA = Pattern.compile(
            "(validate|generate|generated).{0,40}json|json.{0,40}(schema|invalid)"
                    + "|does not match.{0,20}schema|schema validation",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SIN_RESPONSE_FORMAT = Pattern.compile(
            "response_format|json_schema|not supported", Pattern.CASE_INSENSITIVE);

    private static final Pattern VALLA_MARKDOWN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");

    private final ObjectMapper json;
    private final RestClient http;

    public ClienteDeModelo(ObjectMapper json) {
        this.json = json;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        // Un modelo grande pensando un informe tarda; más de dos minutos ya es
        // que algo va mal.
        fabrica.setReadTimeout(Duration.ofSeconds(120));
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    // ── Lo que sale ───────────────────────────────────────────────

    /** Una vuelta de conversación: el mensaje del asistente y lo que costó. */
    public record Respuesta(JsonNode mensaje, int tokensEntrada, int tokensSalida) {}

    /** Una respuesta con forma fija, ya validada y limpia. */
    public record Estructurada(JsonNode valor, int tokensEntrada, int tokensSalida, String modelo, int intentos) {}

    // ── Conversación libre (con herramientas) ─────────────────────

    /**
     * Una llamada al chat.
     *
     * @param herramientas las funciones que el modelo puede pedir, o {@code null}
     */
    public Respuesta conversar(Motor motor, ArrayNode mensajes, ArrayNode herramientas) {
        ObjectNode cuerpo = json.createObjectNode();
        cuerpo.put("model", motor.modelo());
        cuerpo.set("messages", mensajes);
        if (herramientas != null) cuerpo.set("tools", herramientas);

        JsonNode r = llamar(motor, cuerpo);
        return new Respuesta(
                r.path("choices").path(0).path("message"),
                r.path("usage").path("prompt_tokens").asInt(0),
                r.path("usage").path("completion_tokens").asInt(0));
    }

    // ── Respuesta estructurada ────────────────────────────────────

    /**
     * Pide una respuesta con forma fija y la valida. Si no cumple, se le
     * devuelve el problema al modelo para que lo corrija, hasta tres veces.
     */
    public Estructurada generarEstructurado(
            Motor motor, String sistema, String usuario, String nombreEsquema, EsquemaJson esquema) {

        String esquemaTexto = aTexto(esquema.comoMapa());
        ArrayNode mensajes = json.createArrayNode();
        mensajes.add(mensaje("system", sistema
                + "\n\nResponde ÚNICAMENTE con un objeto JSON que cumpla este esquema, "
                + "sin texto alrededor ni vallas de markdown:\n" + esquemaTexto));
        mensajes.add(mensaje("user", usuario));

        int tokensEntrada = 0;
        int tokensSalida = 0;
        String ultimoProblema = "";

        for (int intento = 1; intento <= INTENTOS; intento++) {
            ObjectNode cuerpo = json.createObjectNode();
            cuerpo.put("model", motor.modelo());
            cuerpo.set("messages", mensajes);
            ObjectNode formato = cuerpo.putObject("response_format");
            formato.put("type", "json_schema");
            ObjectNode js = formato.putObject("json_schema");
            js.put("name", nombreEsquema);
            js.set("schema", json.valueToTree(esquema.comoMapa()));
            js.put("strict", true);

            JsonNode r;
            try {
                r = llamarCrudo(motor, cuerpo);
            } catch (RestClientResponseException e) {
                String detalle = e.getResponseBodyAsString();
                if (RECHAZO_POR_ESQUEMA.matcher(detalle).find()) {
                    ultimoProblema = recortar(detalle, 400);
                    mensajes.add(mensaje("user", "Tu respuesta anterior no cumplió el esquema y fue rechazada. "
                            + "Devuelve ÚNICAMENTE el objeto JSON, sin texto alrededor y respetando todos los campos."));
                    continue;
                }
                // No todos los proveedores aceptan json_schema: se repite en modo JSON simple.
                if (!SIN_RESPONSE_FORMAT.matcher(detalle).find()) throw traducir(e, motor);
                cuerpo.putObject("response_format").put("type", "json_object");
                r = llamar(motor, cuerpo);
            } catch (ResourceAccessException e) {
                throw traducir(e, motor);
            }

            tokensEntrada += r.path("usage").path("prompt_tokens").asInt(0);
            tokensSalida += r.path("usage").path("completion_tokens").asInt(0);
            String crudo = r.path("choices").path(0).path("message").path("content").asText("");

            try {
                JsonNode valor = json.readTree(extraerJson(crudo));
                Optional<String> problema = esquema.problemaCon(valor);
                if (problema.isEmpty()) {
                    return new Estructurada(limpiar(valor), tokensEntrada, tokensSalida, motor.modelo(), intento);
                }
                ultimoProblema = problema.get();
            } catch (JsonProcessingException e) {
                ultimoProblema = "no es JSON válido";
            }
            mensajes.add(mensaje("assistant", recortar(crudo, 2000)));
            mensajes.add(mensaje("user", "Esa respuesta no cumple el esquema: " + ultimoProblema
                    + "\nDevuelve solo el JSON corregido."));
        }

        throw new IaSinFormato("El modelo " + motor.modelo()
                + " no ha devuelto un JSON válido en tres intentos. Último problema: " + ultimoProblema);
    }

    public ObjectNode mensaje(String rol, String contenido) {
        ObjectNode m = json.createObjectNode();
        m.put("role", rol);
        m.put("content", contenido);
        return m;
    }

    // ── HTTP ──────────────────────────────────────────────────────

    private JsonNode llamar(Motor motor, ObjectNode cuerpo) {
        try {
            return llamarCrudo(motor, cuerpo);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw traducir(e, motor);
        }
    }

    private JsonNode llamarCrudo(Motor motor, ObjectNode cuerpo) {
        String base = motor.baseUrl().endsWith("/") ? motor.baseUrl() : motor.baseUrl() + "/";
        return http.post()
                .uri(base + "chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                // Ollama no valida la clave, pero la cabecera tiene que ir.
                .header("Authorization", "Bearer " + (motor.apiKey().isBlank() ? "local" : motor.apiKey()))
                .body(cuerpo)
                .retrieve()
                .body(JsonNode.class);
    }

    /** Convierte un fallo de conexión en algo que se entiende en el panel. */
    private static RuntimeException traducir(RuntimeException e, Motor motor) {
        if (e instanceof ResourceAccessException) {
            return new IaNoDisponible("No hay nadie escuchando en " + motor.baseUrl()
                    + ". Si usas Ollama, arráncalo con \"ollama serve\".");
        }
        if (e instanceof RestClientResponseException r) {
            int estado = r.getStatusCode().value();
            if (estado == 401 || estado == 403) {
                return new IaNoDisponible("La clave configurada no es válida para " + motor.baseUrl() + ".");
            }
            if (estado == 404) {
                return new IaNoDisponible("El modelo " + motor.modelo() + " no existe en " + motor.baseUrl()
                        + ". Si usas Ollama, descárgalo con \"ollama pull <modelo>\".");
            }
            if (estado == 429) {
                return new IaNoDisponible("El proveedor ha cortado por límite de peticiones. Prueba en un minuto.");
            }
            return new IaNoDisponible("El proveedor respondió " + estado + ": "
                    + recortar(r.getResponseBodyAsString(), 300));
        }
        return e;
    }

    // ── Limpieza de la respuesta ──────────────────────────────────

    /** Los modelos pequeños envuelven el JSON en vallas de markdown o lo acompañan de prosa. */
    static String extraerJson(String crudo) {
        Matcher valla = VALLA_MARKDOWN.matcher(crudo);
        String texto = (valla.find() ? valla.group(1) : crudo).trim();

        int inicio = -1;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '{' || c == '[') {
                inicio = i;
                break;
            }
        }
        if (inicio == -1) return texto;
        char cierre = texto.charAt(inicio) == '{' ? '}' : ']';
        int fin = texto.lastIndexOf(cierre);
        return fin > inicio ? texto.substring(inicio, fin + 1) : texto.substring(inicio);
    }

    /**
     * Quita lo que el modelo cuela por mucho que se le pida en el prompt.
     *
     * <p>Medido en el sistema anterior: «No uses emojis» se ignoró en 2 de 2
     * ejecuciones. Un reemplazo determinista acierta siempre y sigue
     * funcionando si mañana se cambia de proveedor.
     */
    static String limpiar(String texto) {
        return texto
                .replaceAll("\\p{IsExtended_Pictographic}|\uFE0F|\u200D", "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll(" +([,.;:!?)])", "$1")
                .trim();
    }

    private JsonNode limpiar(JsonNode valor) {
        if (valor.isTextual()) return new TextNode(limpiar(valor.asText()));
        if (valor.isArray()) {
            ArrayNode copia = json.createArrayNode();
            valor.forEach(v -> copia.add(limpiar(v)));
            return copia;
        }
        if (valor.isObject()) {
            ObjectNode copia = json.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> campos = valor.fields();
            while (campos.hasNext()) {
                var c = campos.next();
                copia.set(c.getKey(), limpiar(c.getValue()));
            }
            return copia;
        }
        return valor;
    }

    private String aTexto(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String recortar(String texto, int maximo) {
        return texto == null ? "" : texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
