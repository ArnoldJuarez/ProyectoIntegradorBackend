package pe.ssiomama.backend.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.ssiomama.backend.ia.EsquemaJson.lista;
import static pe.ssiomama.backend.ia.EsquemaJson.numero;
import static pe.ssiomama.backend.ia.EsquemaJson.objeto;
import static pe.ssiomama.backend.ia.EsquemaJson.texto;
import static pe.ssiomama.backend.ia.EsquemaJson.unoDe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * El validador de las respuestas de los agentes y la limpieza del texto.
 *
 * <p>Es lo que se interpone entre lo que devuelve un modelo pequeño y la base:
 * si deja pasar un JSON mal formado, la propuesta llega rota a la bandeja.
 */
class EsquemaJsonTest {

    private final ObjectMapper json = new ObjectMapper();

    private final EsquemaJson campania = objeto(
            "nombre", texto(),
            "canal", unoDe("CORREO", "SMS"),
            "lineas", lista(objeto("sku", texto(), "cantidad", numero())));

    @Test
    void aceptaLoQueCumple() throws Exception {
        var valor = json.readTree("""
                {"nombre": "Vuelve", "canal": "SMS", "lineas": [{"sku": "CER-01", "cantidad": 24}]}
                """);
        assertThat(campania.problemaCon(valor)).isEmpty();
    }

    @Test
    void detectaUnCampoQueFalta() throws Exception {
        var valor = json.readTree("""
                {"nombre": "Vuelve", "lineas": []}
                """);
        assertThat(campania.problemaCon(valor)).hasValueSatisfying(p -> assertThat(p).contains("canal"));
    }

    @Test
    void detectaUnValorFueraDelEnumerado() throws Exception {
        var valor = json.readTree("""
                {"nombre": "Vuelve", "canal": "PALOMA", "lineas": []}
                """);
        assertThat(campania.problemaCon(valor)).hasValueSatisfying(p -> assertThat(p).contains("$.canal"));
    }

    @Test
    void detectaUnTipoEquivocadoDentroDeUnaLista() throws Exception {
        var valor = json.readTree("""
                {"nombre": "Vuelve", "canal": "SMS", "lineas": [{"sku": "CER-01", "cantidad": "veinte"}]}
                """);
        assertThat(campania.problemaCon(valor))
                .hasValueSatisfying(p -> assertThat(p).contains("$.lineas[0].cantidad"));
    }

    @Test
    void elEsquemaQueSeMandaExigeTodosLosCampos() {
        // El modo estricto de los proveedores rechaza esquemas con opcionales.
        assertThat(campania.comoMapa()).containsEntry("additionalProperties", false);
        assertThat(campania.comoMapa().get("required")).isEqualTo(java.util.List.of("nombre", "canal", "lineas"));
    }

    @Test
    void sacaElJsonDeUnaVallaDeMarkdown() {
        String crudo = "Aquí tienes:\n```json\n{\"a\": 1}\n```\nEspero que sirva.";
        assertThat(ClienteDeModelo.extraerJson(crudo)).isEqualTo("{\"a\": 1}");
    }

    @Test
    void sacaElJsonDeEntreProsa() {
        assertThat(ClienteDeModelo.extraerJson("Claro: {\"a\": [1, 2]} listo")).isEqualTo("{\"a\": [1, 2]}");
    }

    @Test
    void limpiaEmojisNegritasYEspaciosDeMas() {
        assertThat(ClienteDeModelo.limpiar("¡Ven  este **sábado** 🎉 !")).isEqualTo("¡Ven este sábado!");
    }
}
