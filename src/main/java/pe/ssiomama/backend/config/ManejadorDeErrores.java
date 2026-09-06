package pe.ssiomama.backend.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import pe.ssiomama.backend.entradas.CompraRechazada;

/**
 * Da a los errores de la API una forma única: {@code {"error": "..."}}.
 *
 * <h2>Por qué hace falta</h2>
 *
 * <p>Spring, por su cuenta, responde a un {@code ResponseStatusException} con un
 * cuerpo genérico —{@code {"status":409,"error":"Conflict","path":...}}— y
 * <b>tira el motivo</b>. Es decir: se escribe «ese reservado es para un máximo
 * de 6 personas» y al cliente le llega «Conflict». Lo mismo con las
 * validaciones: los mensajes de {@code @NotBlank} no salen del servidor.
 *
 * <p>El resultado era una web que sabe que algo falló pero no puede decir qué,
 * que es la peor versión de un formulario.
 *
 * <h2>Lo que NO hace</h2>
 *
 * <p>No captura {@code Exception}. Un fallo inesperado tiene que seguir siendo
 * un 500 con el cuerpo por defecto: convertir cualquier excepción en un mensaje
 * para el cliente es como se acaban publicando trazas de la base de datos.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    /** La forma que espera el frontend. Una sola clave y en castellano. */
    public record Problema(String error) {}

    /**
     * Los errores que un controlador lanza a propósito, con su motivo escrito
     * para que lo lea una persona.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Problema> deEstado(ResponseStatusException e) {
        String motivo = e.getReason();
        return ResponseEntity.status(e.getStatusCode())
                .body(new Problema(motivo == null || motivo.isBlank() ? "No se pudo." : motivo));
    }

    /**
     * Validación del cuerpo de la petición.
     *
     * <p>Se devuelve <b>el primer</b> mensaje y no la lista entera: un
     * formulario de cinco campos con cinco errores a la vez no se arregla más
     * rápido enseñándolos todos, y el primero es el que la persona va a
     * corregir.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Problema> deValidacion(MethodArgumentNotValidException e) {
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .map(campo -> campo.getDefaultMessage())
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("Revisa los datos.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Problema(mensaje));
    }

    /**
     * Una compra que el negocio no admite: se agotaron, es de otra fecha, el
     * pedido ya no se puede pagar.
     *
     * <p>409 y no 400: la petición estaba bien formada, lo que pasa es que el
     * estado del mundo cambió mientras alguien rellenaba el formulario. El
     * mensaje va escrito para el comprador y sale tal cual.
     */
    @ExceptionHandler(CompraRechazada.class)
    public ResponseEntity<Problema> deCompra(CompraRechazada e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Problema(e.getMessage()));
    }
}
