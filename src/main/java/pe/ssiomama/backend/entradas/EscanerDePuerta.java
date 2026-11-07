package pe.ssiomama.backend.entradas;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.entradas.ResultadoDeEscaneo.MotivoDeRechazo;
import pe.ssiomama.backend.eventos.Puerta;
import pe.ssiomama.backend.eventos.PuertaRepository;

/**
 * La decisión de la puerta: este código pasa o no pasa.
 *
 * <p>Está separado del controlador por una razón concreta y no por costumbre:
 * la carrera entre dos porteros escaneando el mismo QR a la vez se manifiesta
 * como una violación de la clave única de {@code "Acceso"}, y esa excepción
 * <b>no salta dentro del método transaccional</b>: salta al confirmar, cuando el
 * proxy de Spring ya ha salido de él. Si la comprobación viviera en el mismo
 * método, sería imposible atraparla ahí. El controlador la recoge desde fuera.
 *
 * <h2>Por qué SERIALIZABLE</h2>
 *
 * <p>Es el nivel más caro y aquí se justifica. Con el nivel por defecto de
 * PostgreSQL —{@code READ COMMITTED}— dos transacciones simultáneas pueden leer
 * las dos que la entrada está sin usar, decidir las dos que pasa, y una de las
 * dos escribir. La única línea de defensa sería la clave única, que da un error
 * feo en vez de una decisión.
 *
 * <p>El coste es despreciable: son unos pocos escaneos por minuto, no un pico de
 * ventas.
 */
@Service
public class EscanerDePuerta {

    /**
     * Cuánto antes de empezar se admite gente.
     *
     * <p>Seis horas parece mucho para una discoteca, y lo es a propósito: cubre
     * las pruebas de sonido, al personal que entra con su entrada y la cola que
     * se forma antes de abrir. Ajustado más fino, un evento que se retrasa deja
     * fuera a quien ya está en la puerta.
     */
    private static final Duration MARGEN_DE_APERTURA = Duration.ofHours(6);

    /** Para decir «entró a las 01:14» en la hora de la sala, no en UTC. */
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** «viernes 6 de noviembre», para decir desde cuándo vale una entrada. */
    private static final DateTimeFormatter DIA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", java.util.Locale.forLanguageTag("es-PE"));

    private final EntradaRepository entradas;
    private final EscaneoRepository escaneos;
    private final PuertaRepository puertas;
    private final FirmaDeEntrada firma;

    public EscanerDePuerta(
            EntradaRepository entradas,
            EscaneoRepository escaneos,
            PuertaRepository puertas,
            FirmaDeEntrada firma) {
        this.entradas = entradas;
        this.escaneos = escaneos;
        this.puertas = puertas;
        this.firma = firma;
    }

    /**
     * Lee un código de QR y, si procede, registra el acceso.
     *
     * <p>El orden de las comprobaciones no es casual: va de lo que se resuelve
     * sin tocar la base a lo que necesita consultarla, y dentro de eso, de lo
     * más grave a lo más común. Una entrada falsa se descarta con la firma, sin
     * consultar nada.
     *
     * @param portero quién está escaneando; queda registrado en el acceso
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ResultadoDeEscaneo escanear(String codigo, Usuario portero, Instant ahora) {

        // 1. La firma. Sin tocar la base: un código inventado muere aquí.
        Long idEntrada = firma.decodificar(codigo);
        if (idEntrada == null) {
            return ResultadoDeEscaneo.rechazo(
                    MotivoDeRechazo.FALSA, "Entrada falsa: la firma no es válida.");
        }

        var quiza = entradas.buscarPorIdParaLaPuerta(idEntrada);
        if (quiza.isEmpty()) {
            // La firma cuadraba pero no hay tal entrada. O se borró, o alguien
            // firmó un id inventado con el secreto correcto, que sería peor.
            return ResultadoDeEscaneo.rechazo(
                    MotivoDeRechazo.NO_EXISTE, "Esta entrada no existe.");
        }
        Entrada entrada = quiza.get();

        // 2. Anulada o devuelta: no hay nada que discutir, el dinero se devolvió.
        if (entrada.getEstado() == EstadoDeEntrada.ANULADA
                || entrada.getEstado() == EstadoDeEntrada.DEVUELTO) {
            return ResultadoDeEscaneo.rechazo(
                    MotivoDeRechazo.ANULADA, "Entrada anulada o devuelta.", entrada);
        }

        // 3. Ya entró. Se dice la hora y quién la escaneó, porque «ya usada» a
        //    secas es lo que hace que la discusión no termine.
        if (entrada.yaSeUso()) {
            Escaneo previo = entrada.getEscaneo();
            String cuando = HORA.format(previo.getEscaneadoEn().atZone(LIMA));
            return ResultadoDeEscaneo.rechazo(
                    MotivoDeRechazo.REPETIDA,
                    "Ya entró a las %s (escaneó %s)."
                            .formatted(cuando, previo.getEscaneadoPor().getNombre()),
                    entrada);
        }

        // 4. Es de otra noche. El rechazo más frecuente con diferencia.
        var evento = entrada.getEvento();
        Instant abre = evento.getEmpiezaA().minus(MARGEN_DE_APERTURA);
        Instant cierra = evento.getTerminaA();
        // Se dice cuándo abre o que ya pasó: «no es para esta noche» confunde
        // cuando la entrada es justo de la próxima noche y la puerta aún no abre.
        if (ahora.isBefore(abre)) {
            return ResultadoDeEscaneo.rechazo(
                    MotivoDeRechazo.OTRA_FECHA,
                    "Esta entrada es para «%s»: se puede entrar desde el %s a las %s."
                            .formatted(evento.getTitulo(),
                                    DIA.format(abre.atZone(LIMA)), HORA.format(abre.atZone(LIMA))),
                    entrada);
        }
        if (ahora.isAfter(cierra)) {
            return ResultadoDeEscaneo.rechazo(
                    MotivoDeRechazo.OTRA_FECHA,
                    "Esta entrada es para «%s», que ya terminó.".formatted(evento.getTitulo()),
                    entrada);
        }

        // 5. Pasa. Se marca la entrada y se registra el hecho.
        Puerta porDonde = puertas.buscarEntradaDe(evento.getSala().getId()).orElse(null);
        entrada.marcarUsada();
        escaneos.save(Escaneo.registrar(entrada, portero, porDonde, ahora));

        return ResultadoDeEscaneo.adelante(entrada, ahora);
    }
}
