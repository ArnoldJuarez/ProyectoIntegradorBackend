package pe.ssiomama.backend.entradas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * La firma del QR, contra las entradas <b>reales</b> de la base.
 *
 * <p>La columna {@code codigo} de {@code "Entrada"} no guarda un código legible:
 * guarda exactamente {@code id.firma}, el texto que viaja dentro del QR.
 *
 * <p>Hasta la migración a ids numéricos, los códigos guardados se habían
 * emitido con otro {@code TICKET_SECRET} y no validaban. La migración V7 los
 * volvió a firmar todos con el id nuevo y el secreto configurado, así que ahora
 * se puede exigir lo que de verdad importa: que cada código guardado sea el que
 * produce la firma.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FirmaContraEntradasRealesTest {

    @Autowired
    private EntradaRepository entradas;

    @Autowired
    private FirmaDeEntrada firma;

    /**
     * El camino completo sobre ids reales: firmar y volver a leer.
     *
     * <p>Es el recorrido que hace el escáner de la puerta, con los mismos ids
     * que tiene la base, no los de un test inventado.
     */
    @Test
    @DisplayName("firma y descifra correctamente los ids reales de la base")
    void idaYVueltaSobreIdsReales() {
        List<Entrada> todas = entradas.findAll();

        assertThat(todas)
                .as("la base tiene entradas emitidas por el sistema anterior")
                .isNotEmpty();

        assertThat(todas).allSatisfy(entrada -> {
            String codigo = firma.codificar(entrada.getId());
            assertThat(firma.decodificar(codigo))
                    .as("no se recupera el id de la entrada %s", entrada.getId())
                    .isEqualTo(entrada.getId());
        });
    }

    /** Firmar dos veces el mismo id da lo mismo: si no, nada sería verificable. */
    @Test
    @DisplayName("la firma de un id real es estable")
    void laFirmaEsEstable() {
        Long id = entradas.findAll().getFirst().getId();

        assertThat(firma.codificar(id)).isEqualTo(firma.codificar(id));
    }

    /**
     * Cambiar una letra invalida el código. Es lo que impide que alguien se
     * fabrique una entrada partiendo de la suya.
     */
    @Test
    @DisplayName("un codigo manipulado deja de validar")
    void unCodigoManipuladoNoVale() {
        String codigo = firma.codificar(entradas.findAll().getFirst().getId());

        char ultimo = codigo.charAt(codigo.length() - 1);
        String manipulado = codigo.substring(0, codigo.length() - 1) + (ultimo == 'A' ? 'B' : 'A');

        assertThat(firma.decodificar(manipulado)).isNull();
    }

    /**
     * Cada QR guardado valida con el secreto actual.
     *
     * <p>Si falla, o se cambió {@code TICKET_SECRET} después de emitir, o alguna
     * entrada se insertó sin pasar por la firma: en los dos casos esas entradas
     * no entrarían por la puerta.
     */
    @Test
    @DisplayName("los codigos guardados son los que produce la firma")
    void losCodigosGuardadosValidan() {
        assertThat(entradas.findAll()).allSatisfy(entrada -> assertThat(entrada.getCodigo())
                .as("el QR guardado de la entrada %s", entrada.getId())
                .isEqualTo(firma.codificar(entrada.getId())));
    }
}
