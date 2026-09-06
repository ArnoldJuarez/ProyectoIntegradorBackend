package pe.ssiomama.backend.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.ssiomama.backend.eventos.GestionDeEventos.DatosDeNoche;
import pe.ssiomama.backend.eventos.GestionDeEventos.DatosDeTipo;

/**
 * Programar noches, contra los datos reales.
 *
 * <p>Toda la clase va en una transacción que se deshace al terminar: las noches
 * que se crean aquí no quedan en la base.
 */
@SpringBootTest
@Transactional
class GestionDeEventosTest {

    /** Una noche publicada con 24 entradas vendidas. */
    private static final String CON_ENTRADAS = "viernes-urbano";

    @Autowired
    private GestionDeEventos gestion;

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private TipoDeEntradaRepository tipos;

    @Autowired
    private SalaRepository salas;

    @Autowired
    private ActuacionRepository cartel;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    // ── Apoyo ─────────────────────────────────────────────────────

    private String salaPrincipal() {
        return salas.findAllByOrderByAforoDesc().getFirst().getId();
    }

    private List<String> dosArtistas() {
        return jdbc.queryForList(
                "select id from \"Artista\" order by nombre limit 2", String.class);
    }

    /** Un horario válido cualquiera, en el futuro. */
    private Instant puertas() {
        return Instant.parse("2027-03-05T01:00:00Z");
    }

    private DatosDeNoche noche(String titulo) {
        return new DatosDeNoche(
                titulo,
                "Una noche de prueba.",
                salaPrincipal(),
                puertas(),
                puertas().plus(Duration.ofHours(6)),
                18,
                "Casual",
                EstadoDeEvento.BORRADOR,
                null,
                List.of());
    }

    private DatosDeTipo tipo(String nombre, int cantidad) {
        return new DatosDeTipo(nombre, "Entrada de prueba.", 2500, cantidad, 6, 1, true);
    }

    private void comoUnaPeticionNueva() {
        em.flush();
        em.clear();
    }

    /** El total ofertado de una noche, para las comprobaciones de aforo. */
    private int ofertadasDe(String slug) {
        return jdbc.queryForObject(
                """
                select coalesce(sum(t.cantidad), 0) from "TipoEntrada" t
                join "Evento" v on v.id = t."eventoId" where v.slug = ?
                """,
                Integer.class,
                slug);
    }

    // ── Crear ─────────────────────────────────────────────────────

    @Test
    @DisplayName("crear una noche genera su slug a partir del titulo")
    void crearGeneraSlug() {
        var creada = gestion.crear(noche("Noche de Salsa Brava"));

        assertThat(creada.getSlug()).isEqualTo("noche-de-salsa-brava");
        assertThat(creada.getTitulo()).isEqualTo("Noche de Salsa Brava");
        assertThat(creada.getEstado()).isEqualTo(EstadoDeEvento.BORRADOR);
        // Un borrador no está publicado, así que no tiene fecha de publicación.
        assertThat(creada.getPublicadoEn()).isNull();
        // empiezaA se iguala a la apertura, como hacía el sistema anterior.
        assertThat(creada.getEmpiezaA()).isEqualTo(creada.getPuertasA());
    }

    @Test
    @DisplayName("dos noches con el mismo titulo no chocan de slug")
    void slugRepetido() {
        var primera = gestion.crear(noche("Noche de Salsa Brava"));
        comoUnaPeticionNueva();
        var segunda = gestion.crear(noche("Noche de Salsa Brava"));

        assertThat(primera.getSlug()).isEqualTo("noche-de-salsa-brava");
        assertThat(segunda.getSlug()).isEqualTo("noche-de-salsa-brava-2");
    }

    @Test
    @DisplayName("publicar al crear deja la fecha de publicacion puesta")
    void publicarAlCrear() {
        var datos = noche("Noche Publicada");
        var publicada = gestion.crear(new DatosDeNoche(
                datos.titulo(), datos.descripcion(), datos.salaId(), datos.puertasA(),
                datos.terminaA(), datos.edadMinima(), datos.vestimenta(),
                EstadoDeEvento.PUBLICADO, null, List.of()));

        assertThat(publicada.getPublicadoEn()).isNotNull();
    }

    @Test
    @DisplayName("el primero del cartel es cabeza de cartel")
    void elCartelSeOrdena() {
        var artistas = dosArtistas();
        var datos = noche("Noche con Cartel");

        var creada = gestion.crear(new DatosDeNoche(
                datos.titulo(), datos.descripcion(), datos.salaId(), datos.puertasA(),
                datos.terminaA(), datos.edadMinima(), datos.vestimenta(), datos.estado(),
                null, artistas));
        comoUnaPeticionNueva();

        var actuaciones = cartel.findByEventoIdOrderByCabezaDeCartelDescArtistaNombreAsc(
                creada.getId());

        assertThat(actuaciones).hasSize(2);
        assertThat(actuaciones.getFirst().isCabezaDeCartel()).isTrue();
        assertThat(actuaciones.getFirst().getArtista().getId()).isEqualTo(artistas.getFirst());
        assertThat(actuaciones.get(1).isCabezaDeCartel()).isFalse();
    }

    // ── Horarios ──────────────────────────────────────────────────

    @Test
    @DisplayName("el cierre tiene que ser posterior a la apertura")
    void horarioAlReves() {
        var d = noche("Noche Imposible");
        assertThatThrownBy(() -> gestion.crear(new DatosDeNoche(
                        d.titulo(), d.descripcion(), d.salaId(),
                        puertas(), puertas().minus(Duration.ofHours(1)),
                        d.edadMinima(), d.vestimenta(), d.estado(), null, List.of())))
                .isInstanceOf(EventoRechazado.class)
                .hasMessageContaining("posterior");
    }

    @Test
    @DisplayName("una noche no dura mas de 24 horas")
    void nocheEterna() {
        var d = noche("Noche Eterna");
        assertThatThrownBy(() -> gestion.crear(new DatosDeNoche(
                        d.titulo(), d.descripcion(), d.salaId(),
                        puertas(), puertas().plus(Duration.ofHours(30)),
                        d.edadMinima(), d.vestimenta(), d.estado(), null, List.of())))
                .isInstanceOf(EventoRechazado.class)
                .hasMessageContaining("24 horas");
    }

    // ── Editar ────────────────────────────────────────────────────

    /**
     * El slug es la dirección pública. Cambiarlo al cambiar el título rompería
     * los enlaces ya compartidos en redes.
     */
    @Test
    @DisplayName("cambiar el titulo no cambia la direccion")
    void elSlugNoSeToca() {
        var creada = gestion.crear(noche("Titulo Viejo"));
        String slug = creada.getSlug();
        comoUnaPeticionNueva();

        var editada = gestion.editar(slug, noche("Titulo Completamente Nuevo"));

        assertThat(editada.getSlug()).isEqualTo(slug);
        assertThat(editada.getTitulo()).isEqualTo("Titulo Completamente Nuevo");
    }

    /**
     * La comprobación que más importa de este archivo: hay 24 personas con una
     * entrada para esa fecha en el móvil.
     */
    @Test
    @DisplayName("no se mueve la fecha de una noche con entradas emitidas")
    void moverConEntradasFuera() {
        var actual = eventos.findBySlug(CON_ENTRADAS).orElseThrow();
        long emitidas = jdbc.queryForObject(
                """
                select count(*) from "Entrada" e join "Evento" v on v.id = e."eventoId"
                where v.slug = ? and e.estado::text in ('VALIDA', 'USADA')
                """,
                Long.class,
                CON_ENTRADAS);
        assertThat(emitidas).as("el seed tiene entradas para esa noche").isPositive();

        assertThatThrownBy(() -> gestion.editar(
                        CON_ENTRADAS,
                        new DatosDeNoche(
                                actual.getTitulo(), actual.getDescripcion(),
                                actual.getSala().getId(),
                                actual.getPuertasA().plus(Duration.ofDays(7)),
                                actual.getTerminaA().plus(Duration.ofDays(7)),
                                actual.getEdadMinima(), actual.getVestimenta(),
                                actual.getEstado(), null, List.of())))
                .isInstanceOf(EventoRechazado.class)
                .hasMessageContaining("%d entradas emitidas".formatted(emitidas))
                .hasMessageContaining("Cancela");
    }

    @Test
    @DisplayName("sin entradas emitidas la fecha si se puede mover")
    void moverSinEntradas() {
        var creada = gestion.crear(noche("Noche Movible"));
        String slug = creada.getSlug();
        comoUnaPeticionNueva();

        Instant nueva = puertas().plus(Duration.ofDays(14));
        var d = noche("Noche Movible");
        var movida = gestion.editar(
                slug,
                new DatosDeNoche(
                        d.titulo(), d.descripcion(), d.salaId(),
                        nueva, nueva.plus(Duration.ofHours(6)),
                        d.edadMinima(), d.vestimenta(), d.estado(), null, List.of()));

        assertThat(movida.getPuertasA()).isEqualTo(nueva);
    }

    /**
     * Las noches del sistema anterior ya ofertan más entradas que el aforo de la
     * sala. Editarlas tiene que seguir siendo posible: la regla del aforo vive
     * donde crece la oferta, no aquí.
     */
    @Test
    @DisplayName("una noche pasada de aforo se sigue pudiendo editar")
    void editarPasadaDeAforo() {
        var actual = eventos.buscarPorSlugConDetalle(CON_ENTRADAS).orElseThrow();
        assertThat(ofertadasDe(CON_ENTRADAS))
                .as("el seed oferta más de lo que cabe, y por eso este test existe")
                .isGreaterThan(actual.getSala().getAforo());

        var editada = gestion.editar(
                CON_ENTRADAS,
                new DatosDeNoche(
                        "Viernes Urbano Retocado", actual.getDescripcion(),
                        actual.getSala().getId(), actual.getPuertasA(), actual.getTerminaA(),
                        actual.getEdadMinima(), actual.getVestimenta(), actual.getEstado(),
                        null, List.of()));

        assertThat(editada.getTitulo()).isEqualTo("Viernes Urbano Retocado");
    }

    @Test
    @DisplayName("el cartel se reemplaza entero al editar")
    void elCartelSeReemplaza() {
        var artistas = dosArtistas();
        var d = noche("Noche con Cambio de Cartel");

        var creada = gestion.crear(new DatosDeNoche(
                d.titulo(), d.descripcion(), d.salaId(), d.puertasA(), d.terminaA(),
                d.edadMinima(), d.vestimenta(), d.estado(), null, artistas));
        String slug = creada.getSlug();
        comoUnaPeticionNueva();

        // Se queda solo el segundo, que pasa a ser cabeza de cartel.
        gestion.editar(
                slug,
                new DatosDeNoche(
                        d.titulo(), d.descripcion(), d.salaId(), d.puertasA(), d.terminaA(),
                        d.edadMinima(), d.vestimenta(), d.estado(), null,
                        List.of(artistas.get(1))));
        comoUnaPeticionNueva();

        var actuaciones = cartel.findByEventoIdOrderByCabezaDeCartelDescArtistaNombreAsc(
                creada.getId());

        assertThat(actuaciones).hasSize(1);
        assertThat(actuaciones.getFirst().getArtista().getId()).isEqualTo(artistas.get(1));
        assertThat(actuaciones.getFirst().isCabezaDeCartel()).isTrue();
    }

    /**
     * Una noche puede abrir a las 20:00 y arrancar la música a las 21:00. Esa
     * hora de diferencia es un dato que alguien decidió, y editar el título no
     * puede borrarla.
     */
    @Test
    @DisplayName("editar conserva el hueco entre abrir puertas y empezar")
    void elHuecoSeConserva() {
        var antes = eventos.findBySlug(CON_ENTRADAS).orElseThrow();
        Duration hueco = Duration.between(antes.getPuertasA(), antes.getEmpiezaA());
        assertThat(hueco)
                .as("el seed tiene puertas y comienzo separados, y por eso este test existe")
                .isPositive();

        // Se edita sin tocar las fechas: solo el título.
        var editada = gestion.editar(
                CON_ENTRADAS,
                new DatosDeNoche(
                        "Viernes Urbano con Otro Nombre", antes.getDescripcion(),
                        antes.getSala().getId(), antes.getPuertasA(), antes.getTerminaA(),
                        antes.getEdadMinima(), antes.getVestimenta(), antes.getEstado(),
                        null, List.of()));

        assertThat(Duration.between(editada.getPuertasA(), editada.getEmpiezaA()))
                .isEqualTo(hueco);
    }

    @Test
    @DisplayName("mover la noche mueve tambien la hora de empezar")
    void moverArrastraElComienzo() {
        var creada = gestion.crear(noche("Noche que se Mueve"));
        String slug = creada.getSlug();
        comoUnaPeticionNueva();

        Instant dosHorasDespues = puertas().plus(Duration.ofHours(2));
        var d = noche("Noche que se Mueve");
        var movida = gestion.editar(
                slug,
                new DatosDeNoche(
                        d.titulo(), d.descripcion(), d.salaId(),
                        dosHorasDespues, dosHorasDespues.plus(Duration.ofHours(6)),
                        d.edadMinima(), d.vestimenta(), d.estado(), null, List.of()));

        // Al crearla el hueco era cero, así que las dos se mueven juntas.
        assertThat(movida.getPuertasA()).isEqualTo(dosHorasDespues);
        assertThat(movida.getEmpiezaA()).isEqualTo(dosHorasDespues);
    }

    // ── Tipos de entrada ──────────────────────────────────────────

    @Test
    @DisplayName("anadir un tipo a una noche nueva")
    void anadirTipo() {
        var creada = gestion.crear(noche("Noche con Entradas"));
        String slug = creada.getSlug();
        comoUnaPeticionNueva();

        var tipo = gestion.anadirTipo(slug, tipo("General", 100));

        assertThat(tipo.getNombre()).isEqualTo("General");
        assertThat(tipo.getCantidad()).isEqualTo(100);
        assertThat(tipo.getVendidas()).isZero();
        assertThat(tipo.getDisponibles()).isEqualTo(100);
        // La venta se corta al empezar la noche, que es el valor que no sorprende.
        assertThat(tipo.getVentaHasta()).isEqualTo(creada.getEmpiezaA());
    }

    /** Vender más entradas que el aforo legal no es un detalle de interfaz. */
    @Test
    @DisplayName("no se oferta mas de lo que cabe en la sala")
    void aforoDeLaSala() {
        var creada = gestion.crear(noche("Noche Pequeña"));
        String slug = creada.getSlug();
        int aforo = creada.getSala().getAforo();
        comoUnaPeticionNueva();

        assertThatThrownBy(() -> gestion.anadirTipo(slug, tipo("Demasiadas", aforo + 1)))
                .isInstanceOf(EventoRechazado.class)
                .hasMessageContaining("aforo");
    }

    @Test
    @DisplayName("el segundo tipo cuenta contra lo que ya hay ofertado")
    void aforoAcumulado() {
        var creada = gestion.crear(noche("Noche que se Llena"));
        String slug = creada.getSlug();
        int aforo = creada.getSala().getAforo();
        comoUnaPeticionNueva();

        gestion.anadirTipo(slug, tipo("Primera Tanda", aforo - 10));
        comoUnaPeticionNueva();

        assertThatThrownBy(() -> gestion.anadirTipo(slug, tipo("Segunda Tanda", 20)))
                .isInstanceOf(EventoRechazado.class)
                .hasMessageContaining("Ya hay %d".formatted(aforo - 10));
    }

    /** Esas entradas existen y su portador se presentará en la puerta. */
    @Test
    @DisplayName("el cupo no baja por debajo de lo ya vendido")
    void cupoPorDebajoDeLoVendido() {
        String tipoId = jdbc.queryForObject(
                """
                select t.id from "TipoEntrada" t join "Evento" v on v.id = t."eventoId"
                where v.slug = ? and t.vendidas > 0 order by t.vendidas desc limit 1
                """,
                String.class,
                CON_ENTRADAS);

        int vendidas = tipos.findById(tipoId).orElseThrow().getVendidas();

        assertThatThrownBy(() ->
                        gestion.editarTipo(tipoId, tipo("General", Math.max(vendidas - 1, 0))))
                .isInstanceOf(EventoRechazado.class)
                .hasMessageContaining("Ya se han vendido %d".formatted(vendidas));
    }

    @Test
    @DisplayName("editar un tipo no toca sus ventas")
    void editarNoTocaLasVentas() {
        var creada = gestion.crear(noche("Noche Editable"));
        comoUnaPeticionNueva();
        var tipo = gestion.anadirTipo(creada.getSlug(), tipo("General", 100));
        comoUnaPeticionNueva();

        var editado = gestion.editarTipo(
                tipo.getId(), new DatosDeTipo("VIP", "Otra cosa", 5000, 50, 4, 2, false));

        assertThat(editado.getNombre()).isEqualTo("VIP");
        assertThat(editado.getPrecioCentimos()).isEqualTo(5000);
        assertThat(editado.getCantidad()).isEqualTo(50);
        assertThat(editado.isActivo()).isFalse();
        assertThat(editado.getVendidas()).isZero();
    }

    @Test
    @DisplayName("un tipo sin ventas se borra de verdad")
    void borrarTipoSinVentas() {
        var creada = gestion.crear(noche("Noche con Tipo Borrable"));
        comoUnaPeticionNueva();
        var tipo = gestion.anadirTipo(creada.getSlug(), tipo("Se Borra", 50));
        String tipoId = tipo.getId();
        comoUnaPeticionNueva();

        boolean retirado = gestion.quitarTipo(tipoId);
        comoUnaPeticionNueva();

        assertThat(retirado).isFalse();
        assertThat(tipos.findById(tipoId)).isEmpty();
    }

    /**
     * Borrarlo dejaría huérfanas las entradas ya emitidas, que apuntan a él para
     * saber su nombre y sus consumiciones.
     */
    @Test
    @DisplayName("un tipo con ventas se retira, no se borra")
    void retirarTipoConVentas() {
        String tipoId = jdbc.queryForObject(
                """
                select t.id from "TipoEntrada" t join "Evento" v on v.id = t."eventoId"
                where v.slug = ? and t.vendidas > 0 limit 1
                """,
                String.class,
                CON_ENTRADAS);

        boolean retirado = gestion.quitarTipo(tipoId);
        comoUnaPeticionNueva();

        assertThat(retirado).isTrue();
        var sigueAhi = tipos.findById(tipoId).orElseThrow();
        assertThat(sigueAhi.isActivo()).isFalse();
        assertThat(sigueAhi.getVendidas()).isPositive();
    }

    // ── Lo que no existe ──────────────────────────────────────────

    @Test
    @DisplayName("una sala inventada se rechaza")
    void salaInventada() {
        var d = noche("Noche sin Sala");
        assertThatThrownBy(() -> gestion.crear(new DatosDeNoche(
                        d.titulo(), d.descripcion(), "no-existe", d.puertasA(), d.terminaA(),
                        d.edadMinima(), d.vestimenta(), d.estado(), null, List.of())))
                .isInstanceOf(EventoRechazado.class)
                .hasMessageContaining("sala no existe");
    }

    @Test
    @DisplayName("editar una noche que no existe se rechaza")
    void nocheInventada() {
        assertThatThrownBy(() -> gestion.editar("no-existe", noche("Da Igual")))
                .isInstanceOf(EventoRechazado.class)
                .hasMessageContaining("no existe");
    }
}
