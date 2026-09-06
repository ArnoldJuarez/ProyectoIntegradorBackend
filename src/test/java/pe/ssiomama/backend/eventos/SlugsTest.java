package pe.ssiomama.backend.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cómo un título se convierte en una dirección.
 *
 * <p>Es de las pocas clases del proyecto que no toca la base, así que sus tests
 * corren en milisegundos y no necesitan levantar Spring. Vale la pena tenerlos
 * separados: el slug forma parte de la URL pública, y una regresión aquí rompe
 * enlaces ya compartidos.
 */
class SlugsTest {

    @Test
    @DisplayName("las tildes y la ñ se convierten, no se borran")
    void tildesYEnie() {
        // El caso que justifica la normalización NFD: sin ella, «canción» daría
        // «canci-n» porque la ó no es ASCII y se borraría entera.
        assertThat(Slugs.de("Canción de Verano")).isEqualTo("cancion-de-verano");
        assertThat(Slugs.de("Año Nuevo")).isEqualTo("ano-nuevo");
        assertThat(Slugs.de("Réquiem")).isEqualTo("requiem");
    }

    @Test
    @DisplayName("los signos y los espacios se vuelven un solo guion")
    void signos() {
        assertThat(Slugs.de("Viernes Urbano · Edición 2026")).isEqualTo("viernes-urbano-edicion-2026");
        assertThat(Slugs.de("¿Qué pasa?  ¡Fiesta!")).isEqualTo("que-pasa-fiesta");
        assertThat(Slugs.de("Rock & Roll")).isEqualTo("rock-roll");
    }

    @Test
    @DisplayName("no quedan guiones sueltos en los bordes")
    void sinGuionesEnLosBordes() {
        assertThat(Slugs.de("  ¡Sábado!  ")).isEqualTo("sabado");
        assertThat(Slugs.de("---Fiesta---")).isEqualTo("fiesta");
    }

    @Test
    @DisplayName("un titulo sin letras ni numeros no da un slug vacio")
    void tituloImposible() {
        // Vacío por sí solo, pero libre() se encarga de darle un nombre.
        assertThat(Slugs.de("¿?¡!")).isEmpty();
        assertThat(Slugs.libre("¿?¡!", candidato -> false)).isEqualTo("evento");
    }

    @Test
    @DisplayName("un titulo larguisimo se recorta sin dejar el guion colgando")
    void tituloLargo() {
        String largo = "Noche ".repeat(40);
        String slug = Slugs.de(largo);

        assertThat(slug).hasSizeLessThanOrEqualTo(80);
        assertThat(slug).doesNotEndWith("-");
    }

    /**
     * «Viernes Urbano» se repite cada semana, así que la segunda vez tiene que
     * salir algo legible y no un identificador aleatorio.
     */
    @Test
    @DisplayName("un slug ya cogido se numera")
    void slugRepetido() {
        Set<String> cogidos = Set.of("viernes-urbano", "viernes-urbano-2");

        assertThat(Slugs.libre("Viernes Urbano", cogidos::contains)).isEqualTo("viernes-urbano-3");
    }

    @Test
    @DisplayName("si esta libre se usa tal cual, sin sufijo")
    void slugLibre() {
        assertThat(Slugs.libre("Noche de Salsa", candidato -> false)).isEqualTo("noche-de-salsa");
    }
}
