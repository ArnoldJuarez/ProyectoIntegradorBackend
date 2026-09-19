package pe.ssiomama.backend.clientes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.function.Predicate;

/**
 * La condición que define un segmento del CRM.
 *
 * <h2>Reglas, no listas</h2>
 *
 * <p>Un segmento no guarda qué clientes pertenecen a él: guarda <b>la
 * condición</b>. Se evalúa cada vez que alguien lo consulta, así que un cliente
 * entra y sale solo — deja de venir dos meses y aparece en «Dormidos» sin que
 * nadie toque nada, y vuelve a comprar y desaparece de ahí.
 *
 * <p>Una lista congelada tendría que recalcularse a mano, y el día que nadie la
 * recalcula la campaña se manda a quien ya no toca.
 *
 * <h2>Por qué las claves del JSON están en inglés</h2>
 *
 * <p>{@code {"type": "inactive", "daysSinceLastVisit": 60}}. Las escribió el
 * sistema anterior y siguen así en la base: el renombrado de tablas y columnas
 * no tocó el contenido de las columnas {@code jsonb}.
 *
 * <p>Se leen tal cual en vez de migrarlas. Cambiarlas obligaría a reescribir
 * filas de datos reales para no ganar nada funcional, y dejaría a cualquier
 * segmento creado antes del cambio sin poder evaluarse. Lo que sí está en
 * español es todo lo que se ve: el nombre, la descripción y el texto que
 * explica la regla.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReglaDeSegmento(
        @JsonProperty("type") String tipo,
        @JsonProperty("daysSinceLastVisit") Integer diasSinVenir,
        @JsonProperty("minTotalCents") Integer gastoMinimoCentimos,
        @JsonProperty("days") Integer dias) {

    /** Los cinco tipos que el sistema sabe evaluar. */
    public static final String SIN_VENIR = "inactive";
    public static final String POR_GASTO = "spend";
    public static final String CUMPLEANOS = "birthdayMonth";
    public static final String RECIEN_LLEGADOS = "recentFirstPurchase";
    public static final String CON_CONSENTIMIENTO = "consented";

    /** La zona en la que se decide qué día es hoy. */
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /**
     * Cómo se lee esta regla en pantalla.
     *
     * <p>El texto se genera aquí y no se guarda en la base junto a la regla: si
     * se guardara, cambiar el umbral de 60 a 90 días dejaría una descripción
     * mintiendo hasta que alguien se acordara de editarla.
     */
    public String enPalabras() {
        if (tipo == null) {
            return "Regla no reconocida";
        }
        return switch (tipo) {
            case SIN_VENIR -> "Sin venir desde hace más de %d días".formatted(diasSinVenirODefecto());
            case POR_GASTO -> "Gasto acumulado por encima de S/ %.2f"
                    .formatted(gastoMinimoODefecto() / 100.0);
            case CUMPLEANOS -> "Cumplen años este mes";
            case RECIEN_LLEGADOS -> "Primera compra en los últimos %d días".formatted(diasODefecto());
            case CON_CONSENTIMIENTO -> "Han dado su consentimiento para recibir comunicaciones";
            default -> "Regla no reconocida";
        };
    }

    /**
     * ¿Sabe el sistema evaluar esta regla?
     *
     * <p>El nulo se comprueba antes del switch y no como un caso más: un
     * `switch` sobre un String nulo lanza NullPointerException, y aquí eso
     * significaría que un JSON sin la clave «type» tumba la pantalla entera del
     * CRM en vez de enseñar un segmento marcado como no reconocido.
     */
    public boolean esConocida() {
        if (tipo == null) {
            return false;
        }
        return switch (tipo) {
            case SIN_VENIR, POR_GASTO, CUMPLEANOS, RECIEN_LLEGADOS, CON_CONSENTIMIENTO -> true;
            default -> false;
        };
    }

    /**
     * La condición aplicada a un cliente concreto.
     *
     * <p>Se evalúa en memoria y no como consulta SQL a propósito. Son unas
     * decenas de clientes y cinco reglas: traducir cada una a su {@code where}
     * daría cinco consultas distintas que mantener, y la de cumpleaños ni
     * siquiera se puede expresar bien con el modelo de datos actual. El día que
     * haya miles de clientes habrá que bajarlo a la base, y entonces el cambio
     * será solo aquí dentro.
     *
     * @param ahora el instante contra el que se miden los plazos. Llega desde
     *     fuera para que los tests puedan fijar la fecha.
     */
    public Predicate<Cliente> comoFiltro(Instant ahora) {
        if (tipo == null) {
            return cliente -> false;
        }
        return switch (tipo) {
            case SIN_VENIR -> cliente -> {
                // Sin última visita no se puede decir que lleve tiempo sin venir:
                // es alguien que nunca vino, que es otra cosa.
                if (cliente.getUltimaVisita() == null || !cliente.isAceptaMarketing()) {
                    return false;
                }
                long dias = ChronoUnit.DAYS.between(cliente.getUltimaVisita(), ahora);
                return dias > diasSinVenirODefecto();
            };

            case POR_GASTO -> cliente -> cliente.getGastadoCentimos() >= gastoMinimoODefecto();

            case CUMPLEANOS -> cliente -> {
                LocalDate nacimiento = cliente.getFechaDeNacimiento();
                return nacimiento != null
                        && nacimiento.getMonth() == LocalDate.ofInstant(ahora, LIMA).getMonth();
            };

            case RECIEN_LLEGADOS -> cliente ->
                    ChronoUnit.DAYS.between(cliente.getCreadoEn(), ahora) <= diasODefecto();

            case CON_CONSENTIMIENTO -> Cliente::isAceptaMarketing;

            // Una regla que no se entiende no incluye a nadie. Es lo contrario
            // de lo que haría un «incluir a todos» por defecto: mandar una
            // campaña a la base entera por un JSON mal escrito.
            default -> cliente -> false;
        };
    }

    /*
     * Los tres valores por defecto de abajo cubren un JSON al que le falta su
     * número. No debería pasar, pero si pasa es mejor un segmento vacío o
     * inofensivo que una excepción al abrir la pantalla del CRM.
     */

    private int diasSinVenirODefecto() {
        return diasSinVenir == null ? 60 : diasSinVenir;
    }

    private int gastoMinimoODefecto() {
        return gastoMinimoCentimos == null ? Integer.MAX_VALUE : gastoMinimoCentimos;
    }

    private int diasODefecto() {
        return dias == null ? 30 : dias;
    }
}
