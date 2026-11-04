package pe.ssiomama.backend.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lo que decide qué archivo se acepta y cómo se llama en disco. */
class MediosTest {

    @Test
    @DisplayName("un MP4 se reconoce por «ftyp», no por la extensión")
    void reconoceMp4() {
        byte[] cabecera = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        assertThat(Medios.formatoDeVideo(cabecera)).contains("mp4");
    }

    @Test
    @DisplayName("un WebM se reconoce por su marca EBML")
    void reconoceWebm() {
        byte[] cabecera = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0};
        assertThat(Medios.formatoDeVideo(cabecera)).contains("webm");
    }

    @Test
    @DisplayName("un PNG renombrado a .mp4 no pasa")
    void rechazaLoQueNoEsVideo() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertThat(Medios.formatoDeVideo(png)).isEmpty();
        assertThat(Medios.formatoDeVideo(new byte[0])).isEmpty();
    }

    @Test
    @DisplayName("los nombres de archivo quedan en minúsculas, sin tildes ni símbolos")
    void nombresLimpios() {
        assertThat(Medios.limpio("DJ Beto & Cía")).isEqualTo("dj-beto-cia");
        assertThat(Medios.limpio("  Ñoño!!  ")).isEqualTo("nono");
        assertThat(Medios.limpio("")).isEqualTo("archivo");
        assertThat(Medios.limpio("x".repeat(100))).hasSize(60);
    }

    @Test
    @DisplayName("el Instagram queda solo en el usuario, venga como venga")
    void instagramNormalizado() {
        assertThat(GestionDeArtistas.instagram("@DJ.Beto")).isEqualTo("dj.beto");
        assertThat(GestionDeArtistas.instagram("https://www.instagram.com/djbeto.aqp/")).isEqualTo("djbeto.aqp");
        assertThat(GestionDeArtistas.instagram("instagram.com/la_ruka?igsh=abc")).isEqualTo("la_ruka");
        assertThat(GestionDeArtistas.instagram("  ")).isNull();
        assertThatThrownBy(() -> GestionDeArtistas.instagram("no vale con espacios"))
                .isInstanceOf(EventoRechazado.class);
    }
}
