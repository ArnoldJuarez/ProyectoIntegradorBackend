package pe.ssiomama.backend.ia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * El chat de la web pública: cualquiera pregunta, el conserje contesta.
 *
 * <p>Público y sin cuenta, como el resto de la web. Cada mensaje se guarda en
 * {@code SesionChat}/{@code MensajeChat}, y cada respuesta enlaza a la ejecución
 * que la produjo: así se puede saber por qué dijo lo que dijo.
 *
 * <p>Si el proveedor no contesta, no se devuelve un error sino una respuesta
 * con el teléfono: quien pregunta por la web no tiene por qué saber que hay un
 * modelo detrás, y «llámanos» le sirve más que un 503.
 */
@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final Conserje conserje;
    private final RegistroDeIa registro;

    public ChatController(Conserje conserje, RegistroDeIa registro) {
        this.conserje = conserje;
        this.registro = registro;
    }

    public record TurnoPrevio(
            @Pattern(regexp = "usuario|asistente") String rol,
            @Size(max = 4000) String contenido) {}

    public record Pregunta(
            @Size(max = 64) String sesionId,
            @NotBlank(message = "Escribe una pregunta.")
            @Size(max = 1000, message = "La pregunta es demasiado larga.") String mensaje,
            @Size(max = 20) List<@Valid TurnoPrevio> historial) {}

    public record Contestacion(String sesionId, String respuesta) {}

    @PostMapping("/api/publico/chat")
    public Contestacion preguntar(@Valid @RequestBody Pregunta pregunta) {
        String mensaje = pregunta.mensaje().trim();
        String sesion = registro.sesionDeChat(pregunta.sesionId());
        registro.guardarMensaje(sesion, "user", mensaje, null);

        List<Conserje.Turno> historial = new ArrayList<>();
        if (pregunta.historial() != null) {
            pregunta.historial().forEach(t -> historial.add(new Conserje.Turno(t.rol(), t.contenido())));
        }
        historial.add(new Conserje.Turno("usuario", mensaje));

        try {
            var r = conserje.preguntar(historial);
            registro.guardarMensaje(sesion, "assistant", r.texto(), r.ejecucionId());
            return new Contestacion(sesion, r.texto());
        } catch (IaNoDisponible e) {
            log.warn("Conserje sin motor: {}", e.getMessage());
            return new Contestacion(sesion, "El asistente está desconectado ahora mismo. Llámanos al "
                    + Club.TELEFONO + " y te atendemos.");
        } catch (IaSinFormato e) {
            return new Contestacion(sesion, "Me he liado con esa consulta. Llámanos al "
                    + Club.TELEFONO + " y te lo resolvemos al momento.");
        } catch (RuntimeException e) {
            log.error("Error en el conserje", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No hemos podido responder ahora mismo.");
        }
    }
}
