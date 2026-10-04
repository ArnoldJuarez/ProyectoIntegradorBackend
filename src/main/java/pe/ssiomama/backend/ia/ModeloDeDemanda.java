package pe.ssiomama.backend.ia;

import java.time.Instant;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Cuánta gente entra y cuánto bebe cada persona, por regresión lineal.
 *
 * <p>Es la misma proyección que el sistema anterior (`lib/proyeccion`), portada
 * tal cual para que los números coincidan:
 *
 * <ul>
 *   <li><b>Asistencia:</b> log(ocupación) contra el género de la noche, si es
 *       sábado, los días desde la quincena, verano, semana de Fiestas Patrias y
 *       feriados cercanos.
 *   <li><b>Consumo:</b> log(unidades por asistente) de cada producto contra el
 *       género.
 * </ul>
 *
 * <p>Se trabaja en logaritmos porque los efectos son multiplicativos: un sábado
 * no suma cien personas, multiplica la ocupación. Y se descartan las noches
 * atípicas con la mediana de desviaciones absolutas (MAD), no con la desviación
 * típica, porque una sola noche desastrosa infla la típica lo bastante como
 * para no descartarse a sí misma.
 */
@Component
public class ModeloDeDemanda {

    /** Por debajo de esto el ajuste no se sostiene. */
    private static final int MINIMO_DE_NOCHES = 20;

    // ── Datos de entrada ──────────────────────────────────────────

    public record Consumo(String sku, String nombre, int unidades) {}

    public record NocheCelebrada(Instant fecha, int aforo, int asistentes, List<String> generos, List<Consumo> consumo) {}

    /** Lo que se sabe de una noche antes de que pase. */
    public record Rasgos(
            String genero,
            boolean esSabado,
            int diasDesdePago,
            boolean esVerano,
            boolean esSemanaPatria,
            boolean esFeriado,
            int aforo) {

        public static Rasgos de(Instant fecha, List<String> generos, int aforo) {
            LocalDate dia = fecha.atZone(Club.ZONA).toLocalDate();
            return new Rasgos(
                    generos.isEmpty() ? "Sin género" : generos.get(0),
                    dia.getDayOfWeek() == DayOfWeek.SATURDAY,
                    ModeloDeDemanda.diasDesdePago(dia),
                    dia.getMonthValue() == 12 || dia.getMonthValue() <= 3,
                    dia.getMonthValue() == 7 && dia.getDayOfMonth() >= 24,
                    ModeloDeDemanda.esFeriadoCercano(dia),
                    aforo);
        }
    }

    // ── Calendario peruano ────────────────────────────────────────

    /** Feriados nacionales de fecha fija (mes, día). */
    private static final int[][] FERIADOS = {
        {1, 1}, {5, 1}, {6, 29}, {7, 28}, {7, 29}, {8, 30}, {10, 8}, {11, 1}, {12, 8}, {12, 25},
    };

    static boolean esFeriadoCercano(LocalDate dia) {
        return Arrays.stream(FERIADOS)
                .anyMatch(f -> f[0] == dia.getMonthValue() && Math.abs(f[1] - dia.getDayOfMonth()) <= 1);
    }

    /** Días desde el último cobro: en Perú se paga la quincena y el fin de mes. */
    static int diasDesdePago(LocalDate dia) {
        int d = dia.getDayOfMonth();
        if (d == dia.lengthOfMonth()) return 0;
        if (d >= 15) return d - 15;
        return d;
    }

    // ── Entrenamiento ─────────────────────────────────────────────

    public Entrenado entrenar(List<NocheCelebrada> historico) {
        List<NocheCelebrada> utiles = sinAtipicas(historico.stream()
                .filter(n -> n.asistentes() > 0 && n.aforo() > 0 && !n.generos().isEmpty())
                .toList());
        if (utiles.size() < MINIMO_DE_NOCHES) {
            throw new IllegalStateException("No se puede proyectar todavía: hacen falta al menos "
                    + MINIMO_DE_NOCHES + " noches con asistencia para entrenar y hay " + utiles.size() + ".");
        }

        List<String> generos = utiles.stream().map(n -> n.generos().get(0)).distinct().sorted().toList();

        List<double[]> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();
        for (NocheCelebrada n : utiles) {
            x.add(filaAsistencia(Rasgos.de(n.fecha(), n.generos(), n.aforo()), generos));
            y.add(Math.log(n.asistentes() / (double) n.aforo()));
        }
        Ajuste asistencia = Ajuste.ols(x, y);

        Map<String, LineaEntrenada> consumo = new LinkedHashMap<>();
        LinkedHashSet<String> skus = new LinkedHashSet<>();
        utiles.forEach(n -> n.consumo().forEach(c -> skus.add(c.sku())));

        for (String sku : skus) {
            List<double[]> xc = new ArrayList<>();
            List<Double> yc = new ArrayList<>();
            String nombre = sku;
            for (NocheCelebrada n : utiles) {
                var linea = n.consumo().stream().filter(c -> c.sku().equals(sku)).findFirst();
                if (linea.isEmpty()) continue;
                double porAsistente = linea.get().unidades() / (double) n.asistentes();
                if (porAsistente <= 0) continue;
                nombre = linea.get().nombre();
                xc.add(dummies(n.generos().get(0), generos));
                yc.add(Math.log(porAsistente));
            }
            if (xc.size() < generos.size() + 5) continue;
            consumo.put(sku, new LineaEntrenada(sku, nombre, Ajuste.ols(xc, yc)));
        }

        return new Entrenado(generos, asistencia, consumo, utiles.size());
    }

    private static double[] dummies(String genero, List<String> generos) {
        return generos.subList(1, generos.size()).stream().mapToDouble(g -> g.equals(genero) ? 1 : 0).toArray();
    }

    private static double[] filaAsistencia(Rasgos r, List<String> generos) {
        double[] d = dummies(r.genero(), generos);
        double[] fila = Arrays.copyOf(d, d.length + 5);
        fila[d.length] = r.esSabado() ? 1 : 0;
        // Recortado a 15: pasado medio mes el efecto del cobro ya no crece.
        fila[d.length + 1] = Math.min(r.diasDesdePago(), 15);
        fila[d.length + 2] = r.esVerano() ? 1 : 0;
        fila[d.length + 3] = r.esSemanaPatria() ? 1 : 0;
        fila[d.length + 4] = r.esFeriado() ? 1 : 0;
        return fila;
    }

    /** Descarta noches atípicas con la MAD del log de ocupación. */
    private static List<NocheCelebrada> sinAtipicas(List<NocheCelebrada> noches) {
        if (noches.size() < 10) return noches;
        double[] logs = noches.stream().mapToDouble(n -> Math.log(n.asistentes() / (double) n.aforo())).toArray();
        double[] ordenados = logs.clone();
        Arrays.sort(ordenados);
        double mediana = ordenados[ordenados.length / 2];
        double[] desviaciones = Arrays.stream(logs).map(l -> Math.abs(l - mediana)).sorted().toArray();
        double mad = desviaciones[desviaciones.length / 2];
        if (mad == 0) return noches;

        List<NocheCelebrada> quedan = new ArrayList<>();
        for (int i = 0; i < noches.size(); i++) {
            if (Math.abs(logs[i] - mediana) <= 4 * mad) quedan.add(noches.get(i));
        }
        return quedan;
    }

    // ── Modelo entrenado ──────────────────────────────────────────

    record LineaEntrenada(String sku, String nombre, Ajuste ajuste) {}

    public record LineaProyectada(String sku, String nombre, double porAsistente, int unidades) {}

    public record Proyeccion(int asistentes, List<LineaProyectada> lineas) {}

    public record Entrenado(
            List<String> generos, Ajuste asistencia, Map<String, LineaEntrenada> consumo, int noches) {

        public double r2() {
            return asistencia.r2();
        }

        public int proyectarAsistencia(Rasgos r) {
            double ocupacion = Math.min(1, Math.exp(asistencia.predecir(filaAsistencia(r, generos))));
            return (int) Math.max(0, Math.round(r.aforo() * ocupacion));
        }

        public Proyeccion proyectarConsumo(Rasgos r) {
            int asistentes = proyectarAsistencia(r);
            double[] d = dummies(r.genero(), generos);
            List<LineaProyectada> lineas = consumo.values().stream()
                    .map(l -> {
                        double porAsistente = Math.exp(l.ajuste().predecir(d));
                        return new LineaProyectada(l.sku(), l.nombre(), porAsistente,
                                (int) Math.round(porAsistente * asistentes));
                    })
                    .sorted(Comparator.comparingInt(LineaProyectada::unidades).reversed())
                    .toList();
            return new Proyeccion(asistentes, lineas);
        }
    }

    // ── Mínimos cuadrados ─────────────────────────────────────────

    /** Un ajuste por mínimos cuadrados ordinarios, con término constante. */
    public record Ajuste(double[] beta, double r2, int n) {

        static Ajuste ols(List<double[]> x, List<Double> y) {
            int n = x.size();
            if (n == 0) throw new IllegalStateException("No hay datos que ajustar.");
            int k = x.get(0).length + 1;
            if (n <= k) {
                throw new IllegalStateException("Hacen falta más de " + k + " observaciones para ajustar " + k + " coeficientes.");
            }

            double[][] xtx = new double[k][k];
            double[] xty = new double[k];
            for (int f = 0; f < n; f++) {
                double[] fila = conConstante(x.get(f));
                for (int i = 0; i < k; i++) {
                    xty[i] += fila[i] * y.get(f);
                    for (int j = 0; j < k; j++) xtx[i][j] += fila[i] * fila[j];
                }
            }
            double[] beta = resolver(xtx, xty);

            double media = y.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double total = 0;
            double residual = 0;
            for (int f = 0; f < n; f++) {
                double prediccion = producto(conConstante(x.get(f)), beta);
                total += Math.pow(y.get(f) - media, 2);
                residual += Math.pow(y.get(f) - prediccion, 2);
            }
            return new Ajuste(beta, total == 0 ? 1 : 1 - residual / total, n);
        }

        double predecir(double[] fila) {
            return producto(conConstante(fila), beta);
        }

        private static double[] conConstante(double[] fila) {
            double[] c = new double[fila.length + 1];
            c[0] = 1;
            System.arraycopy(fila, 0, c, 1, fila.length);
            return c;
        }

        private static double producto(double[] a, double[] b) {
            double s = 0;
            for (int i = 0; i < b.length; i++) s += a[i] * b[i];
            return s;
        }

        /**
         * Gauss-Jordan con pivoteo parcial.
         *
         * <p>Un pivote casi nulo deja ese coeficiente a cero en vez de fallar: pasa
         * cuando un género no aparece nunca en sábado, y ahí el efecto
         * sencillamente no se puede estimar.
         */
        private static double[] resolver(double[][] a, double[] b) {
            int k = b.length;
            double[][] m = new double[k][k + 1];
            for (int i = 0; i < k; i++) {
                System.arraycopy(a[i], 0, m[i], 0, k);
                m[i][k] = b[i];
            }
            for (int col = 0; col < k; col++) {
                int mejor = col;
                for (int f = col + 1; f < k; f++) {
                    if (Math.abs(m[f][col]) > Math.abs(m[mejor][col])) mejor = f;
                }
                double[] t = m[col];
                m[col] = m[mejor];
                m[mejor] = t;

                double pivote = m[col][col];
                if (Math.abs(pivote) < 1e-12) continue;
                for (int f = 0; f < k; f++) {
                    if (f == col) continue;
                    double factor = m[f][col] / pivote;
                    if (factor == 0) continue;
                    for (int c = col; c <= k; c++) m[f][c] -= factor * m[col][c];
                }
            }
            double[] beta = new double[k];
            for (int i = 0; i < k; i++) beta[i] = Math.abs(m[i][i]) < 1e-12 ? 0 : m[i][k] / m[i][i];
            return beta;
        }
    }
}
