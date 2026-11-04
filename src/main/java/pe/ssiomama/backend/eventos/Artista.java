package pe.ssiomama.backend.eventos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** DJ o grupo que toca una noche — tabla {@code "Artist"}. */
@Entity
@Table(name = "Artista")
public class Artista {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "genero")
    private String genero;

    @Column(name = "urlImagen")
    private String imagen;

    @Column(name = "instagram")
    private String instagram;

    @Column(name = "bio")
    private String bio;

    protected Artista() {
        // Hibernate necesita un constructor sin argumentos.
    }

    /** Un artista nuevo en el catálogo, todavía sin foto. */
    static Artista crear(String nombre, String genero, String instagram, String bio) {
        Artista a = new Artista();
        a.id = UUID.randomUUID().toString();
        a.actualizar(nombre, genero, instagram, bio);
        return a;
    }

    /** Cambia la ficha. La foto va aparte: es un archivo, no un campo de formulario. */
    void actualizar(String nombre, String genero, String instagram, String bio) {
        this.nombre = nombre.trim();
        this.genero = vacioANulo(genero);
        this.instagram = vacioANulo(instagram);
        this.bio = vacioANulo(bio);
    }

    void cambiarImagen(String url) {
        this.imagen = vacioANulo(url);
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getGenero() {
        return genero;
    }

    public String getImagen() {
        return imagen;
    }

    public String getInstagram() {
        return instagram;
    }

    public String getBio() {
        return bio;
    }
}
