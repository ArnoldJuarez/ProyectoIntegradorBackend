package pe.ssiomama.backend.entradas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Paridad con la implementación de Node.
 *
 * <p>Los valores esperados no están escritos a mano: salen de ejecutar
 * {@code lib/tickets.ts} con el mismo secreto de prueba. Mientras estos casos
 * pasen, un QR emitido por el Next.js valida en el backend Java y al revés, que
 * es lo que permite migrar la puerta sin reemitir las entradas vendidas.
 *
 * <p>Se usa un secreto de prueba y no el real justamente para que este archivo
 * pueda vivir en git.
 */
class FirmaDeEntradaTest {

    private static final String SECRETO_DE_PRUEBA = "secreto-de-prueba-no-el-real";

    private final FirmaDeEntrada firma = new FirmaDeEntrada(SECRETO_DE_PRUEBA);

    @ParameterizedTest(name = "\"{0}\" firma como \"{1}\"")
    @CsvSource({
        "ckt1a2b3c4d5e6f7g8h9i0j, Ss-kaQELDtEbynn8phMS53q6",
        "abc,                     CAMiD_oPdtL_KThc2LeTMbYV",
        "DSC-000001,              BY4Hmc3jcyo6cQk0h6kk641w",
    })
    @DisplayName("produce la misma firma que Node")
    void produceLaMismaFirmaQueNode(String id, String firmaEsperada) {
        assertEquals(id + "." + firmaEsperada, firma.codificar(id));
    }

    /**
     * Un id con acentos revienta si alguien deja que la JVM elija el charset: en
     * Windows el charset por defecto no es UTF-8 y la firma saldría distinta.
     */
    @Test
    @DisplayName("firma igual que Node con caracteres no ASCII")
    void firmaIgualQueNodeConAcentos() {
        assertEquals(
                "ñandú-áéíóú.5zpZNbgGNPlG4fMgPZFEPcBC",
                firma.codificar("ñandú-áéíóú"));
    }

    @Test
    @DisplayName("un codigo valido se decodifica al id original")
    void idaYVuelta() {
        String id = "ckt1a2b3c4d5e6f7g8h9i0j";
        assertEquals(id, firma.decodificar(firma.codificar(id)));
    }

    @Test
    @DisplayName("una firma manipulada se rechaza")
    void rechazaFirmaManipulada() {
        // Una sola letra cambiada al final de la firma.
        assertNull(firma.decodificar("ckt1a2b3c4d5e6f7g8h9i0j.Ss-kaQELDtEbynn8phMS53q7"));
    }

    @Test
    @DisplayName("un id cambiado con la firma de otro se rechaza")
    void rechazaIdCambiado() {
        assertNull(firma.decodificar("otro-id-cualquiera.Ss-kaQELDtEbynn8phMS53q6"));
    }

    /**
     * Una firma más corta o más larga no debe hacer estallar la comparación: la
     * versión de Node tuvo que comprobar la longitud antes de {@code
     * timingSafeEqual} por esto mismo.
     */
    @Test
    @DisplayName("una firma de otra longitud se rechaza sin reventar")
    void rechazaFirmaDeOtraLongitud() {
        assertNull(firma.decodificar("ckt1a2b3c4d5e6f7g8h9i0j.corta"));
        assertNull(firma.decodificar("ckt1a2b3c4d5e6f7g8h9i0j.Ss-kaQELDtEbynn8phMS53q6xxxxxx"));
    }

    @Test
    @DisplayName("un codigo sin punto se rechaza")
    void rechazaCodigoSinPunto() {
        assertNull(firma.decodificar("sinpunto"));
        assertNull(firma.decodificar(""));
        assertNull(firma.decodificar(null));
    }

    /**
     * El id puede llevar puntos: hay que partir por el último, no por el primero.
     */
    @Test
    @DisplayName("respeta ids que contienen puntos")
    void respetaIdsConPuntos() {
        String id = "evento.2026.entrada";
        assertEquals(id, firma.decodificar(firma.codificar(id)));
    }
}
