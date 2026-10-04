package pe.ssiomama.backend.ia;

/**
 * Un proveedor de modelos al que apuntar.
 *
 * <p>Todo habla el formato compatible con OpenAI, que entienden Groq, Gemini y
 * OpenRouter por igual. Cambiar de proveedor es cambiar tres variables de
 * entorno, no tocar código:
 *
 * <pre>
 *   Google Gemini  .../v1beta/openai/                           AI_MODEL=gemini-2.5-flash
 *   Groq           https://api.groq.com/openai/v1               AI_MODEL=openai/gpt-oss-120b
 *   OpenRouter     https://openrouter.ai/api/v1                 AI_MODEL=deepseek/deepseek-chat:free
 * </pre>
 *
 * <p>{@link #toString()} no enseña la clave: este objeto acaba en logs.
 */
public record Motor(String baseUrl, String apiKey, String modelo) {

    @Override
    public String toString() {
        return "Motor[" + baseUrl + ", " + modelo + "]";
    }
}
