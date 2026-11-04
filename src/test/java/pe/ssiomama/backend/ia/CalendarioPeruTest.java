package pe.ssiomama.backend.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Las fechas que el programador de noches usa para buscar vísperas y noches especiales. */
class CalendarioPeruTest {

    @Test
    @DisplayName("Pascua cae donde dice el calendario")
    void pascua() {
        assertThat(CalendarioPeru.domingoDePascua(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(CalendarioPeru.domingoDePascua(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
        assertThat(CalendarioPeru.domingoDePascua(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
    }

    @Test
    @DisplayName("Jueves y Viernes Santo son feriado; el Domingo de Pascua no")
    void semanaSanta() {
        assertThat(CalendarioPeru.esFeriado(LocalDate.of(2026, 4, 2))).isTrue();
        assertThat(CalendarioPeru.esFeriado(LocalDate.of(2026, 4, 3))).isTrue();
        assertThat(CalendarioPeru.esFeriado(LocalDate.of(2026, 4, 5))).isFalse();
    }

    @Test
    @DisplayName("octubre de 2026: Angamos es feriado y Halloween es fiesta, no feriado")
    void octubre() {
        var fechas = CalendarioPeru.entre(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertThat(fechas).extracting(CalendarioPeru.Fecha::nombre)
                .containsExactly("Combate de Angamos", "Halloween y Día de la Canción Criolla");
        assertThat(fechas.get(0).feriado()).isTrue();
        assertThat(fechas.get(1).feriado()).isFalse();
    }

    @Test
    @DisplayName("el aniversario de Arequipa está, aunque no es feriado nacional")
    void aniversarioDeArequipa() {
        var agosto = CalendarioPeru.entre(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15));
        assertThat(agosto).singleElement().satisfies(f -> {
            assertThat(f.nombre()).isEqualTo("Aniversario de Arequipa");
            assertThat(f.feriado()).isFalse();
        });
    }
}
