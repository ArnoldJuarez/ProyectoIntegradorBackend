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
 * El video de una noche: subirlo, quitarlo y servirlo.
 *
 * <p>Es opcional y complementa al flyer, no lo sustituye: el flyer es lo que se
 * comparte y lo que sale en la agenda; el video da ambiente en la ficha.
 *
 * <h2>Se sirve por rangos</h2>
 *
 * <p>Un {@code <video>} no descarga el archivo entero: pide trozos con la
 * cabecera {@code Range} para empezar a reproducir enseguida y para saltar a un
 * minuto concreto. Spring MVC responde {@code 206 Partial Content} solo con
 * devolver un {@link Resource}; sin eso, Safari directamente no reproduce.
 */
@RestController
public class VideoController {

    private final EventoRepository eventos;
    private final Medios medios;

    public VideoController(EventoRepository eventos, Medios medios) {
        this.eventos = eventos;
        this.medios = medios;
    }

    public record VideoGuardado(String video, long pesoKb) {}

    @PostMapping("/api/eventos/{slug}/video")
    @Transactional
    public VideoGuardado subir(
            @PathVariable String slug, @RequestParam("archivo") MultipartFile archivo)
            throws IOException {

        Evento noche = eventos.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Esa noche no existe."));

        Medios.Guardado guardado = medios.guardarVideo(noche.getSlug(), archivo);

        String anterior = noche.getVideo();
        noche.cambiarVideo(guardado.ruta());
        medios.borrarSiEsNuestro(anterior);

        return new VideoGuardado(noche.getVideo(), guardado.pesoKb());
    }

    @DeleteMapping("/api/eventos/{slug}/video")
    @Transactional
    public ResponseEntity<Void> quitar(@PathVariable String slug) {
        Evento noche = eventos.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Esa noche no existe."));

        String anterior = noche.getVideo();
        noche.cambiarVideo(null);
        medios.borrarSiEsNuestro(anterior);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(Medios.RUTA_VIDEOS + "{nombre}")
    public ResponseEntity<Resource> servir(@PathVariable String nombre) {
        return medios.video(nombre)
                .map(VideoController::comoVideo)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<Resource> comoVideo(Path archivo) {
        MediaType tipo = archivo.getFileName().toString().endsWith(".webm")
                ? MediaType.parseMediaType("video/webm")
                : MediaType.parseMediaType("video/mp4");
        return ResponseEntity.ok()
                .contentType(tipo)
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable())
                .body(new FileSystemResource(archivo));
    }
}
