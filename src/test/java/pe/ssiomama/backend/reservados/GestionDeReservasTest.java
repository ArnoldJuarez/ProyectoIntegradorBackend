package pe.ssiomama.backend.reservados;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.clientes.CanalDeInteraccion;

/** El ciclo de una reserva de box, contra la base real. */
@SpringBootTest
@Transactional
class GestionDeReservasTest {

    @Autowired
    private GestionDeReservas gestion;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    /** Una noche publicada y futura y un box que nadie ha pedido para ella. */
    private record Hueco(String slug, String mesaId, int plazas) {}

    private Hueco hueco() {
        return jdbc.queryForObject("""
                select e.slug, b.id, b.plazas from "Evento" e cross join "Box" b
                where e.estado = 'PUBLICADO' and e."empiezaEn" > now() at time zone 'utc' and b.activo
                  and not exists (select 1 from "ReservaBox" r where r."eventoId" = e.id and r."boxId" = b.id)
                order by e."empiezaEn", b.plazas limit 1
                """, (rs, n) -> new Hueco(rs.getString(1), rs.getString(2), rs.getInt(3)));
    }

    private Reserva pedir(Hueco h, String email) {
        return gestion.solicitar(new GestionDeReservas.Solicitud(
                h.slug(), h.mesaId(), Math.min(4, h.plazas()), null, "Prueba", "Reservas", email, "987654321"),
                CanalDeInteraccion.TELEFONO);
    }

    @Test
    @DisplayName("solicitada → confirmada (con depósito) → sentados")
    void cicloNormal() {
        Reserva r = pedir(hueco(), "prueba.reservas.1@ejemplo.pe");
        assertThat(r.getEstado()).isEqualTo(EstadoDeReserva.SOLICITADA);

        gestion.cambiar(r.getId(), EstadoDeReserva.CONFIRMADA, 15000, "Paga por Yape el jueves");
        assertThat(r.getDepositoCentimos()).isEqualTo(15000);
        assertThat(r.getNotas()).isEqualTo("Paga por Yape el jueves");

        gestion.cambiar(r.getId(), EstadoDeReserva.SENTADA, 15000, null);
        assertThat(r.getEstado()).isEqualTo(EstadoDeReserva.SENTADA);

        // Corregir el dedo que se equivocó: sentados ↔ no vino.
        gestion.cambiar(r.getId(), EstadoDeReserva.NO_VINO, 15000, null);
        assertThat(r.getEstado()).isEqualTo(EstadoDeReserva.NO_VINO);
    }

    @Test
    @DisplayName("no se sienta a quien solo había solicitado ni a quien canceló")
    void pasosQueNoExisten() {
        Reserva r = pedir(hueco(), "prueba.reservas.2@ejemplo.pe");

        assertThatThrownBy(() -> gestion.cambiar(r.getId(), EstadoDeReserva.SENTADA, 0, null))
                .isInstanceOf(ReservaRechazada.class).hasMessageContaining("«solicitada»");

        gestion.cambiar(r.getId(), EstadoDeReserva.CANCELADO, 0, null);
        assertThatThrownBy(() -> gestion.cambiar(r.getId(), EstadoDeReserva.SENTADA, 0, null))
                .isInstanceOf(ReservaRechazada.class).hasMessageContaining("«cancelada»");
    }

    @Test
    @DisplayName("un box cancelado se puede volver a pedir esa misma noche")
    void boxCanceladoQuedaLibre() {
        Hueco h = hueco();
        Reserva primera = pedir(h, "prueba.reservas.3@ejemplo.pe");
        gestion.cambiar(primera.getId(), EstadoDeReserva.CANCELADO, 0, null);
        em.flush();

        // Antes de arreglarlo, esto chocaba con el unique (eventoId, boxId) de la base.
        Reserva segunda = pedir(h, "prueba.reservas.4@ejemplo.pe");
        em.flush();

        assertThat(segunda.getEstado()).isEqualTo(EstadoDeReserva.SOLICITADA);
        assertThat(jdbc.queryForObject("""
                select count(*) from "ReservaBox" r join "Evento" e on e.id = r."eventoId"
                where e.slug = ? and r."boxId" = ?
                """, Long.class, h.slug(), h.mesaId())).isEqualTo(1);
    }

    @Test
    @DisplayName("un box que sigue pedido no se puede pedir otra vez")
    void boxOcupado() {
        Hueco h = hueco();
        pedir(h, "prueba.reservas.5@ejemplo.pe");

        assertThatThrownBy(() -> pedir(h, "prueba.reservas.6@ejemplo.pe"))
                .isInstanceOf(ReservaRechazada.class).hasMessageContaining("acaban de pedirlo");
    }
}
