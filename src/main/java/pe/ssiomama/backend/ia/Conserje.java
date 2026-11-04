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

            Responde en español, en dos o tres frases. Si la herramienta trae el dato, dalo tal cual; si no lo trae, di que no lo sabes y da el teléfono %s."""
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
                        Map.of(
                                "limite", Map.of("type", "integer", "description", "Cuántos eventos devolver, por defecto 5"),
                                "buscar", Map.of("type", "string", "description",
                                        "Opcional: título, artista o fecha de una noche concreta, p. ej. «año nuevo» o "
                                                + "«31 de diciembre». Úsalo si preguntan por una noche en particular.")),
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
            // La fecha de hoy: sin ella el modelo no sabe qué «Año Nuevo» es el próximo.
            mensajes.add(modelo.mensaje("system", INSTRUCCIONES + "\n\nHoy es "
                    + Club.fechaConAnio(java.time.Instant.now()) + "."));
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
            case "buscar_eventos" -> buscarEventos(
                    args.path("limite").isNumber() ? Math.min(Math.max(args.path("limite").asInt(), 1), 10) : 5,
                    args.path("buscar").asText(""));
            case "detalle_evento" -> detalleEvento(args.path("slug").asText(""));
            case "info_local" -> infoLocal();
            default -> "Herramienta desconocida: " + nombre;
        };
    }

    /** Hasta dónde miran la búsqueda y el índice: de sobra para varios meses de cartel. */
    private static final int HORIZONTE = 60;

    /** Palabras que no sirven para encontrar una noche. */
    private static final java.util.Set<String> VACIAS = java.util.Set.of(
            "de", "del", "la", "el", "los", "las", "en", "para", "por", "una", "noche", "fiesta", "evento",
            "precio", "precios", "entrada", "entradas", "cuanto", "cuesta");

    /**
     * Las próximas noches con su detalle y, debajo, el resto de lo publicado en
     * una línea cada una, con sus precios.
     *
     * <p>Sin `buscar`, una noche lejana (Año Nuevo, con el cartel de noviembre
     * entero por delante) quedaba fuera de las diez que caben y el conserje
     * contestaba que no sabía nada. Medido: aunque vaya en el índice del final,
     * el modelo la pasa por alto en una respuesta larga. Con `buscar` pide
     * justo esa noche, y la cabecera le avisa de que hay más.
     */
    private String buscarEventos(int limite, String buscar) {
        var todas = datos.proximas(HORIZONTE);
        if (todas.isEmpty()) return "No hay ningún evento publicado ahora mismo.";

        List<String> palabras = java.util.Arrays.stream(normalizar(buscar).split("[^a-z0-9]+"))
                .filter(p -> p.length() > 1 && !VACIAS.contains(p))
                .toList();
        if (!palabras.isEmpty()) {
            // Ganan las noches con más palabras en común. Exigirlas todas fallaba con
            // «precio año nuevo»: «precio» no sale en ninguna noche y no salía nada.
            Map<Recolectores.Noche, Long> aciertos = new java.util.LinkedHashMap<>();
            for (var n : todas) {
                String texto = normalizar(String.join(" ", n.titulo(),
                        n.descripcion() == null ? "" : n.descripcion(), Club.fechaConAnio(n.empieza()),
                        datos.cartel(n.id()).stream().map(Recolectores.Artista::nombre)
                                .collect(Collectors.joining(" "))));
                var suyas = new java.util.HashSet<>(java.util.Arrays.asList(texto.split("[^a-z0-9]+")));
                aciertos.put(n, palabras.stream().filter(suyas::contains).count());
            }
            long mejor = aciertos.values().stream().mapToLong(Long::longValue).max().orElse(0);
            var encontradas = mejor == 0 ? List.<Recolectores.Noche>of() : aciertos.entrySet().stream()
                    .filter(e -> e.getValue() == mejor).map(Map.Entry::getKey).limit(limite).toList();
            if (!encontradas.isEmpty()) {
                return "Noches publicadas que coinciden con «" + buscar + "»:\n\n" + detalle(encontradas);
            }
        }

        var noches = todas.subList(0, Math.min(limite, todas.size()));
        var lejanas = todas.subList(noches.size(), todas.size());
        String cabecera = (palabras.isEmpty() ? "" : "Ninguna noche publicada coincide con «" + buscar + "».\n")
                + "Hay " + todas.size() + " noches publicadas. Van con detalle las " + noches.size() + " primeras"
                + (lejanas.isEmpty() ? "." : "; las " + lejanas.size() + " siguientes, con sus precios, al final.")
                + "\n\n";
        if (lejanas.isEmpty()) return cabecera + detalle(noches);

        return cabecera + detalle(noches) + "\n\nMás adelante (detalle_evento con el slug da el cartel y los horarios):\n"
                + lejanas.stream()
                        .map(n -> {
                            String precios = datos.tiposALaVenta(n.id()).stream()
                                    .map(t -> t.nombre() + " " + Club.soles(t.precio()))
                                    .collect(Collectors.joining(", "));
                            return "- " + n.titulo() + ", " + Club.fechaConAnio(n.empieza()) + " — slug: " + n.slug()
                                    + " — entradas: " + (precios.isEmpty() ? "aún no a la venta" : precios);
                        })
                        .collect(Collectors.joining("\n"));
    }

    private String detalle(List<Recolectores.Noche> noches) {
        return noches.stream().map(n -> {
            String entradas = datos.tiposALaVenta(n.id()).stream()
                    .map(t -> t.nombre() + " " + Club.soles(t.precio()) + " (quedan " + t.quedan() + ")")
                    .collect(Collectors.joining(", "));
            String cartel = datos.cartel(n.id()).stream()
                    .map(a -> a.nombre() + (a.inicio() != null && a.fin() != null
                            ? " (" + Club.hora(a.inicio()) + "-" + Club.hora(a.fin()) + ")" : ""))
                    .collect(Collectors.joining(", "));
            return String.join("\n",
                    n.titulo() + " — slug: " + n.slug(),
                    "  " + Club.fechaConAnio(n.empieza()) + ", puertas " + Club.hora(n.puertas()) + ", " + n.sala(),
                    "  Cartel: " + (cartel.isEmpty() ? "por confirmar" : cartel),
                    "  Entradas: " + (entradas.isEmpty() ? "aún no a la venta" : entradas));
        }).collect(Collectors.joining("\n\n"));
    }

    /** Minúsculas y sin tildes: «Año Nuevo» y «ano nuevo» tienen que encontrarse. */
    private static String normalizar(String texto) {
        String sinTildes = java.text.Normalizer.normalize(texto == null ? "" : texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(java.util.Locale.ROOT);
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
                "Fecha: " + Club.fechaConAnio(n.empieza()),
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
