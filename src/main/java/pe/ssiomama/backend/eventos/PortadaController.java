package pe.ssiomama.backend.eventos;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Subir el flyer de una noche y servir las imágenes subidas.
 *
 * <p>Lo que se hace con el archivo —reescalar, pasar a JPEG, quitar metadatos—
 * está en {@link Medios}, que comparten el flyer, la foto de cada artista y el
 * video. Aquí solo se decide a qué noche va.
 *
 * <p>El sistema anterior guardaba WebP con {@code sharp}. Java no escribe WebP
 * sin una librería nativa, y JPEG pesa algo más pero lo lee cualquier navegador.
 */
@RestController
public class PortadaController {

    private final EventoRepository eventos;
    private final Medios medios;

    public PortadaController(EventoRepository eventos, Medios medios) {
        this.eventos = eventos;
        this.medios = medios;
    }

    public record PortadaGuardada(String portada, long pesoKb) {}

    @PostMapping("/api/eventos/{slug}/portada")
    @Transactional
    public PortadaGuardada subir(
            @PathVariable String slug, @RequestParam("archivo") MultipartFile archivo)
            throws IOException {

        Evento noche = eventos.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Esa noche no existe."));

        Medios.Guardado guardado = medios.guardarImagen(noche.getSlug(), archivo);

        String anterior = noche.getPortada();
        noche.cambiarPortada(guardado.ruta());
        medios.borrarSiEsNuestro(anterior);

        return new PortadaGuardada(noche.getPortada(), guardado.pesoKb());
    }

    /** Quitar el flyer y dejar la noche con el rayado de siempre. */
    @DeleteMapping("/api/eventos/{slug}/portada")
    @Transactional
    public ResponseEntity<Void> quitar(@PathVariable String slug) {
        Evento noche = eventos.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Esa noche no existe."));

        String anterior = noche.getPortada();
        noche.cambiarPortada(null);
        medios.borrarSiEsNuestro(anterior);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sirve una imagen subida: flyer o foto de artista.
     *
     * <p>Caché larga e inmutable: el nombre cambia con cada subida, así que un
     * archivo con un nombre dado nunca cambia de contenido.
     */
    @GetMapping(Medios.RUTA_IMAGENES + "{nombre}")
    public ResponseEntity<Resource> servir(@PathVariable String nombre) {
        return medios.imagen(nombre)
                .map(PortadaController::comoImagen)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<Resource> comoImagen(Path archivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable())
                .body(new FileSystemResource(archivo));
    }
}
