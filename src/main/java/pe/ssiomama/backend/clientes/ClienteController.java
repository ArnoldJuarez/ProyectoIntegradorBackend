package pe.ssiomama.backend.clientes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Los clientes, para el panel.
 *
 * <p>Equivale a {@code app/panel/clientes/} del sistema anterior. Es la parte
 * CRM: quién viene, cuánto se ha gastado y qué ha pasado con cada uno.
 *
 * <p>Todo lo de aquí es interno y está detrás de rol de panel. Son datos
 * personales de gente real: nombres, correos y teléfonos.
 */
@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    /** Tope duro del listado. Sin él, pedir la lista entera crece con la base. */
    private static final int MAXIMO_POR_PAGINA = 100;

    /** Cuántas líneas de historial trae la ficha. */
    private static final int HISTORIAL_EN_FICHA = 50;

    private final ClienteRepository clientes;
    private final InteraccionRepository interacciones;

    public ClienteController(ClienteRepository clientes, InteraccionRepository interacciones) {
        this.clientes = clientes;
        this.interacciones = interacciones;
    }

    public record ClienteEnLista(
            String id,
            String nombre,
            String email,
            String telefono,
            int visitas,
            int gastadoCentimos,
            Instant ultimaVisita,
            boolean aceptaMarketing) {

        static ClienteEnLista de(Cliente c) {
            return new ClienteEnLista(
                    c.getId(),
                    c.getNombreCompleto(),
                    c.getEmail(),
                    c.getTelefono(),
                    c.getVisitas(),
                    c.getGastadoCentimos(),
                    c.getUltimaVisita(),
                    c.isAceptaMarketing());
        }
    }

    public record LineaDeHistorial(
            String id, TipoDeInteraccion tipo, CanalDeInteraccion canal, String resumen, Instant cuando) {

        static LineaDeHistorial de(Interaccion i) {
            return new LineaDeHistorial(
                    i.getId(), i.getTipo(), i.getCanal(), i.getResumen(), i.getOcurrioEn());
        }
    }

    public record FichaDeCliente(
            String id,
            String nombre,
            String email,
            String telefono,
            LocalDate fechaDeNacimiento,
            String distrito,
            int visitas,
            int gastadoCentimos,
            Instant ultimaVisita,
            boolean aceptaMarketing,
            Instant consintioEn,
            Instant creadoEn,
            /** Media por visita, en céntimos. 0 si todavía no ha venido. */
            int gastoMedioCentimos,
            List<LineaDeHistorial> historial) {}

    /**
     * @param buscar nombre, apellido o correo. Vacío = los más recientes.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<ClienteEnLista> listar(
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "50") int limite) {

        // El patrón se arma aquí y no en la consulta: la repository recibe algo
        // ya listo para comparar, y el "null = sin filtro" queda en un solo sitio.
        String patron = (buscar == null || buscar.isBlank())
                ? null
                : "%" + buscar.trim().toLowerCase(Locale.ROOT) + "%";

        int cuantos = Math.clamp(limite, 1, MAXIMO_POR_PAGINA);

        return clientes.buscar(patron, PageRequest.of(0, cuantos)).stream()
                .map(ClienteEnLista::de)
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<FichaDeCliente> ficha(@PathVariable String id) {
        Cliente c = clientes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe ese cliente."));

        List<LineaDeHistorial> historial =
                interacciones
                        .findByClienteIdOrderByOcurrioEnDesc(
                                id, PageRequest.of(0, HISTORIAL_EN_FICHA))
                        .stream()
                        .map(LineaDeHistorial::de)
                        .toList();

        /*
         * La media por visita se calcula aquí y no se guarda: es un dato
         * derivado de dos contadores que ya existen, y guardarlo daría un tercer
         * numero que puede quedar desfasado respecto a los otros dos.
         */
        int medio = c.getVisitas() > 0 ? c.getGastadoCentimos() / c.getVisitas() : 0;

        return ResponseEntity.ok(new FichaDeCliente(
                c.getId(),
                c.getNombreCompleto(),
                c.getEmail(),
                c.getTelefono(),
                c.getFechaDeNacimiento(),
                c.getDistrito(),
                c.getVisitas(),
                c.getGastadoCentimos(),
                c.getUltimaVisita(),
                c.isAceptaMarketing(),
                c.getConsintioEn(),
                c.getCreadoEn(),
                medio,
                historial));
    }
}
