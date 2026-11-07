package pe.ssiomama.backend.entradas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * La firma de los QR.
 *
 * <p>El algoritmo es el de siempre (HMAC-SHA256, base64url sin relleno,
 * recortado a 24): los valores esperados de los vectores salen de ejecutar
 * {@code lib/tickets.ts} con el mismo secreto de prueba. Lo que cambió es lo que
 * se firma: el id numérico de la entrada.
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
    @DisplayName("el algoritmo produce la misma firma que Node")
    void produceLaMismaFirmaQueNode(String texto, String firmaEsperada) {
        assertEquals(firmaEsperada, firma.firmar(texto));
    }

    /**
     * Un texto con acentos revienta si alguien deja que la JVM elija el charset:
     * en Windows el charset por defecto no es UTF-8 y la firma saldría distinta.
     */
    @Test
    @DisplayName("firma igual que Node con caracteres no ASCII")
    void firmaIgualQueNodeConAcentos() {
        assertEquals("5zpZNbgGNPlG4fMgPZFEPcBC", firma.firmar("ñandú-áéíóú"));
    }

    @Test
    @DisplayName("el codigo es el id, un punto y la firma del id")
    void formaDelCodigo() {
        assertEquals("1543." + firma.firmar("1543"), firma.codificar(1543L));
    }

    @Test
    @DisplayName("un codigo valido se decodifica al id original")
    void idaYVuelta() {
        Long id = 18_314L;
        assertEquals(id, firma.decodificar(firma.codificar(id)));
    }

    @Test
    @DisplayName("una firma manipulada se rechaza")
    void rechazaFirmaManipulada() {
        String codigo = firma.codificar(42L);
        String ultima = codigo.substring(codigo.length() - 1);
        String manipulado = codigo.substring(0, codigo.length() - 1) + ("A".equals(ultima) ? "B" : "A");
        assertNull(firma.decodificar(manipulado));
    }

    @Test
    @DisplayName("un id cambiado con la firma de otro se rechaza")
    void rechazaIdCambiado() {
        String deLa42 = firma.codificar(42L);
        assertNull(firma.decodificar("43" + deLa42.substring(deLa42.indexOf('.'))));
    }

    /**
     * Un QR emitido antes de los ids numéricos tiene la firma bien hecha (mismo
     * secreto, mismo algoritmo), pero su id era un texto que ya no existe.
     */
    @Test
    @DisplayName("un QR con id de texto del sistema anterior se rechaza")
    void rechazaIdDeTexto() {
        assertNull(firma.decodificar("ckt1a2b3c4d5e6f7g8h9i0j.Ss-kaQELDtEbynn8phMS53q6"));
    }

    /**
     * Una firma más corta o más larga no debe hacer estallar la comparación: la
     * versión de Node tuvo que comprobar la longitud antes de {@code
     * timingSafeEqual} por esto mismo.
     */
    @Test
    @DisplayName("una firma de otra longitud se rechaza sin reventar")
    void rechazaFirmaDeOtraLongitud() {
        String codigo = firma.codificar(7L);
        assertNull(firma.decodificar("7.corta"));
        assertNull(firma.decodificar(codigo + "xxxxxx"));
    }

    @Test
    @DisplayName("un codigo sin punto se rechaza")
    void rechazaCodigoSinPunto() {
        assertNull(firma.decodificar("sinpunto"));
        assertNull(firma.decodificar(""));
        assertNull(firma.decodificar(null));
    }
}
