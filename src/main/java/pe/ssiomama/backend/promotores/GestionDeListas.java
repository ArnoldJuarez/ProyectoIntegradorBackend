package pe.ssiomama.backend.promotores;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.eventos.EstadoDeEvento;
import pe.ssiomama.backend.eventos.Evento;
import pe.ssiomama.backend.eventos.EventoRepository;

/**
 * Abrir listas de invitados, apuntar gente y marcar quién entró.
 *
 * <p>Antes las listas solo se leían: se abrían «desde fuera del panel», y fuera
 * del panel no había nada. Ahora quien lleva a los promotores las abre aquí, y
 * la noche misma se marca la entrada de cada fila.
 *
 * <p>El tope cuenta <b>personas</b>, no filas: «Ana +3» son cuatro. Es lo que
 * se controla en la puerta y lo que se le paga al promotor.
 */
@Service
public class GestionDeListas {

    /** Hasta cuándo se toca una lista: doce horas después de empezar la noche, para marcar entradas de madrugada. */
    private static final Duration MARGEN = Duration.ofHours(12);

    private final ListaDeInvitadosRepository listas;
    private final InvitadoRepository invitados;
    private final EventoRepository eventos;

    public GestionDeListas(
            ListaDeInvitadosRepository listas, InvitadoRepository invitados, EventoRepository eventos) {
        this.listas = listas;
        this.invitados = invitados;
        this.eventos = eventos;
    }

    @Transactional
    public ListaDeInvitados abrir(String slug, Long promotorId, String nombre, int maxInvitados) {
        Evento noche = eventos.findBySlug(slug).orElseThrow(() -> new ListaRechazada("Esa noche no existe."));
        if (noche.getEstado() == EstadoDeEvento.CANCELADO || noche.getEstado() == EstadoDeEvento.CERRADO
                || noche.getEmpiezaA().plus(MARGEN).isBefore(Instant.now())) {
            throw new ListaRechazada("Esa noche ya no admite listas.");
        }
        Usuario promotor = listas.buscarPromotores().stream()
                .filter(u -> u.getId().equals(promotorId))
                .findFirst()
                .orElseThrow(() -> new ListaRechazada("Esa persona no tiene código de promotor."));
        boolean repetida = listas.buscarDeLaNoche(slug).stream()
                .anyMatch(l -> l.getPromotor().getId().equals(promotorId) && l.getNombre().equalsIgnoreCase(nombre.trim()));
        if (repetida) {
            throw new ListaRechazada(promotor.getNombre() + " ya tiene una lista «" + nombre.trim() + "» esa noche.");
        }
        return listas.save(ListaDeInvitados.abrir(noche, promotor, nombre, maxInvitados));
    }

    @Transactional
    public ListaDeInvitados cambiar(Long id, String nombre, int maxInvitados) {
        ListaDeInvitados l = lista(id);
        if (maxInvitados < l.apuntados()) {
            throw new ListaRechazada("Ya hay " + l.apuntados() + " personas apuntadas: el tope no puede quedar por debajo.");
        }
        l.cambiar(nombre, maxInvitados);
        return l;
    }

    /** Cierra una lista sin estrenar. Si ya entró alguien, es historia del promotor y se queda. */
    @Transactional
    public void borrar(Long id) {
        ListaDeInvitados l = lista(id);
        if (l.entraron() > 0) {
            throw new ListaRechazada("Ya entró gente con esta lista: se queda para el cálculo del promotor.");
        }
        invitados.deleteAll(l.getInvitados());
        listas.delete(l);
    }

    @Transactional
    public Invitado apuntar(Long listaId, String nombreCompleto, int personas) {
        ListaDeInvitados l = lista(listaId);
        if (l.getEvento().getEmpiezaA().plus(MARGEN).isBefore(Instant.now())) {
            throw new ListaRechazada("La noche ya pasó: no se apunta a nadie más.");
        }
        int quedan = l.getMaxInvitados() - l.apuntados();
        if (personas > quedan) {
            throw new ListaRechazada(quedan <= 0
                    ? "La lista está llena (" + l.getMaxInvitados() + " personas)."
                    : "Solo quedan " + quedan + " plazas en la lista.");
        }
        // save() hace merge (el id va asignado) y devuelve otra instancia: es esa,
        // la gestionada, la que entra en la lista para que las cuentas la vean.
        Invitado apuntado = invitados.save(Invitado.apuntar(l, nombreCompleto, personas));
        l.getInvitados().add(apuntado);
        return apuntado;
    }

    @Transactional
    public void quitar(Long invitadoId) {
        Invitado i = invitado(invitadoId);
        if (i.isEntro()) {
            throw new ListaRechazada(i.getNombreCompleto() + " ya entró: no se puede quitar de la lista.");
        }
        i.getLista().getInvitados().remove(i);
        invitados.delete(i);
    }

    @Transactional
    public Invitado marcarEntrada(Long invitadoId, boolean entro) {
        Invitado i = invitado(invitadoId);
        i.marcarEntrada(entro, Instant.now());
        return i;
    }

    private ListaDeInvitados lista(Long id) {
        return listas.buscarConInvitados(id).orElseThrow(() -> new ListaRechazada("Esa lista no existe."));
    }

    private Invitado invitado(Long id) {
        return invitados.buscarConLista(id).orElseThrow(() -> new ListaRechazada("Esa fila no existe."));
    }
}
