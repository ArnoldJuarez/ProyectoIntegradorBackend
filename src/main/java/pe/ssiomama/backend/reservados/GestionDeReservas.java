package pe.ssiomama.backend.reservados;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.clientes.CanalDeInteraccion;
import pe.ssiomama.backend.clientes.Cliente;
import pe.ssiomama.backend.clientes.ClienteRepository;
import pe.ssiomama.backend.clientes.Interaccion;
import pe.ssiomama.backend.clientes.InteraccionRepository;
import pe.ssiomama.backend.clientes.TipoDeInteraccion;
import pe.ssiomama.backend.eventos.EstadoDeEvento;
import pe.ssiomama.backend.eventos.Evento;
import pe.ssiomama.backend.eventos.EventoRepository;

/**
 * Las reservas de box: pedirlas (desde la web o por teléfono) y llevarlas
 * hasta que el grupo se sienta o no aparece.
 *
 * <h2>El ciclo</h2>
 *
 * <pre>
 *   SOLICITADA ──confirmar──▶ CONFIRMADA ──llegan──▶ SENTADA
 *       │                        │  └──no vienen──▶ NO_VINO
 *       └─────cancelar───────────┴────────────────▶ CANCELADO ──reabrir──▶ SOLICITADA
 * </pre>
 *
 * <p>{@code SENTADA} y {@code NO_VINO} se pueden corregir entre sí: a las dos
 * de la mañana alguien marca mal, y obligar a cancelar para arreglarlo
 * falsearía el histórico de quién falla.
 */
@Service
public class GestionDeReservas {

    private static final Map<EstadoDeReserva, Set<EstadoDeReserva>> PASOS = Map.of(
            EstadoDeReserva.SOLICITADA, EnumSet.of(EstadoDeReserva.CONFIRMADA, EstadoDeReserva.CANCELADO),
            EstadoDeReserva.CONFIRMADA,
                    EnumSet.of(EstadoDeReserva.SENTADA, EstadoDeReserva.NO_VINO, EstadoDeReserva.CANCELADO),
            EstadoDeReserva.SENTADA, EnumSet.of(EstadoDeReserva.NO_VINO),
            EstadoDeReserva.NO_VINO, EnumSet.of(EstadoDeReserva.SENTADA),
            EstadoDeReserva.CANCELADO, EnumSet.of(EstadoDeReserva.SOLICITADA));

    private final ReservaRepository reservas;
    private final MesaVipRepository mesas;
    private final EventoRepository eventos;
    private final ClienteRepository clientes;
    private final InteraccionRepository interacciones;

    public GestionDeReservas(
            ReservaRepository reservas,
            MesaVipRepository mesas,
            EventoRepository eventos,
            ClienteRepository clientes,
            InteraccionRepository interacciones) {
        this.reservas = reservas;
        this.mesas = mesas;
        this.eventos = eventos;
        this.clientes = clientes;
        this.interacciones = interacciones;
    }

    /** Quién pide qué. Igual venga de la web o de quien atiende el teléfono. */
    public record Solicitud(
            String slug,
            Long mesaId,
            int personas,
            String notas,
            String nombre,
            String apellido,
            String email,
            String telefono) {}

    /**
     * Deja una solicitud de box.
     *
     * @param canal {@code WEB} desde la web pública, {@code TELEFONO} o
     *     {@code WHATSAPP} cuando la apunta alguien del local desde el panel
     */
    @Transactional
    public Reserva solicitar(Solicitud s, CanalDeInteraccion canal) {
        Evento noche = eventos.findBySlug(s.slug())
                .orElseThrow(() -> new ReservaRechazada("Esa fecha no admite reservas."));

        // Desde el panel también se apunta en una noche agotada: el box no es
        // una entrada, y que no queden entradas no significa que no quede mesa.
        boolean abierta = noche.getEstado() == EstadoDeEvento.PUBLICADO
                || (canal != CanalDeInteraccion.WEB && noche.getEstado() == EstadoDeEvento.AGOTADO);
        if (!abierta || noche.getEmpiezaA().isBefore(Instant.now())) {
            throw new ReservaRechazada("Esa fecha no admite reservas.");
        }

        MesaVip mesa = mesas.findById(s.mesaId())
                .filter(MesaVip::isActiva)
                .orElseThrow(() -> new ReservaRechazada("Ese reservado ya no está disponible."));

        if (s.personas() > mesa.getPlazas()) {
            throw new ReservaRechazada(
                    "%s es para un máximo de %d personas.".formatted(mesa.getNombre(), mesa.getPlazas()));
        }

        String email = s.email().trim().toLowerCase(Locale.ROOT);
        Cliente cliente = clientes.findByEmailIgnoreCase(email)
                .map(existente -> {
                    // Aquí SÍ se pisan los datos, al revés que en el boletín: el
                    // formulario los pide enteros y un teléfono viejo es peor
                    // que ninguno cuando hay que llamar.
                    existente.actualizarContacto(s.nombre().trim(), s.apellido(), s.telefono().trim());
                    return existente;
                })
                .orElseGet(() -> clientes.save(Cliente.nuevoConContacto(
                        s.nombre().trim(), s.apellido(), email, s.telefono().trim())));

        reservas.borrarCancelada(noche.getId(), mesa.getId(), EstadoDeReserva.CANCELADO);
        Reserva reserva = Reserva.solicitar(noche, mesa, cliente, s.personas(), s.notas());

        try {
            // Con el id ya asignado, save() hace merge y devuelve OTRA instancia:
            // la que queda gestionada es la devuelta, no la que se le pasa.
            reserva = reservas.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException e) {
            /*
             * Entre comprobar que la mesa estaba libre e insertar pasa un
             * instante, y dos personas pidiendo a la vez pasan las dos la
             * comprobación. Quien lo impide de verdad es el unique (eventoId,
             * boxId) de la base.
             *
             * El flush es lo que hace que la violación salte AQUÍ y no al
             * cerrar la transacción, que es donde ya no se puede convertir en
             * un mensaje entendible.
             */
            throw new ReservaRechazada("Ese reservado acaban de pedirlo para esa noche.");
        }

        interacciones.save(Interaccion.registrar(
                cliente,
                TipoDeInteraccion.RESERVA,
                canal,
                "Solicitó %s para %d personas en \"%s\"."
                        .formatted(mesa.getNombre(), s.personas(), noche.getTitulo())));
        return reserva;
    }

    /**
     * Cambia el estado, el depósito y las notas de una reserva.
     *
     * <p>Lo que no es un paso del ciclo se rechaza con el motivo: sentar a
     * quien canceló, o confirmar a quien ya está sentado, es casi siempre un
     * clic en la fila equivocada.
     */
    @Transactional
    public Reserva cambiar(Long id, EstadoDeReserva estado, int depositoCentimos, String notas) {
        Reserva r = reservas.buscarCompleta(id)
                .orElseThrow(() -> new ReservaRechazada("Esa reserva no existe."));

        if (depositoCentimos < 0) {
            throw new ReservaRechazada("El depósito no puede ser negativo.");
        }
        if (estado != r.getEstado() && !PASOS.getOrDefault(r.getEstado(), Set.of()).contains(estado)) {
            throw new ReservaRechazada("Una reserva «%s» no puede pasar a «%s»."
                    .formatted(nombre(r.getEstado()), nombre(estado)));
        }

        r.pasarA(estado);
        r.acordar(depositoCentimos, notas);
        return r;
    }

    private static String nombre(EstadoDeReserva e) {
        return switch (e) {
            case SOLICITADA -> "solicitada";
            case CONFIRMADA -> "confirmada";
            case SENTADA -> "sentados";
            case NO_VINO -> "no vino";
            case CANCELADO -> "cancelada";
        };
    }
}
