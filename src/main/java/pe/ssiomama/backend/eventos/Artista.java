package pe.ssiomama.backend.eventos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
