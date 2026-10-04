package pe.ssiomama.backend.ia;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convierte una propuesta aprobada en registros reales.
 *
 * <p>Es el único punto del sistema donde lo que produce un modelo llega a
 * modificar datos, y solo se llega aquí después de que una persona haya pulsado
 * «Aprobar».
 *
 * <p>Lo que no se puede aplicar no rompe la operación: se aplica el resto y se
 * devuelve el aviso. Un SKU que el modelo escribió mal no debería tirar abajo
 * las otras cinco líneas que sí estaban bien.
 *
 * <p>Nada sale hacia fuera: las campañas quedan en borrador y los pedidos a
 * proveedor también. Quien los envíe lo hará a mano.
 */
@Service
public class AplicadorDePropuestas {

    /** El segmento al que va una campaña cuando el modelo nombra uno que no existe. */
    static final String SEGMENTO_GENERAL = "Todos con consentimiento";

    private static final Set<String> CANALES = Set.of("CORREO", "SMS", "WHATSAPP", "PUSH");
    private static final String AHORA = "(now() at time zone 'utc')";

    private final JdbcClient sql;

    public AplicadorDePropuestas(JdbcClient sql) {
        this.sql = sql;
    }

    /**
     * @param aplicada {@code false} cuando era informativa o no quedó nada que hacer
     */
    public record Resultado(boolean aplicada, String resumen, List<String> avisos) {}

    @Transactional
    public Resultado aplicar(TipoDePropuesta tipo, JsonNode datos, String ejecucionId) {
        return switch (tipo) {
            case TEXTO_CAMPANIA -> textosDeCampania(datos, ejecucionId);
            case CAMPANIA -> campania(datos, ejecucionId);
            case ORDEN_COMPRA -> pedido(datos);
            case CAMBIO_PRECIO -> precios(datos);
            case INFORME -> new Resultado(false, "Es un informe: no hay nada que ejecutar, queda archivado.", List.of());
        };
    }

    // ── Textos de promoción → campaña en borrador ─────────────────

    private Resultado textosDeCampania(JsonNode datos, String ejecucionId) {
        String asunto = datos.path("correo").path("asunto").asText("");
        String cuerpo = datos.path("correo").path("cuerpo").asText("");
        if (asunto.isBlank() || cuerpo.isBlank()) {
            return new Resultado(false, "La propuesta no traía correo que guardar.", List.of());
        }
        String nombre = recortar(datos.path("hook").asText(asunto), 120);
        crearCampania(nombre, "CORREO", segmentoGeneral(), asunto, cuerpo, ejecucionId);

        List<String> avisos = new ArrayList<>();
        if (!datos.path("instagram").path("caption").asText("").isBlank()) {
            avisos.add("El texto de Instagram no se publica solo: cópialo de la propuesta y súbelo a mano.");
        }
        return new Resultado(true, "Campaña \"" + nombre + "\" creada en borrador para el segmento «"
                + SEGMENTO_GENERAL + "».", avisos);
    }

    // ── Campaña del segmentador ───────────────────────────────────

    private Resultado campania(JsonNode datos, String ejecucionId) {
        JsonNode c = datos.path("campaign");
        String nombre = c.path("nombre").asText("");
        String cuerpo = c.path("cuerpo").asText("");
        if (nombre.isBlank() || cuerpo.isBlank()) {
            return new Resultado(false, "La propuesta no traía una campaña completa.", List.of());
        }

        List<String> avisos = new ArrayList<>();
        String canal = c.path("canal").asText("");
        if (!CANALES.contains(canal)) {
            avisos.add("El canal \"" + canal + "\" no existe; se ha usado CORREO.");
            canal = "CORREO";
        }

        String pedido = c.path("segmento").asText("");
        String segmentoId = segmentoPorNombre(pedido).orElseGet(() -> {
            if (!pedido.isBlank()) {
                avisos.add("No existe el segmento «" + pedido + "»; la campaña apunta a «" + SEGMENTO_GENERAL + "».");
            }
            return segmentoGeneral();
        });

        String asunto = c.path("asunto").asText("");
        crearCampania(recortar(nombre, 120), canal, segmentoId, asunto.isBlank() ? null : asunto, cuerpo, ejecucionId);
        return new Resultado(true, "Campaña \"" + recortar(nombre, 120)
                + "\" creada en borrador. Queda sin enviar hasta que la lances.", avisos);
    }

    private void crearCampania(
            String nombre, String canal, String segmentoId, String asunto, String cuerpo, String ejecucionId) {
        sql.sql("""
                insert into "Campania" (id, nombre, canal, "segmentoId", asunto, cuerpo, estado, "ejecucionId", "creadoEn")
                values (:id, :nombre, cast(:canal as "CanalCampania"), :segmento, :asunto, :cuerpo,
                        'BORRADOR', :ejecucion, %s)
                """.formatted(AHORA))
                .param("id", UUID.randomUUID().toString())
                .param("nombre", nombre)
                .param("canal", canal)
                .param("segmento", segmentoId)
                .param("asunto", asunto)
                .param("cuerpo", cuerpo)
                .param("ejecucion", ejecucionId)
                .update();
    }

    private Optional<String> segmentoPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) return Optional.empty();
        return sql.sql("select id from \"Segmento\" where nombre = :nombre")
                .param("nombre", nombre)
                .query(String.class)
                .optional();
    }

    /** El segmento general, creándolo la primera vez que hace falta. */
    private String segmentoGeneral() {
        return segmentoPorNombre(SEGMENTO_GENERAL).orElseGet(() -> {
            String id = UUID.randomUUID().toString();
            sql.sql("""
                    insert into "Segmento" (id, nombre, descripcion, "reglasJson", "esDinamico", "actualizadoEn")
                    values (:id, :nombre, 'Clientes que aceptaron recibir comunicaciones.',
                            '{"type": "consented"}'::jsonb, true, %s)
                    """.formatted(AHORA))
                    .param("id", id)
                    .param("nombre", SEGMENTO_GENERAL)
                    .update();
            return id;
        });
    }

    // ── Pedido a proveedor ────────────────────────────────────────

    private Resultado pedido(JsonNode datos) {
        record Pedible(String sku, int cantidad) {}
        List<Pedible> pedibles = new ArrayList<>();
        for (JsonNode l : datos.path("lineas")) {
            String sku = l.path("sku").asText("");
            int cantidad = (int) Math.round(l.path("suggestedOrder").asDouble(0));
            if (!sku.isBlank() && cantidad > 0) pedibles.add(new Pedible(sku, cantidad));
        }
        if (pedibles.isEmpty()) {
            return new Resultado(false, "La propuesta no pedía reponer nada.", List.of());
        }

        record Proveedor(String id, String nombre) {}
        Optional<Proveedor> proveedor = sql.sql("select id, nombre from \"Proveedor\" order by nombre limit 1")
                .query((rs, n) -> new Proveedor(rs.getString(1), rs.getString(2)))
                .optional();
        if (proveedor.isEmpty()) {
            return new Resultado(false,
                    "No hay ningún proveedor dado de alta, así que no se puede crear el pedido.", List.of());
        }

        record Linea(String productoId, int cantidad, int coste) {}
        List<String> avisos = new ArrayList<>();
        List<Linea> lineas = new ArrayList<>();
        for (Pedible p : pedibles) {
            Optional<Linea> linea = sql.sql("select id, \"costeCentimos\" from \"Producto\" where sku = :sku")
                    .param("sku", p.sku())
                    .query((rs, n) -> new Linea(rs.getString(1), p.cantidad(), rs.getInt(2)))
                    .optional();
            if (linea.isEmpty()) {
                avisos.add("No existe ningún producto con la referencia " + p.sku() + "; línea omitida.");
                continue;
            }
            lineas.add(linea.get());
        }
        if (lineas.isEmpty()) {
            return new Resultado(false, "Ninguna de las referencias propuestas existe en el catálogo.", avisos);
        }

        long total = lineas.stream().mapToLong(l -> (long) l.cantidad() * l.coste()).sum();
        String ordenId = UUID.randomUUID().toString();
        sql.sql("""
                insert into "OrdenCompra" (id, "proveedorId", estado, "totalCentimos", "creadoEn")
                values (:id, :proveedor, 'BORRADOR', :total, %s)
                """.formatted(AHORA))
                .param("id", ordenId)
                .param("proveedor", proveedor.get().id())
                .param("total", total)
                .update();
        for (Linea l : lineas) {
            sql.sql("""
                    insert into "LineaOrdenCompra" (id, "ordenCompraId", "productoId", cantidad, "costeUnitarioCentimos")
                    values (:id, :orden, :producto, :cantidad, :coste)
                    """)
                    .param("id", UUID.randomUUID().toString())
                    .param("orden", ordenId)
                    .param("producto", l.productoId())
                    .param("cantidad", l.cantidad())
                    .param("coste", l.coste())
                    .update();
        }

        avisos.add("El proveedor asignado es " + proveedor.get().nombre()
                + ", el primero del catálogo. Cámbialo si no es el correcto.");
        return new Resultado(true, "Pedido en borrador con " + lineas.size() + " líneas por "
                + Club.soles(total) + ". Queda sin enviar.", avisos);
    }

    // ── Cambio de precios ─────────────────────────────────────────

    private Resultado precios(JsonNode datos) {
        List<String> avisos = new ArrayList<>();
        int cambiados = 0;
        boolean algunaValida = false;

        for (JsonNode a : datos.path("actions")) {
            String tipo = a.path("ticketTypeName").asText("");
            String evento = a.path("eventTitle").asText("");
            if (tipo.isBlank() || !a.path("suggestedPriceCents").isNumber()
                    || a.path("suggestedPriceCents").asDouble() < 0) continue;
            algunaValida = true;
            int precio = (int) Math.round(a.path("suggestedPriceCents").asDouble());

            // Solo noches futuras y publicadas: cambiar el precio de una ya
            // celebrada falsearía su contabilidad.
            record Candidato(String id, String nombre, int vendidas, String noche) {}
            List<Candidato> candidatos = sql.sql("""
                    select t.id, t.nombre, t.vendidas, e.titulo
                    from "TipoEntrada" t join "Evento" e on e.id = t."eventoId"
                    where t.nombre = :tipo and t.activo and e.estado = 'PUBLICADO'
                      and e."empiezaEn" >= %s and (:evento = '' or e.titulo = :evento)
                    """.formatted(AHORA))
                    .param("tipo", tipo)
                    .param("evento", evento)
                    .query((rs, n) -> new Candidato(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4)))
                    .list();

            if (candidatos.isEmpty()) {
                avisos.add(evento.isBlank()
                        ? "No hay ningún \"" + tipo + "\" a la venta; se omite."
                        : "No hay ningún \"" + tipo + "\" a la venta en \"" + evento + "\"; se omite.");
                continue;
            }
            // El mismo nombre en varias noches y sin decir cuál: se avisa en vez
            // de cambiar precios a ciegas en todas.
            if (candidatos.size() > 1) {
                avisos.add("\"" + tipo + "\" existe en " + candidatos.size()
                        + " eventos y la propuesta no dice cuál; se omite por seguridad.");
                continue;
            }

            Candidato c = candidatos.get(0);
            if (c.vendidas() > 0) {
                avisos.add("\"" + c.nombre() + "\" de " + c.noche() + " ya tiene " + c.vendidas()
                        + " vendidas al precio anterior.");
            }
            sql.sql("update \"TipoEntrada\" set \"precioCentimos\" = :precio where id = :id")
                    .param("precio", precio)
                    .param("id", c.id())
                    .update();
            cambiados++;
        }

        if (!algunaValida) {
            return new Resultado(false, "La propuesta no sugería ningún cambio de precio.", List.of());
        }
        if (cambiados == 0) {
            return new Resultado(false, "No se ha podido cambiar ningún precio.", avisos);
        }
        return new Resultado(true, cambiados + (cambiados == 1 ? " precio actualizado" : " precios actualizados")
                + " en eventos futuros.", avisos);
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
