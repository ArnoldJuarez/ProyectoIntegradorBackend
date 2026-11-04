package pe.ssiomama.backend.auth;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.personal.Puesto;
import pe.ssiomama.backend.personal.PuestoRepository;

/**
 * El equipo: dar de alta a alguien, cambiarle el rol, darle código de promotor,
 * desactivarlo y ponerle contraseña.
 *
 * <h2>Las reglas que evitan quedarse fuera</h2>
 *
 * <ul>
 *   <li>Nadie se desactiva ni se quita el rol de dirección a sí mismo: un clic
 *       equivocado dejaría el local sin nadie que pueda deshacerlo.
 *   <li>El correo no se repite: es con lo que se inicia sesión.
 *   <li>El código de promotor no se repite: es lo que atribuye cada venta.
 * </ul>
 *
 * <p>Desactivar a alguien le cierra la sesión en la siguiente petición (lo
 * comprueba {@link FiltroDeSesion}), no al caducar la cookie.
 */
@Service
public class GestionDePersonal {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final int CLAVE_MINIMA = 8;

    private final UsuarioRepository usuarios;
    private final PuestoRepository puestos;
    private final PasswordEncoder codificador;

    public GestionDePersonal(UsuarioRepository usuarios, PuestoRepository puestos, PasswordEncoder codificador) {
        this.usuarios = usuarios;
        this.puestos = puestos;
        this.codificador = codificador;
    }

    public record DatosDePersona(
            String nombre,
            String email,
            Rol rol,
            boolean activo,
            String puestoId,
            int sueldoPorHoraCentimos,
            String codigoDePromotor,
            Integer comisionPorcentaje,
            /** Pago por cada invitado de sus listas que entra. Nulo: se deja como estaba. */
            Integer bonoPorInvitadoCentimos) {

        /** Sin bono: el que ya tuviera se conserva. */
        public DatosDePersona(String nombre, String email, Rol rol, boolean activo, String puestoId,
                int sueldoPorHoraCentimos, String codigoDePromotor, Integer comisionPorcentaje) {
            this(nombre, email, rol, activo, puestoId, sueldoPorHoraCentimos, codigoDePromotor, comisionPorcentaje, null);
        }
    }

    /**
     * @param clave la contraseña inicial, o nula para alguien que no entra al sistema
     */
    @Transactional
    public Usuario alta(DatosDePersona d, String clave) {
        Usuario u = Usuario.alta(d.nombre(), d.rol(), LocalDate.now(LIMA));
        aplicar(u, d);
        if (clave != null && !clave.isBlank()) ponerClave(u, clave);
        return usuarios.save(u);
    }

    @Transactional
    public Usuario editar(String id, DatosDePersona d, Usuario quien) {
        Usuario u = persona(id);
        if (u.getId().equals(quien.getId())) {
            if (!d.activo()) throw new PersonalRechazado("No puedes desactivarte a ti mismo.");
            if (d.rol() != Rol.DIRECCION) {
                throw new PersonalRechazado("No puedes quitarte el rol de dirección: pídeselo a otra persona de dirección.");
            }
        }
        aplicar(u, d);
        return u;
    }

    @Transactional
    public void cambiarClave(String id, String clave) {
        Usuario u = persona(id);
        if (u.getEmail() == null) {
            throw new PersonalRechazado("Ponle antes un correo: sin correo no hay con qué iniciar sesión.");
        }
        ponerClave(u, clave);
    }

    private void aplicar(Usuario u, DatosDePersona d) {
        String email = d.email() == null || d.email().isBlank() ? null : d.email().trim().toLowerCase(Locale.ROOT);
        if (email != null) {
            usuarios.findByEmailIgnoreCase(email)
                    .filter(otro -> !otro.getId().equals(u.getId()))
                    .ifPresent(otro -> {
                        throw new PersonalRechazado("Ese correo ya lo usa " + otro.getNombre() + ".");
                    });
        }
        String codigo = d.codigoDePromotor() == null || d.codigoDePromotor().isBlank()
                ? null
                : d.codigoDePromotor().trim().toUpperCase(Locale.ROOT);
        if (codigo != null) {
            if (!codigo.matches("[A-Z0-9]{3,20}")) {
                throw new PersonalRechazado("El código de promotor va en mayúsculas y números, de 3 a 20 caracteres.");
            }
            usuarios.findAll().stream()
                    .filter(otro -> codigo.equals(otro.getCodigoDePromotor()) && !otro.getId().equals(u.getId()))
                    .findFirst()
                    .ifPresent(otro -> {
                        throw new PersonalRechazado("El código " + codigo + " ya es de " + otro.getNombre() + ".");
                    });
            if (d.comisionPorcentaje() == null || d.comisionPorcentaje() < 0 || d.comisionPorcentaje() > 50) {
                throw new PersonalRechazado("Un promotor necesita su comisión, entre 0 % y 50 %.");
            }
        }
        if (d.sueldoPorHoraCentimos() < 0) {
            throw new PersonalRechazado("El sueldo por hora no puede ser negativo.");
        }
        Puesto puesto = d.puestoId() == null || d.puestoId().isBlank()
                ? null
                : puestos.findById(d.puestoId()).orElseThrow(() -> new PersonalRechazado("Ese puesto no existe."));

        if (d.bonoPorInvitadoCentimos() != null && (d.bonoPorInvitadoCentimos() < 0 || d.bonoPorInvitadoCentimos() > 100_000)) {
            throw new PersonalRechazado("El pago por invitado va de S/ 0 a S/ 1000.");
        }

        u.actualizar(d.nombre(), email, d.rol(), d.activo(), puesto, d.sueldoPorHoraCentimos(), codigo,
                d.comisionPorcentaje());
        u.cambiarBonoPorInvitado(d.bonoPorInvitadoCentimos() == null
                ? u.getBonoPorInvitadoCentimos()
                : d.bonoPorInvitadoCentimos());
    }

    private void ponerClave(Usuario u, String clave) {
        if (clave.length() < CLAVE_MINIMA) {
            throw new PersonalRechazado("La contraseña necesita al menos " + CLAVE_MINIMA + " caracteres.");
        }
        u.cambiarHashDeContrasena(codificador.encode(clave));
    }

    private Usuario persona(String id) {
        return usuarios.findById(id).orElseThrow(() -> new PersonalRechazado("Esa persona no existe."));
    }
}
