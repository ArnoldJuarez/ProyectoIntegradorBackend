package pe.ssiomama.backend.publico;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.ssiomama.backend.clientes.CanalDeInteraccion;
import pe.ssiomama.backend.clientes.Cliente;
import pe.ssiomama.backend.clientes.ClienteRepository;
import pe.ssiomama.backend.clientes.Interaccion;
import pe.ssiomama.backend.clientes.InteraccionRepository;
import pe.ssiomama.backend.clientes.TipoDeInteraccion;
import pe.ssiomama.backend.eventos.EstadoDeEvento;
import pe.ssiomama.backend.eventos.Evento;
import pe.ssiomama.backend.reservados.EstadoDeReserva;
import pe.ssiomama.backend.reservados.MesaVip;
import pe.ssiomama.backend.reservados.MesaVipRepository;
import pe.ssiomama.backend.reservados.Reserva;
import pe.ssiomama.backend.reservados.ReservaRepository;

/**
 * Reservados: qué mesas hay libres para una noche y cómo pedir una.
 *
 * <p>Pedir un reservado <b>no lo aparta</b>: deja una solicitud en
 * {@code REQUESTED} y alguien del local llama para confirmarla y acordar el
 * depósito. La web no cobra ni compromete la mesa, y por eso el mensaje de
 * vuelta lo dice con esas palabras.
 */
@RestController
@RequestMapping("/api/publico")
public class ReservadosController {

    private final MesaVipRepository mesas;
    private final ReservaRepository reservas;
    private final CarteleraRepository cartelera;
    private final ClienteRepository clientes;
    private final InteraccionRepository interacciones;

    public ReservadosController(
            MesaVipRepository mesas,
            ReservaRepository reservas,
            CarteleraRepository cartelera,
            ClienteRepository clientes,
            InteraccionRepository interacciones) {
        this.mesas = mesas;
        this.reservas = reservas;
        this.cartelera = cartelera;
        this.clientes = clientes;
        this.interacciones = interacciones;
    }

    public record MesaDisponible(
            String id,
            String nombre,
            String zona,
            int plazas,
            int consumoMinimoCentimos,
            boolean libre) {}

    public record Solicitud(
            @NotBlank(message = "Falta la noche") String slug,
            @NotBlank(message = "Elige un reservado") String mesaId,
            @Min(value = 1, message = "Dinos cuántos venís")
            @Max(value = 30, message = "Para grupos de más de 30 llámanos")
            int personas,
            @Size(max = 500, message = "El comentario es demasiado largo") String notas,
            @NotBlank(message = "Falta el nombre")
            @Size(max = 80, message = "Ese nombre es demasiado largo")
            String nombre,
            @Size(max = 80, message = "Ese apellido es demasiado largo") String apellido,
            @NotBlank(message = "Falta el correo")
            @Email(message = "El correo no es válido")
            @Size(max = 160)
            String email,
            @NotBlank(message = "Necesitamos un teléfono para confirmar la mesa")
            @Size(min = 6, max = 30, message = "Ese teléfono no parece completo")
            String telefono) {}

    public record Respuesta(String id, String mensaje) {}

    /** Un 409 con un mensaje que se le puede enseñar a quien lo pidió. */
    private static ResponseStatusException noSePuede(String motivo) {
        return new ResponseStatusException(HttpStatus.CONFLICT, motivo);
    }

    /**
     * Los reservados de una noche, marcando cuáles siguen libres.
     *
     * <p>Se devuelven también los pedidos, marcados como no libres, en vez de
     * esconderlos: ver que «el box grande está cogido» explica por qué no
     * aparece, y esconderlo parece un fallo de la web.
     */
    @GetMapping("/reservados/{slug}")
    @Transactional(readOnly = true)
    public List<MesaDisponible> deLaNoche(@PathVariable String slug) {
        // Comprueba que la noche existe y es pública antes de enseñar nada.
        cartelera.buscarPublicaPorSlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Set<String> pedidas =
                Set.copyOf(reservas.idsDeMesasPedidas(slug, EstadoDeReserva.CANCELLED));

        return mesas.buscarDisponibles().stream()
                .map(m -> new MesaDisponible(
                        m.getId(),
                        m.getNombre(),
                        m.getZona() == null ? null : m.getZona().getNombre(),
                        m.getPlazas(),
                        m.getConsumoMinimoCentimos(),
                        !pedidas.contains(m.getId())))
                .toList();
    }

    @PostMapping("/reservas")
    @Transactional
    public ResponseEntity<Respuesta> pedir(@Valid @RequestBody Solicitud solicitud) {
        Evento noche = cartelera
                .buscarPublicaPorSlug(solicitud.slug())
                .orElseThrow(() -> noSePuede("Esa fecha no admite reservas."));

        if (noche.getEstado() != EstadoDeEvento.PUBLISHED
                || noche.getEmpiezaA().isBefore(Instant.now())) {
            throw noSePuede("Esa fecha no admite reservas.");
        }

        MesaVip mesa = mesas.findById(solicitud.mesaId())
                .filter(MesaVip::isActiva)
                .orElseThrow(() -> noSePuede("Ese reservado ya no está disponible."));

        if (solicitud.personas() > mesa.getPlazas()) {
            throw noSePuede(
                    "%s es para un máximo de %d personas."
                            .formatted(mesa.getNombre(), mesa.getPlazas()));
        }

        String email = solicitud.email().trim().toLowerCase(Locale.ROOT);
        Cliente cliente = clientes
                .findByEmailIgnoreCase(email)
                .map(existente -> {
                    // Aquí SÍ se pisan los datos, al revés que en el boletín: el
                    // formulario los pide enteros y un teléfono viejo es peor
                    // que ninguno cuando hay que llamar.
                    existente.actualizarContacto(
                            solicitud.nombre().trim(),
                            solicitud.apellido(),
                            solicitud.telefono().trim());
                    return existente;
                })
                .orElseGet(() -> clientes.save(Cliente.nuevoConContacto(
                        solicitud.nombre().trim(),
                        solicitud.apellido(),
                        email,
                        solicitud.telefono().trim())));

        Reserva reserva =
                Reserva.solicitar(noche, mesa, cliente, solicitud.personas(), solicitud.notas());

        try {
            reservas.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException e) {
            /*
             * Entre comprobar que la mesa estaba libre e insertar pasa un
             * instante, y dos personas pidiendo a la vez pasan las dos la
             * comprobación. Quien lo impide de verdad es el unique (eventId,
             * tableId) de la base.
             *
             * El flush es lo que hace que la violación salte AQUÍ y no al
             * cerrar la transacción, que es donde ya no se puede convertir en
             * un mensaje entendible. Sin esto, el segundo en pedir recibiría un
             * 500 en vez de saber que llegó tarde.
             */
            throw noSePuede("Ese reservado acaban de pedirlo para esa noche.");
        }

        interacciones.save(Interaccion.registrar(
                cliente,
                TipoDeInteraccion.RESERVATION,
                CanalDeInteraccion.WEB,
                "Solicitó %s para %d personas en \"%s\"."
                        .formatted(mesa.getNombre(), solicitud.personas(), noche.getTitulo())));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new Respuesta(
                        reserva.getId(),
                        "Solicitud recibida. Te llamamos para confirmarla y acordar el depósito."));
    }
}
