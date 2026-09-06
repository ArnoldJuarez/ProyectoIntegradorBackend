package pe.ssiomama.backend.entradas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.entradas.ResultadoDeEscaneo.MotivoDeRechazo;
import pe.ssiomama.backend.eventos.EstadoDeEvento;
import pe.ssiomama.backend.eventos.Evento;
import pe.ssiomama.backend.eventos.EventoRepository;

/**
 * El control de puerta: el escáner y lo que se ve alrededor.
 *
 * <p>Es la única pantalla del sistema que usa alguien de pie, con frío y con
 * cola delante. Eso marca dos decisiones que no aparecen en los demás
 * controladores:
 *
 * <ul>
 *   <li>Un rechazo responde <b>200</b>, no un error. Una entrada de otra noche
 *       no es un fallo de la aplicación, es una respuesta.
 *   <li>{@link #noche()} devuelve algo aunque no haya evento. Una pantalla en
 *       blanco no distingue «hoy no hay nada» de «se cayó el servidor».
 * </ul>
 */
@RestController
@RequestMapping("/api/puerta")
public class EscanerController {

    /**
     * Cuántos accesos recientes acompañan al estado de la noche.
     *
     * <p>Doce entran en una pantalla de móvil sin desplazarse, que es donde se
     * mira esto. Más no se leen; menos no dejan comprobar el que se acaba de
     * escanear.
     */
    private static final int ULTIMOS_ACCESOS = 12;

    /** Tiene que ser el mismo margen que aplica {@link EscanerDePuerta}. */
    private static final Duration MARGEN_DE_APERTURA = Duration.ofHours(6);

    private final EscanerDePuerta escaner;
    private final EventoRepository eventos;
    private final EntradaRepository entradas;
    private final EscaneoRepository escaneos;

    public EscanerController(
            EscanerDePuerta escaner,
            EventoRepository eventos,
            EntradaRepository entradas,
            EscaneoRepository escaneos) {
        this.escaner = escaner;
        this.eventos = eventos;
        this.entradas = entradas;
        this.escaneos = escaneos;
    }

    // ── Lo que entra y sale por la API ────────────────────────────

    /** El código leído del QR, tal cual. */
    public record CodigoLeido(@NotBlank(message = "Código vacío.") String codigo) {}

    /** Cuánta gente ha entrado por cada acceso. */
    public record RepartoPorPuerta(String puerta, long personas) {}

    /** Una línea del histórico reciente. */
    public record AccesoReciente(
            String titular, String tipoDeEntrada, String puerta, String portero, Instant cuando) {

        static AccesoReciente de(Escaneo e) {
            return new AccesoReciente(
                    e.getEntrada().getTitular(),
                    e.getEntrada().getTipoDeEntrada().getNombre(),
                    e.getPuerta() == null ? null : e.getPuerta().getNombre(),
                    e.getEscaneadoPor().getNombre(),
                    e.getEscaneadoEn());
        }
    }

    /**
     * El estado de la noche.
     *
     * <p>Los campos de la noche van a nulo cuando no hay ninguna, en vez de
     * repartirse en dos formas distintas de respuesta: el frontend mira
     * {@code hayNoche} y ya.
     *
     * @param enCurso si la noche está abierta ahora. En falso significa que lo
     *     que se enseña es la próxima, y que el escáner todavía rechazaría.
     * @param dentro cuánta gente ha pasado. Es el aforo en vivo.
     */
    public record EstadoDeLaNoche(
            boolean hayNoche,
            boolean enCurso,
            String slug,
            String titulo,
            String sala,
            Instant puertasA,
            Instant empiezaA,
            Instant terminaA,
            int aforoDeLaSala,
            long emitidas,
            long dentro,
            List<RepartoPorPuerta> porPuerta,
            List<AccesoReciente> ultimos) {

        static EstadoDeLaNoche sinEvento() {
            return new EstadoDeLaNoche(
                    false, false, null, null, null, null, null, null, 0, 0, 0, List.of(), List.of());
        }
    }

    // ── Endpoints ─────────────────────────────────────────────────

    /**
     * Lee un QR y decide.
     *
     * <p>Aquí es donde se recoge la carrera entre dos porteros: el servicio hace
     * la comprobación dentro de una transacción serializable, pero si dos la
     * ganan a la vez el conflicto no aparece hasta confirmar, ya fuera del
     * método transaccional. Por eso se atrapa desde este lado.
     *
     * <p>Las dos excepciones que se recogen son distintas y llevan al mismo
     * sitio: {@link DataIntegrityViolationException} es la clave única de
     * {@code "Acceso"} —alguien escribió primero—, y
     * {@link PessimisticLockingFailureException} es el fallo de serialización
     * que PostgreSQL levanta cuando dos transacciones serializables se pisan. En
     * los dos casos lo cierto es lo mismo: esa entrada ya está dentro.
     */
    @PostMapping("/escanear")
    public ResultadoDeEscaneo escanear(
            @RequestBody @Valid CodigoLeido cuerpo, @AuthenticationPrincipal Usuario portero) {
        try {
            return escaner.escanear(cuerpo.codigo(), portero, Instant.now());
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException carrera) {
            return ResultadoDeEscaneo.rechazo(
                    MotivoDeRechazo.REPETIDA, "Esta entrada se acaba de escanear en otra puerta.");
        }
    }

    /**
     * Qué noche es y cómo va.
     *
     * <p>Busca primero la que está abierta; si no hay, la siguiente programada.
     * Los recuentos solo se piden cuando la noche está en curso: contar accesos
     * de un evento que aún no ha empezado da siempre cero, y son tres consultas
     * de más cada vez que la pantalla se refresca.
     */
    @GetMapping("/noche")
    @Transactional(readOnly = true)
    public EstadoDeLaNoche noche() {
        Instant ahora = Instant.now();

        var abierta = eventos.buscarNocheAbierta(
                EstadoDeEvento.PUBLICADO, ahora.plus(MARGEN_DE_APERTURA), ahora);

        if (abierta.isPresent()) {
            return conRecuentos(abierta.get(), true);
        }

        return eventos.buscarProximaNoche(EstadoDeEvento.PUBLICADO, ahora)
                .map(e -> conRecuentos(e, false))
                .orElseGet(EstadoDeLaNoche::sinEvento);
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    /** Monta la respuesta de una noche, con recuentos solo si ya está en curso. */
    private EstadoDeLaNoche conRecuentos(Evento noche, boolean enCurso) {
        long emitidas = 0;
        long dentro = 0;
        List<RepartoPorPuerta> porPuerta = List.of();
        List<AccesoReciente> ultimos = List.of();

        if (enCurso) {
            // Emitidas es lo que llegó a existir como entrada: las válidas más
            // las que ya pasaron. Las anuladas y devueltas no cuentan, porque
            // nadie va a presentarse con ellas.
            Map<EstadoDeEntrada, Long> porEstado = entradas.contarPorEstado(noche.getSlug()).stream()
                    .collect(Collectors.toMap(f -> (EstadoDeEntrada) f[0], f -> (Long) f[1]));
            emitidas = porEstado.getOrDefault(EstadoDeEntrada.VALIDA, 0L)
                    + porEstado.getOrDefault(EstadoDeEntrada.USADA, 0L);

            dentro = escaneos.countByEventoId(noche.getId());

            porPuerta = escaneos.contarPorPuerta(noche.getId()).stream()
                    .map(f -> new RepartoPorPuerta((String) f[0], (Long) f[1]))
                    .toList();

            ultimos = escaneos.buscarUltimosDe(noche.getId(), PageRequest.of(0, ULTIMOS_ACCESOS))
                    .stream()
                    .map(AccesoReciente::de)
                    .toList();
        }

        return new EstadoDeLaNoche(
                true,
                enCurso,
                noche.getSlug(),
                noche.getTitulo(),
                noche.getSala().getNombre(),
                noche.getPuertasA(),
                noche.getEmpiezaA(),
                noche.getTerminaA(),
                noche.getSala().getAforo(),
                emitidas,
                dentro,
                porPuerta,
                ultimos);
    }
}
