package pe.ssiomama.backend.eventos;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El catálogo de artistas: quién puede salir en un cartel.
 *
 * <p>Antes solo se podía elegir entre los que ya estaban en la base, así que
 * programar una noche con un DJ nuevo era imposible desde el panel. Ahora se dan
 * de alta aquí, a mano o al aprobar una noche que propuso la IA con alguien que
 * no estaba.
 *
 * <p>Dos reglas: el nombre no se repite (dos «DJ Beto» en el selector y nadie
 * sabe cuál es cuál), y quien ya tocó no se borra (se perdería de los carteles
 * de las noches pasadas, que son historia).
 */
@Service
public class GestionDeArtistas {

    /**
     * El usuario de Instagram dentro de lo que se pegue: «@dj.beto»,
     * «instagram.com/dj.beto/» o «dj.beto». Instagram admite letras, números,
     * puntos y guiones bajos, hasta 30.
     */
    private static final Pattern INSTAGRAM =
            Pattern.compile("^(?:https?://)?(?:www\\.)?(?:instagram\\.com/)?@?([A-Za-z0-9._]{1,30})/?(?:\\?.*)?$");

    private final ArtistaRepository artistas;
    private final JdbcClient sql;

    public GestionDeArtistas(ArtistaRepository artistas, JdbcClient sql) {
        this.artistas = artistas;
        this.sql = sql;
    }

    public record DatosDeArtista(String nombre, String genero, String instagram, String bio) {}

    @Transactional
    public Artista crear(DatosDeArtista datos) {
        String nombre = datos.nombre().trim();
        artistas.findFirstByNombreIgnoreCase(nombre).ifPresent(otro -> {
            throw new EventoRechazado("Ya hay un artista llamado «" + otro.getNombre() + "».");
        });
        return artistas.save(Artista.crear(nombre, datos.genero(), instagram(datos.instagram()), datos.bio()));
    }

    @Transactional
    public Artista editar(String id, DatosDeArtista datos) {
        Artista artista = artistas.findById(id)
                .orElseThrow(() -> new EventoRechazado("Ese artista no existe."));
        String nombre = datos.nombre().trim();
        artistas.findFirstByNombreIgnoreCase(nombre)
                .filter(otro -> !otro.getId().equals(id))
                .ifPresent(otro -> {
                    throw new EventoRechazado("Ya hay otro artista llamado «" + otro.getNombre() + "».");
                });
        artista.actualizar(nombre, datos.genero(), instagram(datos.instagram()), datos.bio());
        return artista;
    }

    /**
     * Quita un artista del catálogo, solo si nunca ha estado en un cartel.
     *
     * @return la dirección de su foto, para borrar el archivo
     */
    @Transactional
    public String borrar(String id) {
        Artista artista = artistas.findById(id)
                .orElseThrow(() -> new EventoRechazado("Ese artista no existe."));
        long noches = sql.sql("select count(*) from \"EventoArtista\" where \"artistaId\" = :id")
                .param("id", id)
                .query(Long.class)
                .single();
        if (noches > 0) {
            throw new EventoRechazado("%s está en el cartel de %d %s. Borrarlo lo quitaría de esa historia."
                    .formatted(artista.getNombre(), noches, noches == 1 ? "noche" : "noches"));
        }
        String foto = artista.getImagen();
        artistas.delete(artista);
        return foto;
    }

    /** Deja solo el usuario, sin arroba ni dirección, que es como se guarda. */
    static String instagram(String pegado) {
        if (pegado == null || pegado.isBlank()) return null;
        Matcher m = INSTAGRAM.matcher(pegado.trim());
        if (!m.matches()) {
            throw new EventoRechazado("Ese Instagram no parece válido. Escribe solo el usuario, por ejemplo djbeto.aqp.");
        }
        return m.group(1).toLowerCase(Locale.ROOT);
    }
}
