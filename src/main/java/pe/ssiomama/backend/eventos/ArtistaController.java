package pe.ssiomama.backend.eventos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.ssiomama.backend.eventos.GestionDeArtistas.DatosDeArtista;

/**
 * El catálogo de artistas desde el panel: verlo, darlos de alta, editarlos,
 * ponerles foto y quitar los que nunca tocaron.
 *
 * <p>Va en su propio prefijo y no bajo {@code /api/eventos}: ahí
 * {@code /api/eventos/artistas} competiría con {@code /api/eventos/{slug}}, y una
 * noche titulada «Artistas» quedaría inalcanzable.
 */
@RestController
@RequestMapping("/api/artistas")
public class ArtistaController {

    private final ArtistaRepository artistas;
    private final GestionDeArtistas gestion;
    private final Medios medios;
    private final JdbcClient sql;

    public ArtistaController(
            ArtistaRepository artistas, GestionDeArtistas gestion, Medios medios, JdbcClient sql) {
        this.artistas = artistas;
        this.gestion = gestion;
        this.medios = medios;
        this.sql = sql;
    }

    public record FormularioDeArtista(
            @NotBlank(message = "Falta el nombre")
            @Size(min = 2, max = 80, message = "El nombre tiene entre 2 y 80 caracteres")
            String nombre,
            @Size(max = 60, message = "El género es demasiado largo") String genero,
            @Size(max = 120, message = "Ese Instagram es demasiado largo") String instagram,
            @Size(max = 1000, message = "La biografía es demasiado larga") String bio) {}

    /**
     * Un artista con lo que ha hecho en la casa.
     *
     * @param noches en cuántos carteles ha estado. Con más de cero no se puede borrar.
     */
    public record ArtistaEnCatalogo(
            String id,
            String nombre,
            String genero,
            String imagen,
            String instagram,
            String bio,
            long noches,
            Instant ultimaNoche) {}

    public record FotoGuardada(String imagen, long pesoKb) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<ArtistaEnCatalogo> catalogo() {
        record Historial(long noches, Instant ultima) {}
        // Las fechas de la base son UTC sin zona (ver Evento).
        Map<String, Historial> historial = sql.sql("""
                select ea."artistaId", count(*) as noches, max(e."empiezaEn") as ultima
                from "EventoArtista" ea join "Evento" e on e.id = ea."eventoId"
                group by ea."artistaId"
                """)
                .query((rs, n) -> Map.entry(rs.getString(1), new Historial(
                        rs.getLong("noches"),
                        rs.getObject("ultima", LocalDateTime.class).toInstant(ZoneOffset.UTC))))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return artistas.findAllByOrderByNombreAsc().stream()
                .map(a -> {
                    Historial h = historial.getOrDefault(a.getId(), new Historial(0, null));
                    return new ArtistaEnCatalogo(a.getId(), a.getNombre(), a.getGenero(), a.getImagen(),
                            a.getInstagram(), a.getBio(), h.noches(), h.ultima());
                })
                .toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ArtistaEnCatalogo> crear(@Valid @RequestBody FormularioDeArtista f) {
        Artista a = gestion.crear(aDatos(f));
        return ResponseEntity.status(HttpStatus.CREATED).body(enCatalogo(a));
    }

    @PutMapping("/{id}")
    @Transactional
    public ArtistaEnCatalogo editar(@PathVariable String id, @Valid @RequestBody FormularioDeArtista f) {
        return enCatalogo(gestion.editar(id, aDatos(f)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> borrar(@PathVariable String id) {
        medios.borrarSiEsNuestro(gestion.borrar(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/foto")
    @Transactional
    public FotoGuardada subirFoto(@PathVariable String id, @RequestParam("archivo") MultipartFile archivo)
            throws IOException {
        Artista artista = artistas.findById(id)
                .orElseThrow(() -> new EventoRechazado("Ese artista no existe."));
        Medios.Guardado guardado = medios.guardarImagen("artista-" + artista.getNombre(), archivo);
        String anterior = artista.getImagen();
        artista.cambiarImagen(guardado.ruta());
        medios.borrarSiEsNuestro(anterior);
        return new FotoGuardada(artista.getImagen(), guardado.pesoKb());
    }

    @DeleteMapping("/{id}/foto")
    @Transactional
    public ResponseEntity<Void> quitarFoto(@PathVariable String id) {
        Artista artista = artistas.findById(id)
                .orElseThrow(() -> new EventoRechazado("Ese artista no existe."));
        String anterior = artista.getImagen();
        artista.cambiarImagen(null);
        medios.borrarSiEsNuestro(anterior);
        return ResponseEntity.noContent().build();
    }

    private static DatosDeArtista aDatos(FormularioDeArtista f) {
        return new DatosDeArtista(f.nombre(), f.genero(), f.instagram(), f.bio());
    }

    /** Recién creado o editado: el historial se lee de la base para no mentir. */
    private ArtistaEnCatalogo enCatalogo(Artista a) {
        long noches = sql.sql("select count(*) from \"EventoArtista\" where \"artistaId\" = :id")
                .param("id", a.getId())
                .query(Long.class)
                .single();
        return new ArtistaEnCatalogo(a.getId(), a.getNombre(), a.getGenero(), a.getImagen(),
                a.getInstagram(), a.getBio(), noches, null);
    }
}
