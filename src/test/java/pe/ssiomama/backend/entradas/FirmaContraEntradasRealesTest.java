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
 * <h2>Lo que se descubrió al escribir esto</h2>
 *
 * <p>La columna {@code code} de la tabla {@code "Ticket"} no guarda un código
 * legible: guarda exactamente {@code id.firma}, el texto que viaja dentro del
 * código de barras. Así que reproducir esa columna desde el id sería reproducir
 * el QR del cliente.
 *
 * <p>Pues bien: <b>no coincide</b>. Y no por un fallo de esta migración. Se
 * comprobó ejecutando el {@code lib/tickets.ts} original con el
 * {@code TICKET_SECRET} que hay hoy en el {@code .env}, y produce la misma firma
 * que este código Java —y distinta de la guardada—.
 *
 * <p>La conclusión es que <b>las entradas de la base se emitieron con otro
 * secreto</b>: alguien regeneró {@code TICKET_SECRET} después de crearlas. Es
 * exactamente lo que avisa el README del sistema anterior, y significa que esos
 * QR no validan hoy en ninguna de las dos versiones.
 *
 * <p>Por eso este test <b>no</b> compara contra los códigos guardados: hacerlo
 * sería fijar como correcto un dato que no lo es. Lo que fija es que el
 * mecanismo funciona de ida y vuelta con el secreto que esté configurado, que es
 * lo que de verdad tiene que seguir siendo cierto.
 *
 * <p><b>Para poder demostrar el escáner de puerta hay que volver a sembrar los
 * datos</b> ({@code npm run db:seed} en el proyecto anterior), y así las
 * entradas quedan firmadas con el secreto actual.
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
     * que tiene la base —UUID, no los de un test inventado—.
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
        String id = entradas.findAll().getFirst().getId();

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
     * Deja constancia de la situación: los códigos guardados llevan otra firma.
     *
     * <p>El día que se vuelva a sembrar con el secreto actual, este test fallará
     * — y ese fallo será la señal de que ya coinciden y de que se puede endurecer
     * la comprobación.
     */
    @Test
    @DisplayName("aviso: los codigos guardados son de otro TICKET_SECRET")
    void losCodigosGuardadosSonDeOtroSecreto() {
        Entrada primera = entradas.findAll().getFirst();

        assertThat(firma.codificar(primera.getId()))
                .as("""
                        Los codigos guardados YA coinciden con el secreto actual. \
                        Eso es una buena noticia: sustituye este test por uno que \
                        compruebe la igualdad sobre todas las entradas.""")
                .isNotEqualTo(primera.getCodigo());
    }
}
