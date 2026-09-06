package pe.ssiomama.backend.publico;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ssiomama.backend.clientes.CanalDeInteraccion;
import pe.ssiomama.backend.clientes.Cliente;
import pe.ssiomama.backend.clientes.ClienteRepository;
import pe.ssiomama.backend.clientes.Interaccion;
import pe.ssiomama.backend.clientes.InteraccionRepository;
import pe.ssiomama.backend.clientes.TipoDeInteraccion;

/**
 * Alta en el aviso de cartel.
 *
 * <h2>No hay tabla de «suscriptores»</h2>
 *
 * <p>Se guarda como {@code Customer} con {@code marketingConsent}. Una lista de
 * correos aparte acabaría siendo un CRM en la sombra —la misma persona en dos
 * sitios, sin saber que ya te compró tres entradas— y los segmentos del panel ya
 * saben filtrar por consentimiento.
 *
 * <p>Es la decisión del sistema anterior y se respeta: cambiarla ahora partiría
 * en dos los datos que ya hay.
 */
@RestController
@RequestMapping("/api/publico")
public class BoletinController {

    private final ClienteRepository clientes;
    private final InteraccionRepository interacciones;

    public BoletinController(ClienteRepository clientes, InteraccionRepository interacciones) {
        this.clientes = clientes;
        this.interacciones = interacciones;
    }

    /**
     * Lo que pide el formulario. Dos campos y no más: cada campo de más es gente
     * que no lo rellena.
     *
     * <p>El límite de longitud no es cosmético. Este endpoint lo puede llamar
     * cualquiera sin cuenta, y sin tope alguien puede mandar un nombre de un
     * megabyte y guardarlo tal cual.
     */
    public record Alta(
            @NotBlank(message = "Falta el nombre")
            @Size(max = 80, message = "Ese nombre es demasiado largo")
            String nombre,
            @NotBlank(message = "Falta el correo")
            @Email(message = "Ese correo no parece válido")
            @Size(max = 160, message = "Ese correo es demasiado largo")
            String email) {}

    public record Respuesta(String mensaje) {}

    @PostMapping("/boletin")
    @Transactional
    public ResponseEntity<Respuesta> apuntarse(@Valid @RequestBody Alta alta) {
        // En minúsculas para que la misma persona no entre dos veces por
        // escribir su correo con mayúscula distinta.
        String email = alta.email().trim().toLowerCase(Locale.ROOT);
        String nombre = alta.nombre().trim();

        Cliente cliente = clientes
                .findByEmailIgnoreCase(email)
                .map(existente -> {
                    // A quien ya está NO se le pisa el nombre con lo que
                    // escriba hoy en un formulario de dos campos: puede tener
                    // nombre y apellido correctos de una compra anterior.
                    existente.aceptarMarketing();
                    return existente;
                })
                .orElseGet(() -> clientes.save(Cliente.nuevoConConsentimiento(nombre, email)));

        interacciones.save(Interaccion.registrar(
                cliente,
                TipoDeInteraccion.NOTA,
                CanalDeInteraccion.WEB,
                "Pidió aviso cuando salga el próximo cartel."));

        /*
         * La misma respuesta esté o no ya apuntado. Decir "ya estabas" filtra
         * si un correo concreto está en la base, y eso lo puede sondear
         * cualquiera desde fuera.
         */
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new Respuesta("Listo. Te avisamos en cuanto haya cartel nuevo."));
    }
}
