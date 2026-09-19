package pe.ssiomama.backend.clientes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cómo se lee e interpreta la regla de un segmento.
 *
 * <p>Sin base de datos: aquí solo se comprueba que el JSON guardado se entiende
 * y que se explica bien en pantalla. Que la condición seleccione a la gente
 * correcta se prueba contra datos reales en {@code CrmTest}.
 */
class ReglaDeSegmentoTest {

    private final ObjectMapper json = new ObjectMapper();

    private ReglaDeSegmento leer(String texto) throws Exception {
        return json.readValue(texto, ReglaDeSegmento.class);
    }

    /**
     * Las claves del JSON están en inglés porque las escribió el sistema
     * anterior y el renombrado de tablas no tocó el contenido de las columnas
     * {@code jsonb}. Este test fija ese contrato: si alguien «traduce» las
     * claves, las cuatro reglas del seed dejan de leerse.
     */
    @Test
    @DisplayName("las cuatro reglas del seed se leen tal y como estan guardadas")
    void lasDelSeed() throws Exception {
        assertThat(leer("{\"type\": \"birthdayMonth\"}").esConocida()).isTrue();

        var dormidos = leer("{\"type\": \"inactive\", \"daysSinceLastVisit\": 60}");
        assertThat(dormidos.esConocida()).isTrue();
        assertThat(dormidos.diasSinVenir()).isEqualTo(60);

        var nuevos = leer("{\"days\": 30, \"type\": \"recentFirstPurchase\"}");
        assertThat(nuevos.esConocida()).isTrue();
        assertThat(nuevos.dias()).isEqualTo(30);

        var vip = leer("{\"type\": \"spend\", \"minTotalCents\": 30000}");
        assertThat(vip.esConocida()).isTrue();
        assertThat(vip.gastoMinimoCentimos()).isEqualTo(30_000);
    }

    @Test
    @DisplayName("cada regla se explica en castellano")
    void enPalabras() throws Exception {
        assertThat(leer("{\"type\": \"inactive\", \"daysSinceLastVisit\": 60}").enPalabras())
                .isEqualTo("Sin venir desde hace más de 60 días");
        assertThat(leer("{\"type\": \"spend\", \"minTotalCents\": 30000}").enPalabras())
                .isEqualTo("Gasto acumulado por encima de S/ 300.00");
        assertThat(leer("{\"type\": \"birthdayMonth\"}").enPalabras())
                .isEqualTo("Cumplen años este mes");
        assertThat(leer("{\"days\": 30, \"type\": \"recentFirstPurchase\"}").enPalabras())
                .isEqualTo("Primera compra en los últimos 30 días");
        assertThat(leer("{\"type\": \"consented\"}").enPalabras())
                .isEqualTo("Han dado su consentimiento para recibir comunicaciones");
    }

    /**
     * El texto se genera al vuelo y no se guarda junto a la regla. Si se
     * guardara, subir el umbral de 60 a 90 días dejaría la descripción mintiendo
     * hasta que alguien se acordara de editarla.
     */
    @Test
    @DisplayName("cambiar el umbral cambia tambien el texto")
    void elTextoSigueALaRegla() throws Exception {
        assertThat(leer("{\"type\": \"inactive\", \"daysSinceLastVisit\": 90}").enPalabras())
                .contains("90 días");
    }

    /**
     * Un tipo que este sistema no implementa —uno que añada el día de mañana
     * otra versión, por ejemplo— no debe tumbar la pantalla ni, peor, incluir a
     * todo el mundo en una campaña.
     */
    @Test
    @DisplayName("una regla desconocida no incluye a nadie")
    void reglaDesconocida() throws Exception {
        var rara = leer("{\"type\": \"loQueSea\", \"umbral\": 5}");

        assertThat(rara.esConocida()).isFalse();
        assertThat(rara.enPalabras()).isEqualTo("Regla no reconocida");
    }

    /** Un campo de más en el JSON no puede romper la lectura. */
    @Test
    @DisplayName("las claves que sobran se ignoran")
    void clavesDeMas() throws Exception {
        var regla = leer("{\"type\": \"consented\", \"algoNuevo\": true, \"otra\": 3}");

        assertThat(regla.esConocida()).isTrue();
        assertThat(regla.tipo()).isEqualTo(ReglaDeSegmento.CON_CONSENTIMIENTO);
    }

    /**
     * Un JSON al que le falta su número no debe reventar. La de gasto cae a un
     * umbral inalcanzable —segmento vacío—, que es el fallo inofensivo: lo
     * contrario sería escribirle a la base entera.
     */
    @Test
    @DisplayName("si falta el numero, la regla no incluye de mas")
    void numeroQueFalta() throws Exception {
        var sinUmbral = leer("{\"type\": \"spend\"}");

        assertThat(sinUmbral.esConocida()).isTrue();
        assertThat(sinUmbral.enPalabras()).contains("21474836.47");
    }
}
