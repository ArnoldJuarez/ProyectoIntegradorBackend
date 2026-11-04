package pe.ssiomama.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.GestionDePersonal.DatosDePersona;

/** Dar de alta personal sin dejar a nadie fuera por error. */
@SpringBootTest
@Transactional
class GestionDePersonalTest {

    @Autowired
    private GestionDePersonal gestion;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder codificador;

    private DatosDePersona portero(String email, String codigo, Integer comision) {
        return new DatosDePersona("Prueba Portero", email, Rol.PUERTA, true, null, 1200, codigo, comision);
    }

    @Test
    @DisplayName("alguien nuevo con correo y clave puede entrar; la clave se guarda con scrypt")
    void altaConAcceso() {
        Usuario nuevo = gestion.alta(portero("Prueba.Portero@Ssiomama.test", null, null), "puerta-segura-1");

        assertThat(nuevo.getEmail()).isEqualTo("prueba.portero@ssiomama.test");
        assertThat(nuevo.puedeEntrar()).isTrue();
        assertThat(nuevo.getHashDeContrasena()).startsWith("scrypt:").doesNotContain("puerta-segura-1");
        assertThat(codificador.matches("puerta-segura-1", nuevo.getHashDeContrasena())).isTrue();
    }

    @Test
    @DisplayName("sin correo se registra, pero no entra al sistema")
    void altaSinAcceso() {
        Usuario nuevo = gestion.alta(portero(null, null, null), null);
        assertThat(nuevo.puedeEntrar()).isFalse();
        assertThatThrownBy(() -> gestion.cambiarClave(nuevo.getId(), "una-clave-larga"))
                .isInstanceOf(PersonalRechazado.class).hasMessageContaining("correo");
    }

    @Test
    @DisplayName("el correo y el código de promotor no se repiten")
    void nadaRepetido() {
        assertThatThrownBy(() -> gestion.alta(portero("ADMIN@ssiomama.test", null, null), null))
                .isInstanceOf(PersonalRechazado.class).hasMessageContaining("ya lo usa");

        String codigo = usuarios.findAll().stream()
                .map(Usuario::getCodigoDePromotor).filter(c -> c != null).findFirst().orElseThrow();
        assertThatThrownBy(() -> gestion.alta(portero(null, codigo.toLowerCase(), 10), null))
                .isInstanceOf(PersonalRechazado.class).hasMessageContaining("ya es de");
    }

    @Test
    @DisplayName("un promotor necesita su comisión")
    void promotorConComision() {
        assertThatThrownBy(() -> gestion.alta(portero(null, "PRUEBA1", null), null))
                .isInstanceOf(PersonalRechazado.class).hasMessageContaining("comisión");

        Usuario promotor = gestion.alta(portero(null, "prueba1", 10), null);
        assertThat(promotor.getCodigoDePromotor()).isEqualTo("PRUEBA1");
        assertThat(promotor.getComisionPorcentaje()).isEqualTo(10);
    }

    @Test
    @DisplayName("nadie se desactiva ni se quita dirección a sí mismo")
    void nadieSeDejaFuera() {
        Usuario yo = usuarios.findByEmailIgnoreCase("admin@ssiomama.test").orElseThrow();
        var comoSoy = new DatosDePersona(yo.getNombre(), yo.getEmail(), Rol.DIRECCION, true, null, 0, null, null);

        assertThatThrownBy(() -> gestion.editar(yo.getId(),
                new DatosDePersona(yo.getNombre(), yo.getEmail(), Rol.DIRECCION, false, null, 0, null, null), yo))
                .isInstanceOf(PersonalRechazado.class).hasMessageContaining("desactivarte");
        assertThatThrownBy(() -> gestion.editar(yo.getId(),
                new DatosDePersona(yo.getNombre(), yo.getEmail(), Rol.GERENCIA, true, null, 0, null, null), yo))
                .isInstanceOf(PersonalRechazado.class).hasMessageContaining("dirección");

        assertThat(gestion.editar(yo.getId(), comoSoy, yo).getRol()).isEqualTo(Rol.DIRECCION);
    }
}
