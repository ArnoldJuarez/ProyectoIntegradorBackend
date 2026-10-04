package pe.ssiomama.backend.eventos;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.beans.factory.annotation.Value;
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
 * Subir el flyer de una noche y servirlo.
 *
 * <h2>Lo que hace con el archivo</h2>
 *
 * <p>No lo guarda tal cual. Un flyer de diseñador llega en PNG a 3000 px y
 * cuatro megas, y eso en la portada del móvil de alguien con datos es la mitad
 * del tiempo de carga. Se reescala a {@value #ANCHO_MAXIMO} px y se guarda en
 * JPEG, que para una imagen así son unos cientos de KB sin diferencia visible.
 *
 * <p>Reencodear además <b>descarta los metadatos</b>: muchos flyers llegan con
 * la geolocalización del móvil donde se hicieron, y eso no tiene por qué acabar
 * publicado.
 *
 * <p>El sistema anterior guardaba WebP con {@code sharp}. Java no escribe WebP
 * sin una librería nativa, y JPEG pesa algo más pero lo lee cualquier navegador.
 *
 * <h2>Dónde lo escribe</h2>
 *
 * <p>En la carpeta {@code ssiomama.carpeta-portadas}, fuera del código. Y lo
 * sirve el propio backend en {@code /api/publico/portadas/...}: el frontend es
 * otro proceso en otro puerto, y no tiene por qué saber dónde está el disco.
 */
@RestController
public class PortadaController {

    static final String RUTA_PUBLICA = "/api/publico/portadas/";

    private static final int ANCHO_MAXIMO = 1400;
    private static final long PESO_MAXIMO = 12L * 1024 * 1024;
    private static final Set<String> TIPOS = Set.of("image/jpeg", "image/png");

    /** Lo único que se sirve: los nombres que genera este mismo controlador. */
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("[a-z0-9-]+-\\d+\\.jpg");

    private final EventoRepository eventos;
    private final Path carpeta;

    public PortadaController(
            EventoRepository eventos, @Value("${ssiomama.carpeta-portadas}") String carpeta) {
        this.eventos = eventos;
        this.carpeta = Path.of(carpeta).toAbsolutePath().normalize();
    }

    public record PortadaGuardada(String portada, long pesoKb) {}

    @PostMapping("/api/eventos/{slug}/portada")
    @Transactional
    public PortadaGuardada subir(
            @PathVariable String slug, @RequestParam("archivo") MultipartFile archivo)
            throws IOException {

        Evento noche = eventos.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Esa noche no existe."));

        if (archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No ha llegado ningún archivo.");
        }
        if (!TIPOS.contains(String.valueOf(archivo.getContentType()))) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tiene que ser una imagen JPG o PNG.");
        }
        if (archivo.getSize() > PESO_MAXIMO) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "La imagen pesa más de 12 MB.");
        }

        byte[] optimizada;
        try (InputStream entrada = archivo.getInputStream()) {
            BufferedImage original = ImageIO.read(entrada);
            if (original == null) {
                throw new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "No se ha podido leer esa imagen.");
            }
            optimizada = aJpeg(reescalar(original));
        }

        /*
         * El nombre lleva la marca de tiempo a propósito: si se reutilizara el
         * del slug, al cambiar el flyer el navegador seguiría enseñando el viejo
         * durante horas por su caché.
         */
        String nombre = noche.getSlug() + "-" + System.currentTimeMillis() + ".jpg";
        Files.createDirectories(carpeta);
        Files.write(carpeta.resolve(nombre), optimizada);

        String anterior = noche.getPortada();
        noche.cambiarPortada(RUTA_PUBLICA + nombre);
        borrarSiEsNuestra(anterior);

        return new PortadaGuardada(noche.getPortada(), Math.round(optimizada.length / 1024.0));
    }

    /** Quitar el flyer y dejar la noche con el rayado de siempre. */
    @DeleteMapping("/api/eventos/{slug}/portada")
    @Transactional
    public ResponseEntity<Void> quitar(@PathVariable String slug) {
        Evento noche = eventos.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Esa noche no existe."));

        String anterior = noche.getPortada();
        noche.cambiarPortada(null);
        borrarSiEsNuestra(anterior);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sirve un flyer subido.
     *
     * <p>Cache larga e inmutable: el nombre cambia con cada subida, así que un
     * archivo con un nombre dado nunca cambia de contenido.
     */
    @GetMapping(RUTA_PUBLICA + "{nombre}")
    public ResponseEntity<Resource> servir(@PathVariable String nombre) {
        // Sin esto, «../application-local.yml» sería una petición válida.
        if (!NOMBRE_VALIDO.matcher(nombre).matches()) {
            return ResponseEntity.notFound().build();
        }
        Path archivo = carpeta.resolve(nombre);
        if (!Files.isRegularFile(archivo)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable())
                .body(new FileSystemResource(archivo));
    }

    /**
     * Borra el flyer anterior SOLO si lo había subido este mismo sitio.
     *
     * <p>Las noches de demostración apuntan a {@code /fotos/*.webp}, que son
     * archivos del frontend compartidos —uno es la portada del sitio entero—.
     */
    private void borrarSiEsNuestra(String portada) {
        if (portada == null || !portada.startsWith(RUTA_PUBLICA)) return;
        String nombre = portada.substring(RUTA_PUBLICA.length());
        if (!NOMBRE_VALIDO.matcher(nombre).matches()) return;
        try {
            Files.deleteIfExists(carpeta.resolve(nombre));
        } catch (IOException e) {
            // Un archivo huérfano en disco no es motivo para fallar la subida.
        }
    }

    /** A {@value #ANCHO_MAXIMO} px como mucho, sobre fondo negro si traía transparencia. */
    private static BufferedImage reescalar(BufferedImage original) {
        int ancho = Math.min(ANCHO_MAXIMO, original.getWidth());
        int alto = (int) Math.round(original.getHeight() * (ancho / (double) original.getWidth()));

        BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, ancho, alto);
        g.drawImage(original, 0, 0, ancho, alto, null);
        g.dispose();
        return destino;
    }

    private static byte[] aJpeg(BufferedImage imagen) throws IOException {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam parametros = escritor.getDefaultWriteParam();
        parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parametros.setCompressionQuality(0.82f);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream flujo = new MemoryCacheImageOutputStream(salida)) {
            escritor.setOutput(flujo);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }
}
