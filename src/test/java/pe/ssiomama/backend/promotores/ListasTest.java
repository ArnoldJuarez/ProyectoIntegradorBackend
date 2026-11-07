package pe.ssiomama.backend.promotores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Abrir una lista, apuntar gente con su tope y marcar la entrada. */
@SpringBootTest
@Transactional
class ListasTest {

    @Autowired
    private GestionDeListas gestion;

    @Autowired
    private JdbcTemplate jdbc;

    private String nocheFutura() {
        return jdbc.queryForObject("""
                select slug from "Evento" where estado = 'PUBLICADO' and "empiezaEn" > now() at time zone 'utc'
                order by "empiezaEn" limit 1
                """, String.class);
    }

    private Long promotor() {
        return jdbc.queryForObject("select id from \"Usuario\" where \"codigoPromotor\" is not null order by nombre limit 1",
                Long.class);
    }

    @Test
    @DisplayName("el tope cuenta personas: «Ana +3» son cuatro")
    void topeEnPersonas() {
        ListaDeInvitados lista = gestion.abrir(nocheFutura(), promotor(), "Prueba Cumpleaños Ana", 5);
        gestion.apuntar(lista.getId(), "Ana Quispe", 4);

        assertThat(lista.apuntados()).isEqualTo(4);
        assertThatThrownBy(() -> gestion.apuntar(lista.getId(), "Grupo de Luis", 2))
                .isInstanceOf(ListaRechazada.class).hasMessageContaining("Solo quedan 1");
    }

    @Test
    @DisplayName("quien ya entró no se quita, y la lista con entradas no se cierra")
    void loQueEntroSeQueda() {
        ListaDeInvitados lista = gestion.abrir(nocheFutura(), promotor(), "Prueba Grupo de la uni", 20);
        Invitado ana = gestion.apuntar(lista.getId(), "Ana Quispe", 2);
        gestion.marcarEntrada(ana.getId(), true);

        assertThat(lista.entraron()).isEqualTo(2);
        assertThatThrownBy(() -> gestion.quitar(ana.getId()))
                .isInstanceOf(ListaRechazada.class).hasMessageContaining("ya entró");
        assertThatThrownBy(() -> gestion.borrar(lista.getId()))
                .isInstanceOf(ListaRechazada.class).hasMessageContaining("Ya entró gente");

        // Desmarcar existe para el dedo que se equivocó de fila.
        gestion.marcarEntrada(ana.getId(), false);
        assertThat(ana.getEntroEn()).isNull();
    }

    @Test
    @DisplayName("el mismo promotor no abre dos listas con el mismo nombre la misma noche")
    void nombreRepetido() {
        String noche = nocheFutura();
        gestion.abrir(noche, promotor(), "Prueba VIP", 10);

        assertThatThrownBy(() -> gestion.abrir(noche, promotor(), "prueba vip", 10))
                .isInstanceOf(ListaRechazada.class).hasMessageContaining("ya tiene una lista");
    }

    @Test
    @DisplayName("el tope no baja de lo ya apuntado")
    void topeNoBajaDeLoApuntado() {
        ListaDeInvitados lista = gestion.abrir(nocheFutura(), promotor(), "Prueba Tope", 10);
        gestion.apuntar(lista.getId(), "Ana Quispe", 6);

        assertThatThrownBy(() -> gestion.cambiar(lista.getId(), "Prueba Tope", 5))
                .isInstanceOf(ListaRechazada.class).hasMessageContaining("6 personas");
    }
}
