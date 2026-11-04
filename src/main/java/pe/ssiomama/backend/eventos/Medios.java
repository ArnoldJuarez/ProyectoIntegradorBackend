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
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Los archivos que se suben desde el panel: flyers, fotos de artista y videos.
 *
 * <h2>Imágenes: nunca tal cual</h2>
 *
 * <p>Un flyer de diseñador llega en PNG a 3000 px y cuatro megas, y eso en el
 * móvil de alguien con datos es la mitad del tiempo de carga. Se reescala a
 * {@value #ANCHO_MAXIMO} px y se guarda en JPEG. Reencodear además <b>descarta
 * los metadatos</b>: muchas fotos llegan con la geolocalización del móvil donde
 * se hicieron, y eso no tiene por qué acabar publicado.
 *
 * <h2>Videos: tal cual, pero comprobados</h2>
 *
 * <p>Recomprimir video en el servidor exigiría ffmpeg, así que se guarda el
 * archivo que llega. Lo que sí se comprueba es que <b>sea</b> un MP4 o un WebM
 * mirando sus primeros bytes, no la extensión ni el tipo que declara el
 * navegador: los dos los decide quien sube.
 *
 * <h2>Dónde</h2>
 *
 * <p>En {@code ssiomama.carpeta-portadas}, fuera del código y de git. Lo sirve
 * el propio backend bajo {@code /api/publico/...}: el frontend es otro proceso
 * y no tiene por qué saber dónde está el disco.
 */
@Component
public class Medios {

    static final String RUTA_IMAGENES = "/api/publico/portadas/";
    static final String RUTA_VIDEOS = "/api/publico/videos/";

    private static final int ANCHO_MAXIMO = 1400;
    private static final long PESO_MAXIMO_IMAGEN = 12L * 1024 * 1024;
    /** Un clip de promo de 30 s en 1080p ronda los 20 MB; 80 deja margen sin abrir la puerta a una película. */
    private static final long PESO_MAXIMO_VIDEO = 80L * 1024 * 1024;
    private static final Set<String> TIPOS_IMAGEN = Set.of("image/jpeg", "image/png");

    /**
     * Lo único que se sirve: los nombres que genera esta misma clase. Sin esto,
     * «../application-local.yml» sería una petición válida.
     */
    private static final Pattern IMAGEN_VALIDA = Pattern.compile("[a-z0-9-]+-\\d+\\.jpg");
    private static final Pattern VIDEO_VALIDO = Pattern.compile("[a-z0-9-]+-\\d+\\.(mp4|webm)");

    private final Path carpeta;

    public Medios(@Value("${ssiomama.carpeta-portadas}") String carpeta) {
        this.carpeta = Path.of(carpeta).toAbsolutePath().normalize();
    }

    // ── Imágenes ──────────────────────────────────────────────────

    /** Una imagen guardada: su dirección pública y lo que pesa ya optimizada. */
    public record Guardado(String ruta, long pesoKb) {}

    /**
     * Comprueba, optimiza y guarda una imagen.
     *
     * @param prefijo de qué es: el slug de la noche o el nombre del artista
     */
    public Guardado guardarImagen(String prefijo, MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No ha llegado ningún archivo.");
        }
        if (!TIPOS_IMAGEN.contains(String.valueOf(archivo.getContentType()))) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tiene que ser una imagen JPG o PNG.");
        }
        if (archivo.getSize() > PESO_MAXIMO_IMAGEN) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen pesa más de 12 MB.");
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
         * El nombre lleva la marca de tiempo a propósito: si se reutilizara,
         * al cambiar la imagen el navegador seguiría enseñando la vieja durante
         * horas por su caché (se sirve con caché inmutable de un año).
         */
        String nombre = limpio(prefijo) + "-" + System.currentTimeMillis() + ".jpg";
        Files.createDirectories(carpeta);
        Files.write(carpeta.resolve(nombre), optimizada);
        return new Guardado(RUTA_IMAGENES + nombre, Math.round(optimizada.length / 1024.0));
    }

    /** El archivo de una imagen subida, si el nombre es de los nuestros y existe. */
    Optional<Path> imagen(String nombre) {
        return archivo(nombre, IMAGEN_VALIDA);
    }

    // ── Videos ────────────────────────────────────────────────────

    /** Comprueba y guarda un video MP4 o WebM. */
    public Guardado guardarVideo(String prefijo, MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No ha llegado ningún archivo.");
        }
        if (archivo.getSize() > PESO_MAXIMO_VIDEO) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El video pesa más de 80 MB.");
        }

        String extension;
        try (InputStream entrada = archivo.getInputStream()) {
            extension = formatoDeVideo(entrada.readNBytes(12)).orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tiene que ser un video MP4 o WebM."));
        }

        String nombre = limpio(prefijo) + "-" + System.currentTimeMillis() + "." + extension;
        Files.createDirectories(carpeta);
        try (InputStream entrada = archivo.getInputStream()) {
            Files.copy(entrada, carpeta.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
        }
        return new Guardado(RUTA_VIDEOS + nombre, Math.round(archivo.getSize() / 1024.0));
    }

    /** El archivo de un video subido, si el nombre es de los nuestros y existe. */
    Optional<Path> video(String nombre) {
        return archivo(nombre, VIDEO_VALIDO);
    }

    /**
     * Reconoce el formato por la cabecera del archivo.
     *
     * <p>MP4 (y MOV, que comparte contenedor) lleva {@code ftyp} en los bytes
     * 4 a 8; WebM empieza con la marca EBML {@code 1A 45 DF A3}.
     */
    static Optional<String> formatoDeVideo(byte[] cabecera) {
        if (cabecera.length >= 8
                && cabecera[4] == 'f' && cabecera[5] == 't' && cabecera[6] == 'y' && cabecera[7] == 'p') {
            return Optional.of("mp4");
        }
        if (cabecera.length >= 4
                && (cabecera[0] & 0xFF) == 0x1A && (cabecera[1] & 0xFF) == 0x45
                && (cabecera[2] & 0xFF) == 0xDF && (cabecera[3] & 0xFF) == 0xA3) {
            return Optional.of("webm");
        }
        return Optional.empty();
    }

    // ── Borrar ────────────────────────────────────────────────────

    /**
     * Borra un archivo SOLO si lo subió este sitio.
     *
     * <p>Las noches de demostración apuntan a {@code /fotos/*.webp}, que son
     * archivos del frontend compartidos —uno es la portada del sitio entero—, y
     * un video puede ser un enlace de YouTube. Ninguno de esos se toca.
     */
    public void borrarSiEsNuestro(String ruta) {
        if (ruta == null) return;
        Optional<Path> archivo = ruta.startsWith(RUTA_IMAGENES)
                ? imagen(ruta.substring(RUTA_IMAGENES.length()))
                : ruta.startsWith(RUTA_VIDEOS) ? video(ruta.substring(RUTA_VIDEOS.length())) : Optional.empty();
        archivo.ifPresent(a -> {
            try {
                Files.deleteIfExists(a);
            } catch (IOException e) {
                // Un archivo huérfano en disco no es motivo para fallar la operación.
            }
        });
    }

    // ── Apoyo ─────────────────────────────────────────────────────

    private Optional<Path> archivo(String nombre, Pattern valido) {
        if (nombre == null || !valido.matcher(nombre).matches()) return Optional.empty();
        Path archivo = carpeta.resolve(nombre);
        return Files.isRegularFile(archivo) ? Optional.of(archivo) : Optional.empty();
    }

    /** «DJ Beto & Cía» → «dj-beto-cia»: solo lo que admite el patrón de nombres. */
    static String limpio(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String limpio = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (limpio.isEmpty()) return "archivo";
        return limpio.length() > 60 ? limpio.substring(0, 60).replaceAll("-+$", "") : limpio;
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
