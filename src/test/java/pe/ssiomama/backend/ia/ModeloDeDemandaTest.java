package pe.ssiomama.backend.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * La regresión que proyecta asistencia y consumo para el agente de inventario.
 *
 * <p>Se prueba con datos sintéticos cuya respuesta se conoce: si la ocupación
 * es exactamente el doble los sábados, el modelo tiene que decir el doble.
 */
class ModeloDeDemandaTest {

    private final ModeloDeDemanda modelo = new ModeloDeDemanda();

    @Test
    void laQuincenaYElFinDeMesCuentanComoDiaDeCobro() {
        assertThat(ModeloDeDemanda.diasDesdePago(LocalDate.of(2026, 9, 30))).isZero();
        assertThat(ModeloDeDemanda.diasDesdePago(LocalDate.of(2026, 9, 15))).isZero();
        assertThat(ModeloDeDemanda.diasDesdePago(LocalDate.of(2026, 9, 18))).isEqualTo(3);
        assertThat(ModeloDeDemanda.diasDesdePago(LocalDate.of(2026, 9, 4))).isEqualTo(4);
    }

    @Test
    void reconoceLosFeriadosCercanos() {
        assertThat(ModeloDeDemanda.esFeriadoCercano(LocalDate.of(2026, 7, 27))).isTrue();
        assertThat(ModeloDeDemanda.esFeriadoCercano(LocalDate.of(2026, 9, 12))).isFalse();
    }

    @Test
    void sinHistoricoSuficienteNoSeInventaNada() {
        assertThatThrownBy(() -> modelo.entrenar(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos 20 noches");
    }

    @Test
    void recuperaElEfectoDelSabadoYElConsumoPorPersona() {
        List<ModeloDeDemanda.NocheCelebrada> historico = new ArrayList<>();
        // Ocho semanas de viernes y sábados, dos géneros alternos. Los viernes
        // entra el 30 % del aforo y los sábados el 60 %; cada persona bebe 2
        // cervezas en reguetón y 1 en salsa.
        LocalDate viernes = LocalDate.of(2026, 4, 3);
        for (int semana = 0; semana < 14; semana++) {
            for (int d = 0; d < 2; d++) {
                LocalDate dia = viernes.plusWeeks(semana).plusDays(d);
                String genero = (semana + d) % 2 == 0 ? "Reguetón" : "Salsa";
                int asistentes = d == 1 ? 300 : 150;
                int cervezas = asistentes * (genero.equals("Reguetón") ? 2 : 1);
                historico.add(new ModeloDeDemanda.NocheCelebrada(
                        alasDiez(dia), 500, asistentes, List.of(genero),
                        List.of(new ModeloDeDemanda.Consumo("CER-01", "Cerveza", cervezas))));
            }
        }

        var entrenado = modelo.entrenar(historico);
        var sabado = ModeloDeDemanda.Rasgos.de(alasDiez(LocalDate.of(2026, 10, 10)), List.of("Reguetón"), 500);

        // Abril a julio no tiene verano ni feriados que confundan: el sábado
        // queda limpio.
        assertThat(entrenado.proyectarAsistencia(sabado)).isCloseTo(300, within(15));
        var cerveza = entrenado.proyectarConsumo(sabado).lineas().get(0);
        assertThat(cerveza.porAsistente()).isCloseTo(2.0, within(0.05));
    }

    private static Instant alasDiez(LocalDate dia) {
        return ZonedDateTime.of(dia.atTime(22, 0), Club.ZONA).toInstant();
    }
}
