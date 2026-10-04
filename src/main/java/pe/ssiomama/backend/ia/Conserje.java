package pe.ssiomama.backend.ia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * El conserje del chat público.
 *
 * <h2>Sin índice vectorial</h2>
 *
 * <p>El cartel y los precios cambian cada semana, y un índice se quedaría viejo
 * y el bot inventaría precios. En su lugar el modelo llama a herramientas que
 * preguntan a la base en el momento: la respuesta siempre está fresca.
 *
 * <h2>Instrucciones cortas, a propósito</h2>
 *
 * <p>Con una lista larga de reglas, un modelo de 3B deja de llamar a las
 * herramientas y contesta de memoria: medido, pasó de usarlas el 100 % de las
 * veces a no usarlas nunca. Si se añaden reglas, hay que volver a comprobar que
 * sigue consultando la base.
 */
@Service
public class Conserje {

    private static final String INSTRUCCIONES = """
            Eres el conserje de %s, una discoteca de %s.

            Usa las herramientas para contestar. Nunca des una fecha, un precio ni una disponibilidad de memoria.

            Responde en español, en dos o tres frases. Si el dato no sale de una herramienta, di que no lo sabes y da el teléfono %s."""
            .formatted(Club.NOMBRE, Club.CIUDAD, Club.TELEFONO);

    /** Buscar, mirar el detalle y responder: cuatro vueltas son de sobra. */
    private static final int VUELTAS = 4;

    private final ClienteDeModelo modelo;
    private final MotoresDeIa motores;
    private final RegistroDeIa registro;
    private final Recolectores datos;
    private final JdbcClient sql;
    private final ObjectMapper json;
    private final ArrayNode herramientas;

    public Conserje(
            ClienteDeModelo modelo,
            MotoresDeIa motores,
            RegistroDeIa registro,
            Recolectores datos,
            JdbcClient sql,
            ObjectMapper json) {
        this.modelo = modelo;
        this.motores = motores;
        this.registro = registro;
        this.datos = datos;
        this.sql = sql;
        this.json = json;
        this.herramientas = json.valueToTree(List.of(
                herramienta("buscar_eventos",
                        "Devuelve los próximos eventos publicados con su fecha, sala, cartel, precios y entradas "
                                + "disponibles. Úsala siempre que pregunten qué hay, cuándo abren o cuánto cuesta.",
                        Map.of("limite", Map.of("type", "integer", "description", "Cuántos eventos devolver, por defecto 5")),
                        List.of()),
                herramienta("detalle_evento",
                        "Devuelve todos los datos de un evento concreto a partir de su slug: cartel con horarios de "
                                + "cada DJ, tipos de entrada, vestimenta y edad mínima.",
                        Map.of("slug", Map.of("type", "string", "description", "Identificador del evento en la URL")),
                        List.of("slug")),
                herramienta("info_local",
                        "Devuelve los datos fijos de la sala: dirección, teléfono, horarios de apertura, edad mínima "
                                + "y reservados VIP.",
                        Map.of(),
                        List.of())));
    }

    private static Map<String, Object> herramienta(
            String nombre, String descripcion, Map<String, Object> propiedades, List<String> requeridas) {
        return Map.of("type", "function", "function", Map.of(
                "name", nombre,
                "description", descripcion,
                "parameters", Map.of("type", "object", "properties", propiedades, "required", requeridas)));
    }

    /** Un turno de la conversación, como lo manda el navegador. */
    public record Turno(String rol, String contenido) {}

    public record Respuesta(String ejecucionId, String texto) {}

    /** Contesta al último mensaje del historial. */
    public Respuesta preguntar(List<Turno> historial) {
        Motor motor = motores.prosa();
        String ejecucion = registro.abrirEjecucion(
                ClaveDeAgente.CONSERJE, Disparador.CHAT, null, Map.of("history", historial), motor.modelo());

        try {
            ArrayNode mensajes = json.createArrayNode();
            mensajes.add(modelo.mensaje("system", INSTRUCCIONES));
            for (Turno t : historial) {
                mensajes.add(modelo.mensaje("usuario".equals(t.rol()) ? "user" : "assistant", t.contenido()));
            }

            int tokensEntrada = 0;
            int tokensSalida = 0;
            String texto = null;

            for (int vuelta = 0; vuelta < VUELTAS && texto == null; vuelta++) {
                var r = modelo.conversar(motor, mensajes, herramientas);
                tokensEntrada += r.tokensEntrada();
                tokensSalida += r.tokensSalida();
                JsonNode respuesta = r.mensaje();

                JsonNode llamadas = respuesta.path("tool_calls");
                if (!llamadas.isArray() || llamadas.isEmpty()) {
                    String contenido = ClienteDeModelo.limpiar(respuesta.path("content").asText(""));
                    texto = contenido.isBlank() ? "Perdona, no te he entendido. ¿Puedes repetirlo?" : contenido;
                    break;
                }

                // Solo lo que el formato pide de vuelta. Algunos proveedores
                // añaden campos propios (el razonamiento, por ejemplo) y luego
                // los rechazan si se les reenvían.
                ObjectNode delAsistente = json.createObjectNode();
                delAsistente.put("role", "assistant");
                delAsistente.put("content", respuesta.path("content").asText(""));
                delAsistente.set("tool_calls", llamadas);
                mensajes.add(delAsistente);
                for (JsonNode llamada : llamadas) {
                    String nombre = llamada.path("function").path("name").asText();
                    JsonNode argumentos = argumentos(llamada.path("function").path("arguments").asText("{}"));
                    ObjectNode resultado = json.createObjectNode();
                    resultado.put("role", "tool");
                    resultado.put("tool_call_id", llamada.path("id").asText());
                    resultado.put("content", ejecutar(nombre, argumentos));
                    mensajes.add(resultado);
                }
            }

            if (texto == null) {
                texto = "Se me está complicando la consulta. Llámanos al " + Club.TELEFONO
                        + " y te lo resolvemos al momento.";
            }

            registro.completarEjecucion(ejecucion, Map.of("reply", texto), tokensEntrada, tokensSalida,
                    motores.costeCentimos(tokensEntrada, tokensSalida), 1);
            return new Respuesta(ejecucion, texto);
        } catch (RuntimeException e) {
            registro.fallarEjecucion(ejecucion, e.getMessage());
            throw e;
        }
    }

    /** Un modelo pequeño a veces manda argumentos que no son JSON válido. */
    private JsonNode argumentos(String texto) {
        try {
            return json.readTree(texto.isBlank() ? "{}" : texto);
        } catch (JsonProcessingException e) {
            return json.createObjectNode();
        }
    }

    // ── Herramientas ──────────────────────────────────────────────

    String ejecutar(String nombre, JsonNode args) {
        return switch (nombre) {
            case "buscar_eventos" -> buscarEventos(args.path("limite").isNumber()
                    ? Math.min(Math.max(args.path("limite").asInt(), 1), 10) : 5);
            case "detalle_evento" -> detalleEvento(args.path("slug").asText(""));
            case "info_local" -> infoLocal();
            default -> "Herramienta desconocida: " + nombre;
        };
    }

    private String buscarEventos(int limite) {
        var noches = datos.proximas(limite);
        if (noches.isEmpty()) return "No hay ningún evento publicado ahora mismo.";

        return noches.stream().map(n -> {
            String entradas = datos.tiposALaVenta(n.id()).stream()
                    .map(t -> t.nombre() + " " + Club.soles(t.precio()) + " (quedan " + t.quedan() + ")")
                    .collect(Collectors.joining(", "));
            String cartel = datos.cartel(n.id()).stream()
                    .map(Recolectores.Artista::nombre).collect(Collectors.joining(", "));
            return String.join("\n",
                    n.titulo() + " — slug: " + n.slug(),
                    "  " + Club.fechaLarga(n.empieza()) + ", puertas " + Club.hora(n.puertas()) + ", " + n.sala(),
                    "  Cartel: " + (cartel.isEmpty() ? "por confirmar" : cartel),
                    "  Entradas: " + (entradas.isEmpty() ? "aún no a la venta" : entradas));
        }).collect(Collectors.joining("\n\n"));
    }

    private String detalleEvento(String slug) {
        var noche = datos.porSlug(slug).filter(n -> !"BORRADOR".equals(n.estado()));
        if (noche.isEmpty()) return "No existe ningún evento con el slug \"" + slug + "\".";
        var n = noche.get();

        String cartel = datos.cartel(n.id()).stream()
                .map(a -> a.nombre() + (a.inicio() != null && a.fin() != null
                        ? " de " + Club.hora(a.inicio()) + " a " + Club.hora(a.fin()) : ""))
                .collect(Collectors.joining("; "));
        String entradas = datos.tiposALaVenta(n.id()).stream()
                .map(t -> t.nombre() + ": " + Club.soles(t.precio()) + ", quedan " + t.quedan()
                        + (t.consumiciones() > 0 ? ", incluye " + t.consumiciones() + " consumición(es)" : ""))
                .collect(Collectors.joining("\n  "));

        return String.join("\n",
                n.titulo(),
                "Fecha: " + Club.fechaLarga(n.empieza()),
                "Puertas: " + Club.hora(n.puertas()) + " · Cierre: " + Club.hora(n.termina()),
                "Sala: " + n.sala(),
                "Descripción: " + (n.descripcion() == null ? "sin descripción" : n.descripcion()),
                "Cartel: " + (cartel.isEmpty() ? "por confirmar" : cartel),
                "Vestimenta: " + (n.vestimenta() == null ? "libre" : n.vestimenta()),
                "Edad mínima: " + n.edadMinima() + " años con DNI",
                "Entradas:\n  " + (entradas.isEmpty() ? "aún no a la venta" : entradas),
                "Enlace de compra: /evento/" + n.slug());
    }

    private String infoLocal() {
        List<String> boxes = sql.sql("""
                select b.nombre, z.nombre as zona, b.plazas, b."consumoMinimoCentimos"
                from "Box" b left join "Zona" z on z.id = b."zonaId"
                where b.activo order by b.nombre
                """)
                .query((rs, i) -> "  " + rs.getString("nombre") + " ("
                        + (rs.getString("zona") == null ? "sin zona" : rs.getString("zona")) + "), hasta "
                        + rs.getInt("plazas") + " personas, consumo mínimo " + Club.soles(rs.getInt("consumoMinimoCentimos")))
                .list();
        return String.join("\n",
                "Nombre: " + Club.NOMBRE,
                "Dirección: " + Club.DIRECCION,
                "Teléfono: " + Club.TELEFONO,
                "Horario: " + Club.HORARIO,
                "Edad mínima: " + Club.EDAD_MINIMA + " años, se pide DNI o pasaporte en la puerta",
                "Reservados disponibles: " + boxes.size(),
                String.join("\n", boxes));
    }
}
