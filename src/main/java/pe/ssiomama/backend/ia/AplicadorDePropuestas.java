package pe.ssiomama.backend.ia;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.eventos.Artista;
import pe.ssiomama.backend.eventos.ArtistaRepository;
import pe.ssiomama.backend.eventos.EstadoDeEvento;
import pe.ssiomama.backend.eventos.Evento;
import pe.ssiomama.backend.eventos.GestionDeEventos;
import pe.ssiomama.backend.eventos.Sala;
import pe.ssiomama.backend.eventos.SalaRepository;

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
 * <p>Nada sale hacia fuera: las campañas quedan en borrador, los pedidos a
 * proveedor también y las noches nuevas se crean sin publicar. Quien las envíe
 * o las publique lo hará a mano.
 */
@Service
public class AplicadorDePropuestas {

    /** El segmento al que va una campaña cuando el modelo nombra uno que no existe. */
    static final String SEGMENTO_GENERAL = "Todos con consentimiento";

    private static final Set<String> CANALES = Set.of("CORREO", "SMS", "WHATSAPP", "PUSH");
    private static final String AHORA = "(now() at time zone 'utc')";

    private final JdbcClient sql;
    private final GestionDeEventos programacion;
    private final SalaRepository salas;
    private final ArtistaRepository artistas;

    public AplicadorDePropuestas(
            JdbcClient sql, GestionDeEventos programacion, SalaRepository salas, ArtistaRepository artistas) {
        this.sql = sql;
        this.programacion = programacion;
        this.salas = salas;
        this.artistas = artistas;
    }

    /** Algo creado al aplicar, con la ruta del panel donde se sigue gestionando a mano. */
    public record Enlace(String texto, String ruta) {}

    /**
     * @param aplicada {@code false} cuando era informativa o no quedó nada que hacer
     * @param enlaces lo que se ha creado, para abrirlo y terminarlo a mano
     */
    public record Resultado(boolean aplicada, String resumen, List<String> avisos, List<Enlace> enlaces) {

        public Resultado(boolean aplicada, String resumen, List<String> avisos) {
            this(aplicada, resumen, avisos, List.of());
        }
    }

    @Transactional
    public Resultado aplicar(TipoDePropuesta tipo, JsonNode datos, Long ejecucionId) {
        return switch (tipo) {
            case TEXTO_CAMPANIA -> textosDeCampania(datos, ejecucionId);
            case CAMPANIA -> campania(datos, ejecucionId);
            case ORDEN_COMPRA -> pedido(datos);
            case CAMBIO_PRECIO -> precios(datos);
            case INFORME -> new Resultado(false, "Es un informe: no hay nada que ejecutar, queda archivado.", List.of());
            case EVENTO -> noches(datos);
        };
    }

    // ── Textos de promoción → campaña en borrador ─────────────────

    private Resultado textosDeCampania(JsonNode datos, Long ejecucionId) {
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

    private Resultado campania(JsonNode datos, Long ejecucionId) {
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
        Long segmentoId = segmentoPorNombre(pedido).orElseGet(() -> {
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
            String nombre, String canal, Long segmentoId, String asunto, String cuerpo, Long ejecucionId) {
        sql.sql("""
                insert into "Campania" (nombre, canal, "segmentoId", asunto, cuerpo, estado, "ejecucionId", "creadoEn")
                values (:nombre, cast(:canal as "CanalCampania"), :segmento, :asunto, :cuerpo,
                        'BORRADOR', :ejecucion, %s)
                """.formatted(AHORA))
                .param("nombre", nombre)
                .param("canal", canal)
                .param("segmento", segmentoId)
                .param("asunto", asunto)
                .param("cuerpo", cuerpo)
                .param("ejecucion", ejecucionId)
                .update();
    }

    private Optional<Long> segmentoPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) return Optional.empty();
        return sql.sql("select id from \"Segmento\" where nombre = :nombre")
                .param("nombre", nombre)
                .query(Long.class)
                .optional();
    }

    /** El segmento general, creándolo la primera vez que hace falta. */
    private Long segmentoGeneral() {
        return segmentoPorNombre(SEGMENTO_GENERAL).orElseGet(() -> sql.sql("""
                    insert into "Segmento" (nombre, descripcion, "reglasJson", "esDinamico", "actualizadoEn")
                    values (:nombre, 'Clientes que aceptaron recibir comunicaciones.',
                            '{"type": "consented"}'::jsonb, true, %s)
                    returning id
                    """.formatted(AHORA))
                    .param("nombre", SEGMENTO_GENERAL)
                    .query(Long.class)
                    .single());
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

        record Proveedor(Long id, String nombre) {}
        Optional<Proveedor> proveedor = sql.sql("select id, nombre from \"Proveedor\" order by nombre limit 1")
                .query((rs, n) -> new Proveedor(rs.getLong(1), rs.getString(2)))
                .optional();
        if (proveedor.isEmpty()) {
            return new Resultado(false,
                    "No hay ningún proveedor dado de alta, así que no se puede crear el pedido.", List.of());
        }

        record Linea(Long productoId, int cantidad, int coste) {}
        List<String> avisos = new ArrayList<>();
        List<Linea> lineas = new ArrayList<>();
        for (Pedible p : pedibles) {
            Optional<Linea> linea = sql.sql("select id, \"costeCentimos\" from \"Producto\" where sku = :sku")
                    .param("sku", p.sku())
                    .query((rs, n) -> new Linea(rs.getLong(1), p.cantidad(), rs.getInt(2)))
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
        Long ordenId = sql.sql("""
                insert into "OrdenCompra" ("proveedorId", estado, "totalCentimos", "creadoEn")
                values (:proveedor, 'BORRADOR', :total, %s)
                returning id
                """.formatted(AHORA))
                .param("proveedor", proveedor.get().id())
                .param("total", total)
                .query(Long.class)
                .single();
        for (Linea l : lineas) {
            sql.sql("""
                    insert into "LineaOrdenCompra" ("ordenCompraId", "productoId", cantidad, "costeUnitarioCentimos")
                    values (:orden, :producto, :cantidad, :coste)
                    """)
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
            record Candidato(Long id, String nombre, int vendidas, String noche) {}
            List<Candidato> candidatos = sql.sql("""
                    select t.id, t.nombre, t.vendidas, e.titulo
                    from "TipoEntrada" t join "Evento" e on e.id = t."eventoId"
                    where t.nombre = :tipo and t.activo and e.estado = 'PUBLICADO'
                      and e."empiezaEn" >= %s and (:evento = '' or e.titulo = :evento)
                    """.formatted(AHORA))
                    .param("tipo", tipo)
                    .param("evento", evento)
                    .query((rs, n) -> new Candidato(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4)))
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

    // ── Noches nuevas → borradores en la programación ─────────────

    /**
     * Crea en borrador las noches que propuso el programador.
     *
     * <p>Todo lo que podría hacer fallar a {@link GestionDeEventos} se comprueba
     * <b>antes</b> de llamarla: una excepción dentro de una operación
     * transaccional marca la transacción entera para deshacer, y una noche mal
     * puesta tiraría también las buenas. Así, lo que no cuadra se salta con un
     * aviso y el resto se crea.
     *
     * <p>Se crean en {@link EstadoDeEvento#BORRADOR}, sin flyer ni video: la
     * propuesta da el esqueleto y la persona termina la noche a mano —sube el
     * flyer, ajusta lo que quiera— y la publica cuando esté lista.
     */
    private Resultado noches(JsonNode datos) {
        Optional<Sala> principal = salas.findAllByOrderByAforoDesc().stream().findFirst();
        if (principal.isEmpty()) {
            return new Resultado(false, "No hay ninguna sala dada de alta donde programar.", List.of());
        }
        Sala sala = principal.get();

        List<String> avisos = new ArrayList<>();
        List<Enlace> creadas = new ArrayList<>();
        Instant ahora = Instant.now();

        for (JsonNode n : datos.path("noches")) {
            String titulo = recortar(n.path("titulo").asText("").trim(), 160);
            if (titulo.length() < 3) {
                avisos.add("Una noche venía sin título; se omite.");
                continue;
            }

            LocalDate fecha;
            try {
                fecha = LocalDate.parse(n.path("fecha").asText(""));
            } catch (DateTimeParseException e) {
                avisos.add("«" + titulo + "» no traía una fecha válida; se omite.");
                continue;
            }
            LocalTime puertas = hora(n.path("puertas").asText(""), LocalTime.of(21, 0), titulo, "apertura", avisos);
            LocalTime cierre = hora(n.path("cierre").asText(""), LocalTime.of(3, 0), titulo, "cierre", avisos);

            Instant puertasA = fecha.atTime(puertas).atZone(Club.ZONA).toInstant();
            // Un cierre «antes» que la apertura es de madrugada: del día siguiente.
            Instant terminaA = (cierre.isAfter(puertas) ? fecha : fecha.plusDays(1))
                    .atTime(cierre).atZone(Club.ZONA).toInstant();

            if (!puertasA.isAfter(ahora)) {
                avisos.add("«" + titulo + "» era para el " + fecha + ", que ya pasó; se omite.");
                continue;
            }
            Optional<String> ocupada = nocheDelDia(fecha);
            if (ocupada.isPresent()) {
                avisos.add("El " + Club.fechaLarga(fecha) + " ya tiene «" + ocupada.get()
                        + "»; «" + titulo + "» no se crea.");
                continue;
            }

            List<Long> cartel = new ArrayList<>();
            for (JsonNode nombre : n.path("artistas")) {
                String pedido = nombre.asText("").trim();
                if (pedido.isEmpty()) continue;
                Optional<Long> id = artistas.findFirstByNombreIgnoreCase(pedido).map(Artista::getId);
                if (id.isEmpty()) {
                    avisos.add("«" + pedido + "» no está en el catálogo de artistas: dalo de alta y añádelo al "
                            + "cartel de «" + titulo + "» si lo quieres.");
                } else if (!cartel.contains(id.get())) {
                    cartel.add(id.get());
                }
            }

            Evento noche = programacion.crear(new GestionDeEventos.DatosDeNoche(
                    titulo,
                    recortar(n.path("descripcion").asText("").trim(), 2000),
                    sala.getId(),
                    puertasA,
                    terminaA,
                    // La casa es para mayores de edad, diga lo que diga la propuesta.
                    Math.max(Club.EDAD_MINIMA, Math.min(99, n.path("edadMinima").asInt(Club.EDAD_MINIMA))),
                    recortar(n.path("vestimenta").asText("").trim(), 120),
                    EstadoDeEvento.BORRADOR,
                    null,
                    null,
                    cartel.stream().limit(10).toList()));

            int quedan = sala.getAforo();
            for (JsonNode t : n.path("entradas")) {
                String nombre = recortar(t.path("nombre").asText("").trim(), 60);
                int precio = (int) Math.round(t.path("precioCentimos").asDouble(-1));
                int cantidad = (int) Math.round(t.path("cantidad").asDouble(0));
                if (nombre.length() < 2 || precio < 0 || precio > 1_000_000 || cantidad < 1) {
                    avisos.add("Una entrada de «" + titulo + "» venía incompleta; se omite.");
                    continue;
                }
                if (quedan == 0) {
                    avisos.add("«" + nombre + "» no cabe en el aforo de " + sala.getNombre() + "; se omite.");
                    continue;
                }
                if (cantidad > quedan) {
                    avisos.add("«" + nombre + "» pedía " + cantidad + " entradas y solo quedaban " + quedan
                            + " de aforo; se han puesto " + quedan + ".");
                    cantidad = quedan;
                }
                quedan -= cantidad;
                programacion.anadirTipo(noche.getSlug(), new GestionDeEventos.DatosDeTipo(
                        nombre,
                        recortar(t.path("descripcion").asText("").trim(), 300),
                        precio,
                        cantidad,
                        Math.max(1, Math.min(20, t.path("maximoPorPedido").asInt(6))),
                        0,
                        true));
            }

            creadas.add(new Enlace(noche.getTitulo(), "/panel/eventos/" + noche.getSlug()));
        }

        if (creadas.isEmpty()) {
            return new Resultado(false, "No se ha podido crear ninguna de las noches propuestas.", avisos);
        }
        return new Resultado(true, (creadas.size() == 1 ? "1 noche creada" : creadas.size() + " noches creadas")
                + " en borrador. Sube el flyer o el video y publícalas cuando estén listas.", avisos, creadas);
    }

    /** Una hora «HH:MM»; si no lo es, la de siempre con un aviso. */
    private static LocalTime hora(String texto, LocalTime porDefecto, String titulo, String cual, List<String> avisos) {
        try {
            return LocalTime.parse(texto.length() == 4 ? "0" + texto : texto);
        } catch (DateTimeParseException e) {
            avisos.add("La hora de " + cual + " de «" + titulo + "» no era válida; se ha puesto " + porDefecto + ".");
            return porDefecto;
        }
    }

    /** El título de la noche que ya hay ese día (sin contar las canceladas), si la hay. */
    private Optional<String> nocheDelDia(LocalDate dia) {
        // La noche va de las 06:00 de ese día a las 06:00 del siguiente, en hora de la sala.
        Instant desde = dia.atTime(6, 0).atZone(Club.ZONA).toInstant();
        return sql.sql("""
                select titulo from "Evento"
                where estado <> 'CANCELADO' and "empiezaEn" >= :desde and "empiezaEn" < :hasta
                order by "empiezaEn" limit 1
                """)
                .param("desde", LocalDateTime.ofInstant(desde, ZoneOffset.UTC))
                .param("hasta", LocalDateTime.ofInstant(desde.plusSeconds(24 * 3600), ZoneOffset.UTC))
                .query(String.class)
                .optional();
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
