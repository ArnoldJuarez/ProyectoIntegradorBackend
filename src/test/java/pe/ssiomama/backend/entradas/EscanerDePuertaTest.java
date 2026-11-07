package pe.ssiomama.backend.entradas;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.auth.Usuario;
import pe.ssiomama.backend.auth.UsuarioRepository;
import pe.ssiomama.backend.entradas.ResultadoDeEscaneo.MotivoDeRechazo;
import pe.ssiomama.backend.eventos.EventoRepository;

/**
 * La decisión de la puerta, contra los datos reales de la base.
 *
 * <h2>Por qué la hora es un parámetro y no {@code Instant.now()}</h2>
 *
 * <p>Porque si no, este archivo solo se podría ejecutar la noche de un evento.
 * El escáner recibe el instante desde fuera, así que aquí se le puede pasar la
 * madrugada del aniversario y comprobar de verdad que una entrada pasa — y
 * pasarle otra hora para comprobar que la misma entrada no pasa.
 *
 * <p>Toda la clase va en una transacción que se deshace al terminar: los
 * escaneos que se registran aquí no quedan en la base.
 */
@SpringBootTest
@Transactional
class EscanerDePuertaTest {

    /** La noche que ya se celebró: es la única con entradas usadas de verdad. */
    private static final String NOCHE_PASADA = "aniversario-ssiomama";

    /** Una noche futura, para el rechazo por fecha. */
    private static final String OTRA_NOCHE = "viernes-urbano";

    @Autowired
    private EscanerDePuerta escaner;

    @Autowired
    private EntradaRepository entradas;

    @Autowired
    private EscaneoRepository escaneos;

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private FirmaDeEntrada firma;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    // ── Apoyo ─────────────────────────────────────────────────────

    /**
     * El id de una entrada de esa noche en ese estado.
     *
     * <p>Se busca con SQL en vez de con el repositorio para no añadir consultas
     * a la API de producción que solo usarían los tests. El {@code ::text} en el
     * estado evita tener que enlazar el tipo enum de PostgreSQL.
     */
    private Long unaEntrada(String slug, EstadoDeEntrada estado) {
        return jdbc.queryForObject(
                """
                select e.id from "Entrada" e
                join "Evento" v on v.id = e."eventoId"
                where v.slug = ? and e.estado::text = ?
                order by e.id limit 1
                """,
                Long.class,
                slug,
                estado.name());
    }

    /** Un momento cualquiera dentro de la noche de ese evento. */
    private Instant duranteLaNoche(String slug) {
        return eventos.buscarPorSlugConDetalle(slug)
                .orElseThrow()
                .getEmpiezaA()
                .plus(Duration.ofHours(1));
    }

    private Usuario portero() {
        return usuarios.findByEmailIgnoreCase("puerta@ssiomama.test").orElseThrow();
    }

    /**
     * Vacía el contexto de persistencia para simular una petición nueva.
     *
     * <p>Hace falta porque {@code Entrada.escaneo} es el lado inverso de la
     * relación: al guardar el acceso, JPA <b>no</b> rellena esa propiedad en la
     * entrada que ya está cargada en memoria. En producción no se nota —cada
     * petición trae su propio contexto y relee—, pero dos escaneos seguidos
     * dentro del mismo test compartirían el objeto y el segundo no vería el
     * primero.
     */
    private void comoUnaPeticionNueva() {
        em.flush();
        em.clear();
    }

    // ── La firma ──────────────────────────────────────────────────

    @Test
    @DisplayName("un codigo inventado es una entrada falsa")
    void codigoInventadoEsFalso() {
        var r = escaner.escanear("lo-que-sea.firmafalsa", portero(), Instant.now());

        assertThat(r.ok()).isFalse();
        assertThat(r.motivo()).isEqualTo(MotivoDeRechazo.FALSA);
        // Sin tocar la base: la firma se descarta antes de consultar nada.
        assertThat(r.titular()).isNull();
    }

    /**
     * La diferencia con el anterior es importante: aquí la firma <b>sí</b> es
     * válida, lo que significa que quien lo generó tenía el secreto. Que no
     * exista la entrada es entonces un problema de datos, no un intento de
     * colarse, y merece un motivo distinto.
     */
    @Test
    @DisplayName("firma buena de una entrada que no existe: no es falsa, es inexistente")
    void firmaBuenaSinEntrada() {
        var r = escaner.escanear(firma.codificar(999_999_999L), portero(), Instant.now());

        assertThat(r.motivo()).isEqualTo(MotivoDeRechazo.NO_EXISTE);
    }

    // ── Los rechazos ──────────────────────────────────────────────

    @Test
    @DisplayName("una entrada anulada no pasa")
    void anuladaNoPasa() {
        Long id = unaEntrada(NOCHE_PASADA, EstadoDeEntrada.VALIDA);
        // Se anula por SQL porque la entidad no expone ese cambio: anular es
        // cosa del panel, no de la puerta.
        jdbc.update("update \"Entrada\" set estado = 'ANULADA'::\"EstadoEntrada\" where id = ?", id);
        comoUnaPeticionNueva();

        var r = escaner.escanear(firma.codificar(id), portero(), duranteLaNoche(NOCHE_PASADA));

        assertThat(r.ok()).isFalse();
        assertThat(r.motivo()).isEqualTo(MotivoDeRechazo.ANULADA);
        // Aunque no pase, se enseña de quién es: es lo primero que se pregunta.
        assertThat(r.titular()).isNotBlank();
    }

    /**
     * El rechazo más frecuente de una puerta. La entrada es buena, está sin
     * usar, y aun así no pasa: es de otro día.
     */
    @Test
    @DisplayName("una entrada de otra noche no pasa, y lo dice con el nombre del evento")
    void deOtraNocheNoPasa() {
        Long id = unaEntrada(OTRA_NOCHE, EstadoDeEntrada.VALIDA);

        var r = escaner.escanear(firma.codificar(id), portero(), duranteLaNoche(NOCHE_PASADA));

        assertThat(r.motivo()).isEqualTo(MotivoDeRechazo.OTRA_FECHA);
        assertThat(r.mensaje()).contains(r.evento());
    }

    @Test
    @DisplayName("la ventana se abre horas antes de empezar, no al empezar")
    void seAbreAntesDeEmpezar() {
        Long id = unaEntrada(NOCHE_PASADA, EstadoDeEntrada.VALIDA);
        Instant empieza = eventos.buscarPorSlugConDetalle(NOCHE_PASADA).orElseThrow().getEmpiezaA();

        // Dos horas antes de empezar ya se puede entrar: la cola se forma antes.
        var pronto = escaner.escanear(
                firma.codificar(id), portero(), empieza.minus(Duration.ofHours(2)));
        assertThat(pronto.ok()).isTrue();

        comoUnaPeticionNueva();

        // Pero un día antes, no.
        Long otra = unaEntrada(OTRA_NOCHE, EstadoDeEntrada.VALIDA);
        var demasiadoPronto = escaner.escanear(
                firma.codificar(otra), portero(), empieza.minus(Duration.ofDays(1)));
        assertThat(demasiadoPronto.motivo()).isEqualTo(MotivoDeRechazo.OTRA_FECHA);
    }

    /**
     * Las 22 entradas usadas del aniversario tienen su acceso en la base. Al
     * escanear una de ellas hay que decir la hora y quién la escaneó: «ya usada»
     * a secas es lo que hace que la discusión en la puerta no termine.
     */
    @Test
    @DisplayName("una entrada ya usada dice a que hora entro y quien la escaneo")
    void yaUsadaDiceCuandoYQuien() {
        Long id = unaEntrada(NOCHE_PASADA, EstadoDeEntrada.USADA);
        var previo = entradas.buscarPorIdParaLaPuerta(id).orElseThrow().getEscaneo();

        var r = escaner.escanear(firma.codificar(id), portero(), duranteLaNoche(NOCHE_PASADA));

        assertThat(r.motivo()).isEqualTo(MotivoDeRechazo.REPETIDA);
        assertThat(r.mensaje())
                .startsWith("Ya entró a las ")
                .contains(previo.getEscaneadoPor().getNombre());
    }

    // ── El caso bueno ─────────────────────────────────────────────

    @Test
    @DisplayName("una entrada valida pasa y queda registrada")
    void laValidaPasa() {
        Long id = unaEntrada(NOCHE_PASADA, EstadoDeEntrada.VALIDA);
        Long eventoId = eventos.buscarPorSlugConDetalle(NOCHE_PASADA).orElseThrow().getId();
        long antes = escaneos.countByEventoId(eventoId);
        Instant momento = duranteLaNoche(NOCHE_PASADA);

        var r = escaner.escanear(firma.codificar(id), portero(), momento);

        assertThat(r.ok()).isTrue();
        assertThat(r.motivo()).isNull();
        assertThat(r.titular()).isNotBlank();
        assertThat(r.tipoDeEntrada()).isNotBlank();
        assertThat(r.escaneadoEn()).isEqualTo(momento);

        comoUnaPeticionNueva();

        // El aforo subió en uno y la entrada quedó marcada.
        assertThat(escaneos.countByEventoId(eventoId)).isEqualTo(antes + 1);
        assertThat(entradas.findById(id).orElseThrow().getEstado())
                .isEqualTo(EstadoDeEntrada.USADA);
    }

    /** Por dónde entró la gente es justo lo que la tabla vieja no guardaba. */
    @Test
    @DisplayName("el acceso anota por que puerta entro")
    void anotaLaPuerta() {
        Long id = unaEntrada(NOCHE_PASADA, EstadoDeEntrada.VALIDA);

        escaner.escanear(firma.codificar(id), portero(), duranteLaNoche(NOCHE_PASADA));
        comoUnaPeticionNueva();

        var acceso = entradas.buscarPorIdParaLaPuerta(id).orElseThrow().getEscaneo();
        assertThat(acceso.getPuerta()).isNotNull();
        // Nunca una salida de emergencia: por ahí se sale, no se entra.
        assertThat(acceso.getPuerta().isEsSalidaEmergencia()).isFalse();
        assertThat(acceso.getPuerta().isActivo()).isTrue();
    }

    /**
     * El escaneo repetido inmediato: la misma entrada, dos veces seguidas. Es lo
     * que pasa cuando alguien pasa el QR a un amigo nada más entrar.
     */
    @Test
    @DisplayName("escanear dos veces la misma entrada: la segunda no pasa")
    void laSegundaVezNoPasa() {
        Long id = unaEntrada(NOCHE_PASADA, EstadoDeEntrada.VALIDA);
        String codigo = firma.codificar(id);
        Instant momento = duranteLaNoche(NOCHE_PASADA);

        assertThat(escaner.escanear(codigo, portero(), momento).ok()).isTrue();
        comoUnaPeticionNueva();

        var segunda = escaner.escanear(codigo, portero(), momento.plus(Duration.ofMinutes(3)));

        assertThat(segunda.ok()).isFalse();
        assertThat(segunda.motivo()).isEqualTo(MotivoDeRechazo.REPETIDA);
        assertThat(segunda.mensaje()).contains(portero().getNombre());
    }
}
