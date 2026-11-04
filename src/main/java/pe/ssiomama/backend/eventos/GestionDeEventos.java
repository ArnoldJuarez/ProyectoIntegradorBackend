package pe.ssiomama.backend.eventos;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.entradas.EntradaRepository;

/**
 * Crear y editar noches desde el panel.
 *
 * <h2>Las reglas que protegen a quien ya compró</h2>
 *
 * <p>Todo lo que hay aquí es de gestión salvo tres comprobaciones, y esas tres
 * existen por la misma razón: <b>hay gente con una entrada en el móvil</b>.
 *
 * <ul>
 *   <li>No se mueve la fecha de una noche con entradas emitidas. Quien compró
 *       para el viernes se presentaría un viernes que ya no existe.
 *   <li>No se baja el cupo de un tipo por debajo de lo vendido. Esas entradas
 *       existen y su portador aparecerá en la puerta.
 *   <li>No se ofertan más entradas que el aforo de la sala. Eso deja de ser un
 *       detalle de interfaz y pasa a ser un problema con la autoridad.
 * </ul>
 *
 * <p>La del aforo se aplica <b>donde crece la oferta</b> —al añadir o agrandar
 * un tipo de entrada—, no al editar la noche. Los datos que vienen del sistema
 * anterior ya ofertan 610 entradas en una sala de 500, y exigir la regla al
 * editar dejaría esas noches congeladas: no se podría ni corregir un título.
 * Al mudar de sala sí se comprueba algo más flojo pero más grave — que en la
 * sala nueva quepa la gente que ya compró.
 *
 * <p>Todas se comprueban aquí y no en el formulario. El navegador puede
 * ayudar avisando antes, pero quien decide es el servidor: una comprobación que
 * solo vive en el cliente es una comprobación que no existe.
 */
@Service
public class GestionDeEventos {

    /** Lo más que puede durar una noche. Más que eso es un error de tecleo. */
    private static final Duration DURACION_MAXIMA = Duration.ofHours(24);

    /**
     * Lo que se acepta como video: un archivo subido aquí o una dirección https
     * (un MP4 alojado fuera, o YouTube). Un «javascript:» pegado en el campo
     * acabaría en el {@code src} de la ficha pública.
     */
    private static final Pattern VIDEO_ACEPTADO =
            Pattern.compile("^(" + Pattern.quote(Medios.RUTA_VIDEOS) + "[a-z0-9.-]+|https://\\S+)$");

    private final EventoRepository eventos;
    private final TipoDeEntradaRepository tipos;
    private final SalaRepository salas;
    private final ArtistaRepository artistas;
    private final ActuacionRepository cartel;
    private final EntradaRepository entradas;
    private final JdbcClient sql;

    public GestionDeEventos(
            EventoRepository eventos,
            TipoDeEntradaRepository tipos,
            SalaRepository salas,
            ArtistaRepository artistas,
            ActuacionRepository cartel,
            EntradaRepository entradas,
            JdbcClient sql) {
        this.eventos = eventos;
        this.tipos = tipos;
        this.salas = salas;
        this.artistas = artistas;
        this.cartel = cartel;
        this.entradas = entradas;
        this.sql = sql;
    }

    // ── Lo que llega del formulario ───────────────────────────────

    /**
     * Los datos de una noche.
     *
     * @param artistaIds el cartel, en orden. El primero es cabeza de cartel.
     */
    public record DatosDeNoche(
            String titulo,
            String descripcion,
            String salaId,
            Instant puertasA,
            Instant terminaA,
            int edadMinima,
            String vestimenta,
            EstadoDeEvento estado,
            String portada,
            String video,
            List<String> artistaIds) {

        /** Una noche sin video, que es lo normal. */
        public DatosDeNoche(
                String titulo,
                String descripcion,
                String salaId,
                Instant puertasA,
                Instant terminaA,
                int edadMinima,
                String vestimenta,
                EstadoDeEvento estado,
                String portada,
                List<String> artistaIds) {
            this(titulo, descripcion, salaId, puertasA, terminaA, edadMinima, vestimenta, estado, portada,
                    null, artistaIds);
        }
    }

    public record DatosDeTipo(
            String nombre,
            String descripcion,
            int precioCentimos,
            int cantidad,
            int maximoPorPedido,
            int consumicionesIncluidas,
            boolean activo) {}

    // ── Noches ────────────────────────────────────────────────────

    /** Crea una noche con su cartel. */
    @Transactional
    public Evento crear(DatosDeNoche datos) {
        comprobarHorario(datos.puertasA(), datos.terminaA());
        comprobarVideo(datos.video());

        Sala sala = salas.findById(datos.salaId())
                .orElseThrow(() -> new EventoRechazado("Esa sala no existe."));

        String slug = Slugs.libre(datos.titulo(), candidato -> eventos.existsBySlug(candidato));

        Evento noche = Evento.crear(
                slug,
                datos.titulo().trim(),
                datos.descripcion(),
                sala,
                datos.puertasA(),
                datos.terminaA(),
                datos.edadMinima(),
                datos.vestimenta(),
                datos.estado());
        noche.cambiarPortada(datos.portada());
        noche.cambiarVideo(datos.video());

        eventos.save(noche);
        montarElCartel(noche, datos.artistaIds());

        return noche;
    }

    /**
     * Cambia una noche que ya existe.
     *
     * <p>El slug se queda como está aunque cambie el título: es la dirección
     * pública, y las que ya se compartieron tienen que seguir funcionando.
     */
    @Transactional
    public Evento editar(String slug, DatosDeNoche datos) {
        comprobarHorario(datos.puertasA(), datos.terminaA());
        comprobarVideo(datos.video());

        Evento noche = eventos.findBySlug(slug)
                .orElseThrow(() -> new EventoRechazado("Esa noche no existe."));

        Sala sala = salas.findById(datos.salaId())
                .orElseThrow(() -> new EventoRechazado("Esa sala no existe."));

        // Mover la fecha con entradas fuera deja a gente con una entrada para
        // una noche que ya no existe. Se cancela y se crea otra, que además deja
        // rastro de lo que pasó.
        if (noche.cambiaLaFecha(datos.puertasA())) {
            long emitidas = entradas.contarVigentesDe(slug);
            if (emitidas > 0) {
                throw new EventoRechazado(
                        "Hay %d entradas emitidas para esta fecha. Cancela la noche en vez de moverla."
                                .formatted(emitidas));
            }
        }

        // Aquí NO se comprueba el aforo total, y es a propósito. Editar el título
        // de una noche no cambia cuántas entradas hay ofertadas, y exigir la
        // regla en este punto haría imposible tocar nada en una noche que ya
        // estuviera pasada de aforo: castigaría a quien viene a arreglarlo en
        // vez de a quien lo provocó. La regla vive donde crece la oferta, que es
        // al añadir o agrandar un tipo de entrada.
        //
        // Lo que sí se comprueba es mudarse a una sala más pequeña que la gente
        // que ya compró. Vender de más es un problema; meter a quien ya tiene su
        // entrada en un sitio donde no cabe es otro peor.
        if (!sala.getId().equals(noche.getSala().getId())) {
            comprobarQueCabeLoVendido(sala, noche);
        }

        noche.actualizar(
                datos.titulo().trim(),
                datos.descripcion(),
                sala,
                datos.puertasA(),
                datos.terminaA(),
                datos.edadMinima(),
                datos.vestimenta(),
                datos.estado());
        noche.cambiarPortada(datos.portada());
        noche.cambiarVideo(datos.video());

        // El cartel se reemplaza entero. Hay que vaciar antes de escribir, y el
        // flush explícito es lo que garantiza ese orden: sin él, Hibernate
        // podría mandar los INSERT antes del DELETE y chocar con la clave.
        cartel.borrarElCartelDe(noche.getId());
        cartel.flush();
        montarElCartel(noche, datos.artistaIds());

        return noche;
    }

    /**
     * Borra una noche que no ha dejado rastro.
     *
     * <p>Existe para lo que se crea y no llega a nada: un borrador que se
     * duplicó, una propuesta de la IA que se aprobó y al final no convence.
     * Una noche <b>con historia</b> —pedidos, reservas, listas, accesos, ventas
     * de barra, gastos— no se borra: se cancela, que deja constancia de lo que
     * pasó. Y una publicada tampoco, aunque no haya vendido nada: su dirección
     * ya puede estar compartida en redes.
     *
     * @return las direcciones de su flyer y su video, para borrar los archivos
     */
    @Transactional
    public List<String> borrar(String slug) {
        Evento noche = eventos.findBySlug(slug)
                .orElseThrow(() -> new EventoRechazado("Esa noche no existe."));

        if (noche.getEstado() != EstadoDeEvento.BORRADOR && noche.getEstado() != EstadoDeEvento.CANCELADO) {
            throw new EventoRechazado(
                    "Solo se borran noches en borrador o canceladas. Esta está «%s»: cámbiala a cancelada."
                            .formatted(noche.getEstado().name().toLowerCase()));
        }

        String rastro = sql.sql("""
                select concat_ws(', ',
                    case when exists (select 1 from "Pedido" where "eventoId" = :id) then 'pedidos' end,
                    case when exists (select 1 from "ReservaBox" where "eventoId" = :id) then 'reservas' end,
                    case when exists (select 1 from "ListaInvitados" where "eventoId" = :id) then 'listas de invitados' end,
                    case when exists (select 1 from "Acceso" where "eventoId" = :id) then 'accesos' end,
                    case when exists (select 1 from "VentaBarra" where "eventoId" = :id) then 'ventas de barra' end,
                    case when exists (select 1 from "Gasto" where "eventoId" = :id) then 'gastos' end)
                """)
                .param("id", noche.getId())
                .query(String.class)
                .single();
        if (rastro != null && !rastro.isBlank()) {
            throw new EventoRechazado("No se puede borrar: tiene " + rastro + ". Cancélala en su lugar.");
        }

        List<String> medios = new java.util.ArrayList<>();
        if (noche.getPortada() != null) medios.add(noche.getPortada());
        if (noche.getVideo() != null) medios.add(noche.getVideo());

        /*
         * El cartel se borra entidad a entidad, NO con el borrado masivo de
         * editar(). Las actuaciones tienen la noche dentro de su clave
         * (@MapsId): si quedan gestionadas por Hibernate después de un DELETE
         * masivo, al hacer flush propaga el persist hacia la noche y deshace su
         * borrado en silencio («un-scheduling entity deletion»). Lo detectó el
         * test: la noche seguía ahí y ningún DELETE llegó a la base.
         *
         * Con las entradas, lo mismo por otro camino: si alguna sigue
         * gestionada apuntando a la noche borrada, el flush falla con «unsaved
         * transient instance». Se buscan con una consulta —la colección de la
         * noche puede estar desfasada— y se borran antes que ella.
         */
        cartel.deleteAll(cartel.buscarDeLaNoche(noche.getId()));
        tipos.deleteAll(tipos.buscarDeLaNoche(noche.getId()));
        noche.getTiposDeEntrada().clear();
        eventos.delete(noche);
        return medios;
    }

    // ── Tipos de entrada ──────────────────────────────────────────

    /** Añade un tipo de entrada a una noche. */
    @Transactional
    public TipoDeEntrada anadirTipo(String slug, DatosDeTipo datos) {
        Evento noche = eventos.buscarPorSlugConDetalle(slug)
                .orElseThrow(() -> new EventoRechazado("Esa noche no existe."));

        comprobarDatosDelTipo(datos);
        comprobarAforo(noche.getSala(), noche.getTiposDeEntrada(), null, datos.cantidad());

        TipoDeEntrada tipo = TipoDeEntrada.crear(
                noche,
                datos.nombre().trim(),
                datos.descripcion(),
                datos.precioCentimos(),
                datos.cantidad(),
                datos.maximoPorPedido(),
                datos.consumicionesIncluidas(),
                datos.activo());

        return tipos.save(tipo);
    }

    /** Cambia un tipo de entrada que ya existe. */
    @Transactional
    public TipoDeEntrada editarTipo(String tipoId, DatosDeTipo datos) {
        TipoDeEntrada tipo = tipos.buscarConEvento(tipoId)
                .orElseThrow(() -> new EventoRechazado("Ese tipo de entrada no existe."));

        comprobarDatosDelTipo(datos);

        // El cupo no baja de lo vendido: esas entradas existen y su portador se
        // presentará en la puerta con ellas.
        if (datos.cantidad() < tipo.getVendidas()) {
            throw new EventoRechazado(
                    "Ya se han vendido %d. No puedes bajar el cupo por debajo."
                            .formatted(tipo.getVendidas()));
        }

        Evento noche = eventos.buscarPorSlugConDetalle(tipo.getEvento().getSlug()).orElseThrow();
        comprobarAforo(noche.getSala(), noche.getTiposDeEntrada(), tipoId, datos.cantidad());

        tipo.actualizar(
                datos.nombre().trim(),
                datos.descripcion(),
                datos.precioCentimos(),
                datos.cantidad(),
                datos.maximoPorPedido(),
                datos.consumicionesIncluidas(),
                datos.activo());

        return tipo;
    }

    /**
     * Quita un tipo de entrada.
     *
     * <p>Si tiene ventas <b>no se borra</b>: se retira de la venta. Borrarlo
     * dejaría huérfanas las entradas ya emitidas, que apuntan a él para saber su
     * nombre y sus consumiciones.
     *
     * @return cierto si se retiró en vez de borrarse
     */
    @Transactional
    public boolean quitarTipo(String tipoId) {
        TipoDeEntrada tipo = tipos.findById(tipoId)
                .orElseThrow(() -> new EventoRechazado("Ese tipo de entrada no existe."));

        if (tipo.tieneVentas()) {
            tipo.retirarDeLaVenta();
            return true;
        }

        tipos.delete(tipo);
        return false;
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    /** Las horas tienen que tener sentido antes de guardar nada. */
    private void comprobarHorario(Instant puertas, Instant fin) {
        if (puertas == null || fin == null) {
            throw new EventoRechazado("Faltan las horas de apertura o de cierre.");
        }
        if (!fin.isAfter(puertas)) {
            throw new EventoRechazado("El cierre tiene que ser posterior a la apertura.");
        }
        if (Duration.between(puertas, fin).compareTo(DURACION_MAXIMA) > 0) {
            throw new EventoRechazado("Una noche no puede durar más de 24 horas.");
        }
    }

    private void comprobarVideo(String video) {
        if (video == null || video.isBlank()) return;
        if (!VIDEO_ACEPTADO.matcher(video.trim()).matches()) {
            throw new EventoRechazado("El video tiene que ser un archivo subido o una dirección https.");
        }
    }

    private void comprobarDatosDelTipo(DatosDeTipo datos) {
        if (datos.cantidad() < 1) {
            throw new EventoRechazado("Tiene que haber al menos una entrada.");
        }
        if (datos.precioCentimos() < 0) {
            throw new EventoRechazado("El precio no puede ser negativo.");
        }
        if (datos.maximoPorPedido() < 1) {
            throw new EventoRechazado("El máximo por pedido tiene que ser de una entrada o más.");
        }
    }

    /**
     * Que no se oferte más de lo que cabe en la sala.
     *
     * @param excluir el tipo que se está editando, para no contarlo dos veces
     * @param anadir cuántas entradas suma la operación en curso
     */
    private void comprobarAforo(
            Sala sala, List<TipoDeEntrada> existentes, String excluir, int anadir) {

        int yaOfertadas = existentes.stream()
                .filter(t -> excluir == null || !t.getId().equals(excluir))
                .mapToInt(TipoDeEntrada::getCantidad)
                .sum();

        if (yaOfertadas + anadir > sala.getAforo()) {
            throw new EventoRechazado(
                    "Superarías el aforo de %s (%d). Ya hay %d entradas a la venta."
                            .formatted(sala.getNombre(), sala.getAforo(), yaOfertadas));
        }
    }

    /**
     * Que en la sala nueva quepa al menos la gente que ya compró.
     *
     * <p>Es una regla más floja que la del aforo total, y lo es a propósito: no
     * mira lo ofertado sino lo <b>vendido</b>. Una noche puede estar pasada de
     * oferta por un error antiguo sin que eso impida moverla a otra sala; lo que
     * no puede es acabar en un sitio donde no entre quien ya tiene su entrada.
     */
    private void comprobarQueCabeLoVendido(Sala destino, Evento noche) {
        int vendidas = noche.getTiposDeEntrada().stream()
                .mapToInt(TipoDeEntrada::getVendidas)
                .sum();

        if (vendidas > destino.getAforo()) {
            throw new EventoRechazado(
                    "En %s (%d de aforo) no cabe la gente que ya compró: son %d entradas."
                            .formatted(destino.getNombre(), destino.getAforo(), vendidas));
        }
    }

    /**
     * Monta el cartel en el orden que llega.
     *
     * <p>El primero de la lista es cabeza de cartel. Es una convención del
     * formulario —se arrastra al de arriba— y no una casilla aparte, porque en
     * la práctica el orden y la jerarquía son la misma decisión.
     */
    private void montarElCartel(Evento noche, List<String> artistaIds) {
        if (artistaIds == null || artistaIds.isEmpty()) {
            return;
        }

        List<String> sinRepetir = artistaIds.stream().distinct().toList();

        for (int i = 0; i < sinRepetir.size(); i++) {
            Artista artista = artistas.findById(sinRepetir.get(i))
                    .orElseThrow(() -> new EventoRechazado("Alguno de los artistas no existe."));
            cartel.save(Actuacion.enElCartel(noche, artista, i == 0));
        }
    }
}
