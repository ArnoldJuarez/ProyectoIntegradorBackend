package pe.ssiomama.backend.clientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Segmentos y campañas contra los datos reales.
 *
 * <p>Toda la clase va en una transacción que se deshace al terminar.
 *
 * <p>Las comprobaciones <b>no fijan números a mano</b>: cuántos clientes caen en
 * cada segmento depende del día en que se ejecute —«cumplen este mes» cambia
 * cada mes— así que lo esperado se deduce de los mismos datos. Es la lección de
 * la tanda de pruebas que se puso en rojo sola al pasar el calendario.
 */
@SpringBootTest
@Transactional
class CrmTest {

    @Autowired
    private Crm crm;

    @Autowired
    private SegmentoRepository segmentos;

    @Autowired
    private CampaniaRepository campanias;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    // ── Apoyo ─────────────────────────────────────────────────────

    private Segmento porNombre(String nombre) {
        return segmentos.findAllByOrderByNombreAsc().stream()
                .filter(s -> s.getNombre().equals(nombre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("El seed no tiene el segmento " + nombre));
    }

    /** Crea un segmento con la regla que se le pase, para probar casos concretos. */
    private Long segmentoCon(String nombre, String reglaJson) {
        Long id = pe.ssiomama.backend.NocheDePrueba.reservarId(jdbc, "Segmento");
        jdbc.update(
                """
                insert into "Segmento" (id, nombre, descripcion, "reglasJson", "esDinamico", "actualizadoEn")
                values (?, ?, 'Creado por una prueba.', ?::jsonb, true, now())
                """,
                id,
                nombre,
                reglaJson);
        return id;
    }

    private void comoUnaPeticionNueva() {
        em.flush();
        em.clear();
    }

    // ── Los segmentos del seed ────────────────────────────────────

    @Test
    @DisplayName("los cuatro segmentos del seed se entienden")
    void losCuatroSeEntienden() {
        var evaluados = crm.evaluarTodos(Instant.now());

        assertThat(evaluados).hasSize(4);
        assertThat(evaluados).allSatisfy(e -> assertThat(e.regla())
                .as("«%s» tiene una regla que no se entiende", e.segmento().getNombre())
                .isPresent());
    }

    /**
     * La comprobación que de verdad importa: lo que devuelve el segmento
     * coincide con lo que dice la regla, contado aparte contra la base.
     */
    @Test
    @DisplayName("VIP trae exactamente a quien pasa del umbral")
    void vipCuadraConLaBase() {
        var vip = crm.evaluar(porNombre("VIP").getId(), Instant.now()).orElseThrow();
        int umbral = vip.regla().orElseThrow().gastoMinimoCentimos();

        long segunLaBase = jdbc.queryForObject(
                "select count(*) from \"Cliente\" where \"gastoTotalCentimos\" >= ?",
                Long.class,
                umbral);

        assertThat(vip.miembros()).hasSize((int) segunLaBase);
        assertThat(vip.miembros())
                .allSatisfy(c -> assertThat(c.getGastadoCentimos()).isGreaterThanOrEqualTo(umbral));
    }

    @Test
    @DisplayName("cumpleanos del mes trae solo a los que cumplen este mes")
    void cumpleanosDelMes() {
        Instant ahora = Instant.now();
        var cumples = crm.evaluar(porNombre("Cumpleaños del mes").getId(), ahora).orElseThrow();

        int mesDeHoy = java.time.LocalDate.ofInstant(ahora, java.time.ZoneId.of("America/Lima"))
                .getMonthValue();

        assertThat(cumples.miembros()).allSatisfy(c -> {
            assertThat(c.getFechaDeNacimiento()).isNotNull();
            assertThat(c.getFechaDeNacimiento().getMonthValue()).isEqualTo(mesDeHoy);
        });
    }

    /**
     * «Dormidos» solo incluye a quien además dio permiso: el segmento existe
     * para escribirle, y sin consentimiento no se le puede escribir.
     */
    @Test
    @DisplayName("dormidos excluye a quien no dio permiso")
    void dormidosRespetaElPermiso() {
        var dormidos = crm.evaluar(porNombre("Dormidos 60 días").getId(), Instant.now())
                .orElseThrow();

        assertThat(dormidos.miembros()).allSatisfy(c -> assertThat(c.isAceptaMarketing()).isTrue());
    }

    /**
     * Es el rasgo que define a un segmento: no es una lista congelada. Al mover
     * el instante de evaluación, la misma regla devuelve otra gente sin que
     * nadie recalcule nada.
     */
    @Test
    @DisplayName("la misma regla da distinto segun cuando se evalue")
    void seRecalculaSolo() {
        Long id = segmentoCon("Dormidos de prueba", "{\"type\":\"inactive\",\"daysSinceLastVisit\":30}");
        comoUnaPeticionNueva();

        Instant hoy = Instant.now();
        int ahora = crm.evaluar(id, hoy).orElseThrow().miembros().size();
        int dentroDeUnAno = crm.evaluar(id, hoy.plus(Duration.ofDays(365))).orElseThrow()
                .miembros()
                .size();

        // Dentro de un año, todo el que tenga una visita registrada llevará más
        // de 30 días sin venir: el segmento solo puede crecer.
        assertThat(dentroDeUnAno).isGreaterThanOrEqualTo(ahora);
    }

    /**
     * El tamaño del segmento y a cuánta gente se le puede escribir no son lo
     * mismo. Enseñar solo el total promete un alcance que no existe.
     */
    @Test
    @DisplayName("contactables nunca pasa del total del segmento")
    void contactablesEsSubconjunto() {
        for (var e : crm.evaluarTodos(Instant.now())) {
            assertThat(e.contactables())
                    .as("«%s»", e.segmento().getNombre())
                    .isLessThanOrEqualTo(e.miembros().size());
        }
    }

    /**
     * Una regla que no se entiende no puede tumbar la pantalla ni, mucho peor,
     * meter a toda la base en una campaña.
     */
    @Test
    @DisplayName("un segmento con la regla rota sale vacio, no revienta")
    void reglaRota() {
        Long id = segmentoCon("Regla inventada", "{\"type\":\"loQueSea\"}");
        comoUnaPeticionNueva();

        var evaluado = crm.evaluar(id, Instant.now()).orElseThrow();

        assertThat(evaluado.regla()).isEmpty();
        assertThat(evaluado.miembros()).isEmpty();
    }

    @Test
    @DisplayName("un json que ni siquiera es una regla tampoco revienta")
    void jsonBasura() {
        Long id = segmentoCon("Json raro", "{\"nada\":\"que ver\"}");
        comoUnaPeticionNueva();

        assertThat(crm.evaluar(id, Instant.now()).orElseThrow().miembros()).isEmpty();
    }

    // ── Campañas ──────────────────────────────────────────────────

    @Test
    @DisplayName("una campana nace en borrador y apunta al segmento")
    void redactarCampania() {
        var vip = porNombre("VIP");

        var campania = crm.redactar(
                "Aniversario para VIP",
                CanalDeCampania.CORREO,
                vip.getId(),
                "Te invitamos al aniversario",
                "Hola, este viernes celebramos nuestro aniversario.");
        comoUnaPeticionNueva();

        assertThat(campania.getEstado()).isEqualTo(EstadoDeCampania.BORRADOR);
        assertThat(campania.sePuedeTocar()).isTrue();
        // Los contadores se quedan a cero: nadie ha mandado nada.
        assertThat(campania.getEnviados()).isZero();
        assertThat(campania.getEnviadaEn()).isNull();

        var guardada = campanias.buscarConSegmento(campania.getId()).orElseThrow();
        assertThat(guardada.getSegmento().getId()).isEqualTo(vip.getId());
    }

    /** Un correo sin asunto llega como «(sin asunto)» y no lo abre nadie. */
    @Test
    @DisplayName("una campana por correo necesita asunto")
    void correoSinAsunto() {
        Long vip = porNombre("VIP").getId();

        assertThatThrownBy(() -> crm.redactar(
                        "Sin asunto", CanalDeCampania.CORREO, vip, "  ", "El cuerpo sí está."))
                .isInstanceOf(CrmRechazado.class)
                .hasMessageContaining("asunto");
    }

    @Test
    @DisplayName("por whatsapp no hace falta asunto")
    void whatsappSinAsunto() {
        Long vip = porNombre("VIP").getId();

        var campania = crm.redactar(
                "Aviso rápido", CanalDeCampania.WHATSAPP, vip, null, "Este viernes abrimos a las 8.");

        assertThat(campania.getAsunto()).isNull();
        assertThat(campania.getCanal()).isEqualTo(CanalDeCampania.WHATSAPP);
    }

    @Test
    @DisplayName("una campana en borrador se puede reescribir")
    void reescribir() {
        Long vip = porNombre("VIP").getId();
        var campania = crm.redactar(
                "Primera versión", CanalDeCampania.CORREO, vip, "Asunto viejo", "Texto viejo.");
        comoUnaPeticionNueva();

        var cambiada = crm.reescribir(
                campania.getId(),
                "Segunda versión",
                CanalDeCampania.SMS,
                null,
                "Texto nuevo, más corto.");

        assertThat(cambiada.getNombre()).isEqualTo("Segunda versión");
        assertThat(cambiada.getCanal()).isEqualTo(CanalDeCampania.SMS);
        assertThat(cambiada.getCuerpo()).isEqualTo("Texto nuevo, más corto.");
    }

    /**
     * Cancelar deja rastro de que se pensó escribir a alguien y se decidió que
     * no, que es lo que se pregunta al revisar por qué no salió una promoción.
     */
    @Test
    @DisplayName("cancelar no borra: marca")
    void cancelar() {
        Long vip = porNombre("VIP").getId();
        var campania = crm.redactar(
                "Se cancela", CanalDeCampania.CORREO, vip, "Asunto", "Texto.");
        Long id = campania.getId();
        comoUnaPeticionNueva();

        var cancelada = crm.cancelar(id);
        comoUnaPeticionNueva();

        assertThat(cancelada.getEstado()).isEqualTo(EstadoDeCampania.CANCELADO);
        assertThat(campanias.findById(id)).as("sigue en la base").isPresent();
    }

    @Test
    @DisplayName("una campana cancelada ya no se toca")
    void canceladaNoSeToca() {
        Long vip = porNombre("VIP").getId();
        var campania = crm.redactar("Se cancela", CanalDeCampania.CORREO, vip, "A", "B");
        Long id = campania.getId();
        comoUnaPeticionNueva();

        crm.cancelar(id);
        comoUnaPeticionNueva();

        assertThatThrownBy(() -> crm.cancelar(id))
                .isInstanceOf(CrmRechazado.class)
                .hasMessageContaining("cancelada");

        assertThatThrownBy(() ->
                        crm.reescribir(id, "Otro", CanalDeCampania.CORREO, "A", "B"))
                .isInstanceOf(CrmRechazado.class);
    }

    @Test
    @DisplayName("un segmento que no existe se rechaza con su mensaje")
    void segmentoInventado() {
        assertThatThrownBy(() -> crm.redactar(
                        "Al aire", CanalDeCampania.CORREO, 999_999_999L, "A", "B"))
                .isInstanceOf(CrmRechazado.class)
                .hasMessageContaining("segmento no existe");
    }

    @Test
    @DisplayName("el listado trae las campanas con su segmento cargado")
    void listar() {
        Long vip = porNombre("VIP").getId();
        crm.redactar("Una campaña", CanalDeCampania.CORREO, vip, "Asunto", "Texto.");
        comoUnaPeticionNueva();

        var todas = crm.listarCampanias();

        assertThat(todas).isNotEmpty();
        // Tocar el segmento fuera de la consulta reventaría si no viniera cargado.
        assertThat(todas).allSatisfy(c -> assertThat(c.getSegmento().getNombre()).isNotBlank());
    }
}
