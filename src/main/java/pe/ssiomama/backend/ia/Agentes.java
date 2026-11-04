package pe.ssiomama.backend.ia;

import static pe.ssiomama.backend.ia.EsquemaJson.lista;
import static pe.ssiomama.backend.ia.EsquemaJson.numero;
import static pe.ssiomama.backend.ia.EsquemaJson.objeto;
import static pe.ssiomama.backend.ia.EsquemaJson.texto;
import static pe.ssiomama.backend.ia.EsquemaJson.unoDe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Los agentes de fondo: qué saben, qué devuelven y cómo se lanzan.
 *
 * <p><b>Ningún agente actúa solo.</b> Cada ejecución deja una propuesta
 * pendiente en {@code PropuestaAgente}, y solo cuando una persona la aprueba
 * desde el panel llega a tocar datos (ver {@link AplicadorDePropuestas}). Es la
 * regla que separa «IA útil» de «IA que manda un correo con el precio
 * equivocado a cuatro mil clientes».
 *
 * <p>Toda ejecución queda en {@code EjecucionAgente} —entrada, salida, modelo,
 * tokens, coste—, también las que fallan.
 */
@Service
public class Agentes {

    /**
     * Corto y en imperativo. Medido en el sistema anterior: una regla dentro de
     * una frase subordinada se ignora; en su propia línea se cumple.
     */
    private static final String ESTILO_DE_LA_CASA = """
            Eres del equipo de %s, una discoteca de %s.

            Escribe en español peruano, directo y sin palabrería de marketing.
            No uses emojis.
            No inventes datos: usa solo las cifras que te doy.""".formatted(Club.NOMBRE, Club.CIUDAD);

    /** Lo que define a un agente. */
    public record Definicion(
            ClaveDeAgente clave,
            String descripcion,
            TipoDePropuesta tipoDePropuesta,
            /** Lo que va a leer una persona va al motor de prosa; los números, al otro. */
            boolean prosa,
            EsquemaJson esquema,
            Supplier<Optional<String>> recolector,
            String instruccion) {}

    /** Lo que deja una ejecución. */
    public record Resultado(String ejecucionId, String propuestaId, String mensaje) {}

    private final Map<ClaveDeAgente, Definicion> definiciones = new LinkedHashMap<>();
    private final ClienteDeModelo modelo;
    private final MotoresDeIa motores;
    private final RegistroDeIa registro;

    public Agentes(ClienteDeModelo modelo, MotoresDeIa motores, RegistroDeIa registro, Recolectores datos) {
        this.modelo = modelo;
        this.motores = motores;
        this.registro = registro;

        definir(new Definicion(
                ClaveDeAgente.MARKETING,
                "Redacta el post de Instagram y el correo del próximo evento.",
                TipoDePropuesta.TEXTO_CAMPANIA,
                true,
                objeto(
                        "hook", texto("Una frase para el titular del evento, máximo 12 palabras"),
                        "instagram", objeto(
                                "caption", texto("Texto del post, 2 o 3 frases"),
                                "hashtags", lista(texto(), "Entre 4 y 8 hashtags sin la almohadilla")),
                        "correo", objeto(
                                "asunto", texto("Asunto del correo, máximo 60 caracteres"),
                                "cuerpo", texto("Cuerpo del correo en texto plano, 3 o 4 párrafos cortos"))),
                datos::marketing,
                "Escribe los textos de promoción del próximo evento. El gancho tiene que servir de titular, "
                        + "el post de Instagram debe entrar por los ojos sin sonar a folleto, y el correo va dirigido a "
                        + "gente que ya ha venido antes. Menciona el precio más bajo disponible y la fecha."));

        definir(new Definicion(
                ClaveDeAgente.SEGMENTADOR_CRM,
                "Analiza los segmentos y propone la campaña que más falta hace.",
                TipoDePropuesta.CAMPANIA,
                true,
                objeto(
                        "findings", lista(objeto(
                                "segmento", texto(),
                                "customerCount", numero(),
                                "observation", texto("Qué está pasando con este grupo, una frase"))),
                        "campaign", objeto(
                                "nombre", texto(),
                                "segmento", texto("Nombre exacto del segmento al que dirigirla"),
                                "canal", unoDe("CORREO", "SMS", "WHATSAPP", "PUSH"),
                                "asunto", texto(),
                                "cuerpo", texto(),
                                "rationale", texto("Por qué esta campaña y no otra"))),
                datos::segmentador,
                "Analiza los segmentos y di qué está pasando con cada uno. Después propón UNA sola campaña: "
                        + "la que más ingresos pueda recuperar o generar ahora mismo. Elige el segmento y el canal con "
                        + "criterio y explica por qué esa y no otra."));

        EsquemaJson pedido = objeto(
                "reasoning", texto("Cómo has llegado a estas cantidades"),
                "lineas", lista(objeto(
                        "sku", texto(),
                        "productName", texto(),
                        "currentStock", numero(),
                        "suggestedOrder", numero(),
                        "motivo", texto())));

        definir(new Definicion(
                ClaveDeAgente.INVENTARIO,
                "Predice el consumo del fin de semana y arma el pedido a proveedor.",
                TipoDePropuesta.ORDEN_COMPRA,
                false,
                pedido,
                datos::inventario,
                "Estima el consumo de los próximos eventos y propón qué reponer. Prioriza lo que está por "
                        + "debajo del mínimo y lo que más se vende. No pidas de todo: propón solo lo que hace falta "
                        + "para cubrir los eventos que vienen, con un margen razonable."));

        // La instrucción PROHÍBE estimar: ese es todo el experimento frente al
        // agente de arriba, que calcula a ojo desde datos crudos.
        definir(new Definicion(
                ClaveDeAgente.INVENTARIO_ML,
                "El modelo proyecta el consumo; el agente solo arma el pedido y lo explica.",
                TipoDePropuesta.ORDEN_COMPRA,
                false,
                pedido,
                datos::inventarioConModelo,
                "El consumo proyectado de cada producto ya viene calculado. NO lo recalcules ni lo ajustes "
                        + "a ojo: úsalo tal cual. Tu trabajo es decidir cuánto pedir de cada producto para cubrir esa "
                        + "proyección contando con lo que ya hay en existencias, y explicar el pedido en dos frases. "
                        + "Si un producto está cubierto, no lo pidas."));

        definir(new Definicion(
                ClaveDeAgente.PRECIOS,
                "Compara el ritmo de venta con el histórico y sugiere ajustes.",
                TipoDePropuesta.CAMBIO_PRECIO,
                false,
                objeto(
                        "assessment", texto("Cómo va la venta, en dos o tres frases"),
                        "actions", lista(objeto(
                                // Sin el evento, «Early Bird» existe en todas las
                                // noches y el cambio se aplicaría a todas a la vez.
                                "eventTitle", texto("Título exacto del evento al que pertenece esta entrada"),
                                "ticketTypeName", texto(),
                                "currentPriceCents", numero(),
                                "suggestedPriceCents", numero(),
                                "rationale", texto()))),
                datos::precios,
                "Valora cómo va la venta de cada evento comparándola con el histórico y con los días que "
                        + "faltan. Propón cambios de precio solo donde haya motivo claro: si un evento va bien, dilo y "
                        + "no propongas nada. Los precios van en céntimos enteros."));

        definir(new Definicion(
                ClaveDeAgente.INFORMES,
                "Cierra la última noche con números y conclusiones.",
                TipoDePropuesta.INFORME,
                true,
                objeto(
                        "headline", texto(),
                        "resumen", texto("Dos párrafos sobre cómo fue la noche"),
                        "metrics", lista(objeto("label", texto(), "value", texto())),
                        "recommendations", lista(texto(), "Entre 2 y 4 acciones concretas")),
                datos::informe,
                "Escribe el informe de cierre de la noche. Incluye las métricas que importan, señala "
                        + "lo que salió bien y lo que no, y termina con acciones concretas para el próximo evento. "
                        + "Compara entradas vendidas con personas que entraron y comenta el absentismo si es llamativo. "
                        + "Usa la comparación con noches parecidas para decir si fue buena o mala, no la impresión. "
                        + "Si una barra, una caja o un promotor destaca para bien o para mal, nómbralo. "
                        + "Si hay incidencias o notas de la sala, úsalas para explicar las cifras raras."));

        // Propone noches enteras. Va al motor de prosa porque lo que produce lo
        // lee el público (título y descripción), y los números que lleva —precios
        // y cupos— salen de las plantillas reales del histórico, no de su cabeza.
        definir(new Definicion(
                ClaveDeAgente.PROGRAMADOR,
                "Estudia el histórico y los huecos del calendario y propone noches completas.",
                TipoDePropuesta.EVENTO,
                true,
                objeto(
                        "razonamiento", texto("Qué has visto en el histórico y en el calendario, en dos o tres frases"),
                        "noches", lista(objeto(
                                "titulo", texto("Nombre de la noche, como iría en el flyer"),
                                "fecha", texto("Una de las fechas libres, en formato AAAA-MM-DD"),
                                "puertas", texto("Hora de apertura, HH:MM en 24 horas"),
                                "cierre", texto("Hora de cierre, HH:MM en 24 horas; de madrugada es el día siguiente"),
                                "descripcion", texto("Dos o tres frases para la web"),
                                "vestimenta", texto(),
                                "edadMinima", numero(),
                                "artistas", lista(texto(), "Nombres exactos del catálogo; el primero es el cabeza de cartel"),
                                "entradas", lista(objeto(
                                        "nombre", texto(),
                                        "descripcion", texto("Qué incluye o hasta qué hora vale"),
                                        "precioCentimos", numero(),
                                        "cantidad", numero(),
                                        "maximoPorPedido", numero())),
                                "motivo", texto("Por qué esta noche en esta fecha, con las cifras del histórico")),
                                "Entre una y tres noches")),
                datos::programacion,
                "Propón entre una y tres noches nuevas para las fechas libres. Básate en lo que ha funcionado: "
                        + "el día de la semana, el género y los artistas que más llenan, y aprovecha las vísperas de "
                        + "feriado y las fechas especiales. Usa solo artistas del catálogo con su nombre exacto. "
                        + "Títulos cortos al estilo de la casa. Pon entradas parecidas a las plantillas reales, con "
                        + "precios redondos en soles enteros escritos en céntimos (2500 es S/ 25), y que la suma de "
                        + "cupos no pase el aforo. Fíjate en qué día de la semana cae cada fecha. No repitas el título "
                        + "de una noche que ya está programada."));
    }

    private void definir(Definicion d) {
        definiciones.put(d.clave(), d);
    }

    public List<Definicion> todos() {
        return List.copyOf(definiciones.values());
    }

    public Optional<Definicion> definicion(ClaveDeAgente clave) {
        return Optional.ofNullable(definiciones.get(clave));
    }

    /**
     * Lanza un agente y deja su propuesta pendiente.
     *
     * @throws AgenteSinDatos si no hay nada sobre lo que trabajar
     * @throws IaNoDisponible si el proveedor no contesta
     * @throws IaSinFormato si el modelo no da un JSON válido en tres intentos
     */
    public Resultado ejecutar(ClaveDeAgente clave, Disparador disparador, String lanzadoPorId) {
        return ejecutar(clave, disparador, lanzadoPorId, null);
    }

    /**
     * Igual, con otros datos en lugar de los que el agente recoge por su cuenta.
     *
     * <p>Es lo que permite pedir el informe de <b>una</b> noche concreta desde
     * su ficha, en vez de siempre el de la última: el agente, sus instrucciones
     * y su esquema son los mismos; solo cambia sobre qué trabaja.
     *
     * @param datos nulo para usar el recolector del agente
     */
    public Resultado ejecutar(
            ClaveDeAgente clave, Disparador disparador, String lanzadoPorId, Supplier<Optional<String>> datos) {
        Definicion d = definicion(clave).orElseThrow(() ->
                new AgenteSinDatos("No existe el agente «" + clave.etiqueta() + "»."));
        Supplier<Optional<String>> recolector = datos == null ? d.recolector() : datos;

        String contexto;
        try {
            contexto = recolector.get().orElseThrow(() ->
                    new AgenteSinDatos("No hay datos suficientes para ejecutar «" + clave.nombre() + "»."));
        } catch (IllegalStateException e) {
            // El modelo de demanda sin histórico suficiente: lo dice tal cual.
            throw new AgenteSinDatos(e.getMessage());
        }

        Motor motor = d.prosa() ? motores.prosa() : motores.numeros();
        String ejecucion = registro.abrirEjecucion(
                clave, disparador, lanzadoPorId, Map.of("context", contexto), motor.modelo());

        try {
            var r = modelo.generarEstructurado(
                    motor,
                    ESTILO_DE_LA_CASA,
                    d.instruccion() + "\n\nEstos son los datos reales:\n\n" + contexto,
                    clave.etiqueta().replace('-', '_'),
                    d.esquema());

            String propuesta = registro.proponer(ejecucion, d.tipoDePropuesta(), r.valor());
            registro.completarEjecucion(
                    ejecucion, r.valor(), r.tokensEntrada(), r.tokensSalida(),
                    motores.costeCentimos(r.tokensEntrada(), r.tokensSalida()), r.intentos());

            return new Resultado(ejecucion, propuesta,
                    clave.nombre() + " ha dejado una propuesta pendiente de aprobación.");
        } catch (RuntimeException e) {
            registro.fallarEjecucion(ejecucion, e.getMessage());
            throw e;
        }
    }
}
