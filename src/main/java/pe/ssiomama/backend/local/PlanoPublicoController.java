package pe.ssiomama.backend.local;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ssiomama.backend.local.LocalController.SalaDelPlano;

/**
 * El plano del local para la web pública: rutas de evacuación, salidas, zonas
 * seguras y punto de reunión.
 *
 * <p>Esto lo tiene que poder consultar cualquiera, sin cuenta y antes de venir:
 * es información de seguridad. Por eso cuelga de {@code /api/publico}, que es
 * de lectura libre. No lleva nada interno —ni ventas, ni reservas, ni
 * personal—: medidas, zonas con su aforo, puertas y elementos de seguridad, lo
 * mismo que estaría colgado en un cartel junto a la puerta.
 */
@RestController
public class PlanoPublicoController {

    private final LocalController local;

    public PlanoPublicoController(LocalController local) {
        this.local = local;
    }

    @GetMapping("/api/publico/local")
    public List<SalaDelPlano> plano() {
        return local.local();
    }
}
