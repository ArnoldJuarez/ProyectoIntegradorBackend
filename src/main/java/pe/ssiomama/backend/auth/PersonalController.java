package pe.ssiomama.backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ssiomama.backend.auth.GestionDePersonal.DatosDePersona;
import pe.ssiomama.backend.personal.PuestoRepository;

/**
 * El personal desde el panel. Solo dirección (ver {@code SecurityConfig}).
 *
 * <p>La contraseña nunca sale: ni en la lista ni en la ficha. Se sabe si alguien
 * puede entrar ({@code conAcceso}), no con qué.
 */
@RestController
@RequestMapping("/api/personal")
public class PersonalController {

    private final UsuarioRepository usuarios;
    private final PuestoRepository puestos;
    private final GestionDePersonal gestion;

    public PersonalController(UsuarioRepository usuarios, PuestoRepository puestos, GestionDePersonal gestion) {
        this.usuarios = usuarios;
        this.puestos = puestos;
        this.gestion = gestion;
    }

    public record FormularioDePersona(
            @NotBlank(message = "Falta el nombre") @Size(min = 2, max = 80, message = "Entre 2 y 80 caracteres")
            String nombre,
            @Email(message = "El correo no es válido") @Size(max = 160) String email,
            @NotNull(message = "Elige el rol") Rol rol,
            boolean activo,
            String puestoId,
            @Min(value = 0, message = "El sueldo no puede ser negativo") @Max(value = 1_000_000) int sueldoPorHoraCentimos,
            @Size(max = 20) String codigoDePromotor,
            Integer comisionPorcentaje,
            /** Pago por invitado que entra, en céntimos. Nulo: no se toca. */
            Integer bonoPorInvitadoCentimos,
            /** Solo al dar de alta. Para cambiarla después está su propio endpoint. */
            @Size(max = 128) String clave) {}

    public record NuevaClave(
            @NotBlank(message = "Escribe la contraseña nueva")
            @Size(min = 8, max = 128, message = "La contraseña tiene entre 8 y 128 caracteres")
            String clave) {}

    public record PersonaEnLista(
            String id,
            String nombre,
            String email,
            Rol rol,
            boolean activo,
            boolean conAcceso,
            String puestoId,
            String puesto,
            LocalDate fechaDeAlta,
            int sueldoPorHoraCentimos,
            String codigoDePromotor,
            Integer comisionPorcentaje,
            int bonoPorInvitadoCentimos) {

        static PersonaEnLista de(Usuario u) {
            return new PersonaEnLista(u.getId(), u.getNombre(), u.getEmail(), u.getRol(), u.isActivo(),
                    u.puedeEntrar(), u.getPuesto() == null ? null : u.getPuesto().getId(),
                    u.getPuesto() == null ? null : u.getPuesto().getNombre(), u.getFechaDeAlta(),
                    u.getSueldoPorHoraCentimos(), u.getCodigoDePromotor(), u.getComisionPorcentaje(),
                    u.getBonoPorInvitadoCentimos());
        }
    }

    public record PuestoParaElegir(String id, String nombre) {}

    public record OpcionesDePersonal(List<Rol> roles, List<PuestoParaElegir> puestos) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<PersonaEnLista> equipo() {
        return usuarios.findAll().stream()
                .sorted(Comparator.comparing((Usuario u) -> !u.isActivo())
                        .thenComparing(Usuario::getRol)
                        .thenComparing(Usuario::getNombre))
                .map(PersonaEnLista::de)
                .toList();
    }

    @GetMapping("/opciones")
    @Transactional(readOnly = true)
    public OpcionesDePersonal opciones() {
        return new OpcionesDePersonal(Arrays.asList(Rol.values()),
                puestos.findAllByOrderByNombreAsc().stream()
                        .filter(p -> p.isActivo())
                        .map(p -> new PuestoParaElegir(p.getId(), p.getNombre()))
                        .toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<PersonaEnLista> alta(@Valid @RequestBody FormularioDePersona f) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PersonaEnLista.de(gestion.alta(aDatos(f, true), f.clave())));
    }

    @PutMapping("/{id}")
    @Transactional
    public PersonaEnLista editar(
            @PathVariable String id, @Valid @RequestBody FormularioDePersona f, @AuthenticationPrincipal Usuario quien) {
        return PersonaEnLista.de(gestion.editar(id, aDatos(f, f.activo()), quien));
    }

    @PutMapping("/{id}/clave")
    @Transactional
    public ResponseEntity<Void> cambiarClave(@PathVariable String id, @Valid @RequestBody NuevaClave n) {
        gestion.cambiarClave(id, n.clave());
        return ResponseEntity.noContent().build();
    }

    private static DatosDePersona aDatos(FormularioDePersona f, boolean activo) {
        return new DatosDePersona(f.nombre(), f.email(), f.rol(), activo, f.puestoId(), f.sueldoPorHoraCentimos(),
                f.codigoDePromotor(), f.comisionPorcentaje(), f.bonoPorInvitadoCentimos());
    }
}
