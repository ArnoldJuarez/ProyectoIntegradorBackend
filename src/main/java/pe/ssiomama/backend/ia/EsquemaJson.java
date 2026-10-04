package pe.ssiomama.backend.ia;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * La forma que tiene que tener la respuesta de un agente, y su comprobación.
 *
 * <p>Con modelos pequeños validar no es opcional: aciertan el esquema la mayoría
 * de las veces, no siempre. El sistema anterior lo hacía con zod; aquí basta un
 * subconjunto de JSON Schema —objetos, listas, textos, números y enumerados—,
 * que es todo lo que usan los agentes.
 *
 * <p>El mismo objeto da el esquema que se le manda al modelo
 * ({@link #comoMapa()}) y el que se comprueba al volver, así que no pueden
 * desincronizarse.
 */
public final class EsquemaJson {

    private final Map<String, Object> definicion;

    private EsquemaJson(Map<String, Object> definicion) {
        this.definicion = definicion;
    }

    // ── Construcción ──────────────────────────────────────────────

    public static EsquemaJson texto(String descripcion) {
        return new EsquemaJson(conDescripcion(Map.of("type", "string"), descripcion));
    }

    public static EsquemaJson texto() {
        return texto(null);
    }

    public static EsquemaJson numero(String descripcion) {
        return new EsquemaJson(conDescripcion(Map.of("type", "number"), descripcion));
    }

    public static EsquemaJson numero() {
        return numero(null);
    }

    public static EsquemaJson unoDe(String... valores) {
        return new EsquemaJson(Map.of("type", "string", "enum", List.of(valores)));
    }

    public static EsquemaJson lista(EsquemaJson elementos, String descripcion) {
        return new EsquemaJson(conDescripcion(
                Map.of("type", "array", "items", elementos.definicion), descripcion));
    }

    public static EsquemaJson lista(EsquemaJson elementos) {
        return lista(elementos, null);
    }

    /**
     * Un objeto con todos sus campos obligatorios.
     *
     * <p>Todos obligatorios porque es lo que exige el modo estricto de los
     * proveedores que validan en su lado; con campos opcionales lo rechazan.
     */
    public static EsquemaJson objeto(Object... nombresYEsquemas) {
        Map<String, Object> propiedades = new LinkedHashMap<>();
        for (int i = 0; i < nombresYEsquemas.length; i += 2) {
            propiedades.put((String) nombresYEsquemas[i], ((EsquemaJson) nombresYEsquemas[i + 1]).definicion);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("type", "object");
        d.put("properties", propiedades);
        d.put("required", List.copyOf(propiedades.keySet()));
        d.put("additionalProperties", false);
        return new EsquemaJson(d);
    }

    private static Map<String, Object> conDescripcion(Map<String, Object> base, String descripcion) {
        if (descripcion == null) return base;
        Map<String, Object> d = new LinkedHashMap<>(base);
        d.put("description", descripcion);
        return d;
    }

    public Map<String, Object> comoMapa() {
        return definicion;
    }

    // ── Comprobación ──────────────────────────────────────────────

    /** El primer problema encontrado, o vacío si cumple. */
    public Optional<String> problemaCon(JsonNode valor) {
        List<String> problemas = new ArrayList<>();
        comprobar(definicion, valor, "$", problemas);
        return problemas.stream().findFirst();
    }

    @SuppressWarnings("unchecked")
    private static void comprobar(Map<String, Object> esquema, JsonNode valor, String ruta, List<String> problemas) {
        if (!problemas.isEmpty()) return;
        String tipo = (String) esquema.get("type");

        switch (tipo) {
            case "string" -> {
                if (!valor.isTextual()) {
                    problemas.add(ruta + " tiene que ser un texto");
                    return;
                }
                List<String> permitidos = (List<String>) esquema.get("enum");
                if (permitidos != null && !permitidos.contains(valor.asText())) {
                    problemas.add(ruta + " tiene que ser uno de " + permitidos);
                }
            }
            case "number" -> {
                if (!valor.isNumber()) problemas.add(ruta + " tiene que ser un número");
            }
            case "array" -> {
                if (!valor.isArray()) {
                    problemas.add(ruta + " tiene que ser una lista");
                    return;
                }
                Map<String, Object> items = (Map<String, Object>) esquema.get("items");
                for (int i = 0; i < valor.size(); i++) {
                    comprobar(items, valor.get(i), ruta + "[" + i + "]", problemas);
                }
            }
            case "object" -> {
                if (!valor.isObject()) {
                    problemas.add(ruta + " tiene que ser un objeto");
                    return;
                }
                Map<String, Object> propiedades = (Map<String, Object>) esquema.get("properties");
                for (String requerido : (List<String>) esquema.get("required")) {
                    if (!valor.has(requerido) || valor.get(requerido).isNull()) {
                        problemas.add("falta " + ruta + "." + requerido);
                        return;
                    }
                }
                Iterator<Map.Entry<String, JsonNode>> campos = valor.fields();
                while (campos.hasNext()) {
                    var campo = campos.next();
                    Map<String, Object> suyo = (Map<String, Object>) propiedades.get(campo.getKey());
                    if (suyo != null) comprobar(suyo, campo.getValue(), ruta + "." + campo.getKey(), problemas);
                }
            }
            default -> problemas.add(ruta + ": tipo de esquema desconocido " + tipo);
        }
    }
}
