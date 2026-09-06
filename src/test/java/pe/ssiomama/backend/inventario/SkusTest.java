package pe.ssiomama.backend.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El código de almacén. Sin base de datos, así que corre en milisegundos. */
class SkusTest {

    @Test
    @DisplayName("el sku lleva el nombre reconocible y un sufijo")
    void formaDelSku() {
        String sku = Skus.libre("Cerveza Cusqueña 330ml", candidato -> false);

        assertThat(sku).startsWith("CERVEZA-CUSQUENA-3");
        // Raíz recortada a 18, un guion y cuatro caracteres de sufijo.
        assertThat(sku).hasSize(18 + 1 + 4);
    }

    @Test
    @DisplayName("las tildes y la ñ no rompen el codigo")
    void sinTildes() {
        assertThat(Skus.libre("Piña Colada", candidato -> false)).startsWith("PINA-COLADA-");
        assertThat(Skus.libre("Ron Añejo", candidato -> false)).startsWith("RON-ANEJO-");
    }

    /**
     * El caso que justifica el sufijo: dos nombres distintos que producen la
     * misma raíz una vez recortada a 18 caracteres.
     */
    @Test
    @DisplayName("dos nombres que se recortan igual no chocan")
    void nombresQueSeRecortanIgual() {
        Set<String> emitidos = new HashSet<>();

        String uno = Skus.libre("Cerveza Pilsen Callao 330ml", emitidos::contains);
        emitidos.add(uno);
        String otro = Skus.libre("Cerveza Pilsen Callao 650ml", emitidos::contains);

        assertThat(uno).isNotEqualTo(otro);
    }

    @Test
    @DisplayName("un nombre sin letras ni numeros no da un sku vacio")
    void nombreImposible() {
        assertThat(Skus.libre("¿?¡!", candidato -> false)).startsWith("PRODUCTO-");
    }

    @Test
    @DisplayName("el sufijo no usa caracteres que se confunden al dictarlos")
    void sinCaracteresAmbiguos() {
        // Fuera la O y el 0, la I y el 1, la S y el 5: este código se lee por
        // teléfono o se copia de un albarán.
        for (int i = 0; i < 200; i++) {
            String sufijo = Skus.libre("Producto", candidato -> false).substring("PRODUCTO-".length());
            assertThat(sufijo).doesNotContain("O", "0", "I", "1", "S", "5");
        }
    }

    @Test
    @DisplayName("si el sufijo ya esta cogido se prueba con otro")
    void reintentaConOtroSufijo() {
        // Se rechaza el primero que salga y se comprueba que devuelve otro.
        var primeros = new HashSet<String>();
        String sku = Skus.libre("Vodka", candidato -> primeros.size() < 3 && primeros.add(candidato));

        assertThat(sku).startsWith("VODKA-");
        assertThat(primeros).doesNotContain(sku);
    }
}
