package pe.ssiomama.backend.noches;

import static pe.ssiomama.backend.noches.ExpedienteDeNoche.soles;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import pe.ssiomama.backend.noches.ExpedienteDeNoche.Expediente;

/**
 * El expediente de una noche escrito para que lo lea un modelo de lenguaje.
 *
 * <h2>Por qué texto y no el JSON</h2>
 *
 * <p>El JSON del panel lleva identificadores, céntimos y campos pensados para
 * pintar, y un modelo lo lee peor y gasta más tokens en él. Aquí van las mismas
 * cifras, ya en soles y por secciones con título, que es lo que mejor entiende.
 *
 * <h2>Lo que no va</h2>
 *
 * <p>Los nombres de los clientes de los reservados. El informe no los necesita
 * para nada, y no hay motivo para mandar datos personales de gente real a un
 * proveedor externo. Sí van los del personal y los promotores, porque «la caja
 * de Katherine descuadró» es exactamente lo que hay que poder decir.
 */
public final class TextoDeExpediente {

    private static final Locale ES_PE = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES_PE).withZone(ExpedienteDeNoche.LIMA);
    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ExpedienteDeNoche.LIMA);

    private TextoDeExpediente() {}

    public static String de(Expediente x) {
        List<String> l = new ArrayList<>();
        var n = x.noche();
        var a = x.asistencia();
        var t = x.taquilla();
        var b = x.barra();

        l.add("# " + n.titulo());
        l.add(FECHA.format(n.empiezaA()) + ", de " + HORA.format(n.puertasA()) + " a " + HORA.format(n.terminaA())
                + ", en " + n.sala() + " (aforo " + n.aforo() + "). Estado: " + n.estado()
                + (n.pasada() ? "." : " (todavía no ha empezado)."));
        l.add("Cartel: " + (n.cartel().isEmpty() ? "sin artistas confirmados" : String.join(", ", n.cartel())) + ".");

        if (!x.avisos().isEmpty()) {
            l.add("");
            l.add("## Lo que salta a la vista");
            x.avisos().forEach(av -> l.add("- " + av.texto()));
        }

        l.add("");
        l.add("## Asistencia");
        l.add("- Personas dentro: " + a.dentro()
                + (a.ocupacionPct() == null ? "" : ", el " + a.ocupacionPct() + " % del aforo")
                + " (" + a.entraronConEntrada() + " con entrada + " + a.invitadosEntraron() + " por lista de invitados).");
        l.add("- Entradas emitidas: " + a.entradasEmitidas() + (n.pasada()
                ? "; no se usaron " + a.noVinieron() + pct(a.noVinieronPct(), "") + "."
                : "."));
        l.add("- Listas de invitados: " + a.invitadosApuntados() + " apuntados, " + a.invitadosEntraron() + " entraron.");
        if (a.pico() != null) {
            l.add("- Hora pico de entrada: " + a.pico().hora() + " (" + a.pico().personas() + " personas).");
            l.add("- Entrada por hora: " + a.porHora().stream()
                    .map(h -> h.hora() + " " + h.personas()).collect(Collectors.joining(", ")) + ".");
        }
        if (a.porPuerta().size() > 1) {
            l.add("- Por puerta: " + a.porPuerta().stream()
                    .map(p -> p.puerta() + " " + p.personas()).collect(Collectors.joining(", ")) + ".");
        }

        l.add("");
        l.add("## Taquilla");
        l.add("- Ingresos por entradas: " + soles(t.brutoCentimos()) + " en " + t.pedidosPagados() + " pedidos pagados ("
                + t.entradasVendidas() + " entradas; " + soles(t.gestionCentimos()) + " son gastos de gestión).");
        l.add("- Ticket medio por pedido: " + soles(t.ticketMedioCentimos()) + ".");
        t.porTipo().forEach(p -> l.add("- Tipo «" + p.tipo() + "» a " + soles(p.precioCentimos()) + ": "
                + p.vendidas() + " de " + p.cupo() + " vendidas, " + soles(p.ingresosCentimos()) + "."));
        if (!t.porCanal().isEmpty()) {
            l.add("- Por canal: " + t.porCanal().stream()
                    .map(c -> c.canal() + " " + c.entradas() + " entradas (" + soles(c.centimos()) + ")")
                    .collect(Collectors.joining("; ")) + ".");
        }
        if (t.devueltos() > 0 || t.cancelados() > 0) {
            l.add("- Cancelados: " + t.cancelados() + ". Devueltos: " + t.devueltos() + " ("
                    + soles(t.devueltoCentimos()) + ").");
        }

        l.add("");
        l.add("## Barra");
        l.add("- Facturación: " + soles(b.totalCentimos()) + " en " + b.tickets() + " tickets (" + b.unidades()
                + " unidades). Ticket medio " + soles(b.ticketMedioCentimos())
                + (b.gastoPorPersonaCentimos() == null ? "." : "; gasto por persona dentro " + soles(b.gastoPorPersonaCentimos()) + "."));
        l.add("- Coste estimado del producto vendido: " + soles(b.costeProductoCentimos()) + "; margen bruto "
                + soles(b.margenCentimos()) + ".");
        b.porBarra().forEach(p -> l.add("- Barra «" + p.barra() + "»: " + soles(p.centimos()) + ", " + p.tickets()
                + " tickets, " + p.cajas() + (p.cajas() == 1 ? " caja." : " cajas.")));
        b.cajas().forEach(c -> l.add("- Caja de " + c.responsable() + " en " + c.barra() + ": vendió " + soles(c.vendidoCentimos())
                + " (" + c.tickets() + " tickets, " + soles(c.efectivoCentimos()) + " en efectivo); "
                + (c.cerradaEn() == null ? "sigue abierta." : c.descuadreCentimos() == null || c.descuadreCentimos() == 0
                        ? "cuadró."
                        : "descuadre " + soles(c.descuadreCentimos()) + ".")));
        if (!b.porMetodo().isEmpty()) {
            l.add("- Por método de pago: " + b.porMetodo().stream()
                    .map(m -> m.metodo() + " " + soles(m.centimos())).collect(Collectors.joining(", ")) + ".");
        }
        if (!b.porHora().isEmpty()) {
            l.add("- Ventas por hora: " + b.porHora().stream()
                    .map(h -> h.hora() + " " + soles(h.centimos())).collect(Collectors.joining(", ")) + ".");
        }
        if (!b.productos().isEmpty()) {
            l.add("- Productos más vendidos: " + b.productos().stream().limit(8)
                    .map(p -> p.producto() + " " + p.unidades() + " uds (" + soles(p.ingresosCentimos()) + ")")
                    .collect(Collectors.joining("; ")) + ".");
        }

        if (!x.promotores().isEmpty()) {
            l.add("");
            l.add("## Promotores");
            x.promotores().forEach(p -> l.add("- " + p.nombre() + " (" + p.codigo() + "): " + p.entradas()
                    + " entradas vendidas por " + soles(p.ventasCentimos()) + "; lista " + p.entraron() + " de "
                    + p.apuntados() + " entraron; a pagar " + soles(p.aPagarCentimos())
                    + (p.liquidado() ? " (ya liquidado)." : " (pendiente).")));
        }

        var r = x.reservas();
        if (r.total() > 0) {
            l.add("");
            l.add("## Reservados");
            l.add("- " + r.total() + " reservas: " + r.sentadas() + " sentadas, " + r.confirmadas() + " confirmadas, "
                    + r.noVinieron() + " no vinieron, " + r.canceladas() + " canceladas. " + r.personas()
                    + " personas; depósitos " + soles(r.depositosCentimos()) + ".");
        }

        var c = x.costes();
        var res = x.resultado();
        l.add("");
        l.add("## Costes y resultado");
        l.add("- Gastos de la noche: " + soles(c.gastosCentimos()) + (c.gastosPorCategoria().isEmpty() ? "" : " ("
                + c.gastosPorCategoria().stream().map(g -> g.categoria() + " " + soles(g.centimos()))
                        .collect(Collectors.joining(", ")) + ")") + ".");
        l.add("- Personal: " + soles(c.personalCentimos()) + (c.personalPorPuesto().isEmpty() ? " (sin turnos registrados)" : " ("
                + c.personalPorPuesto().stream().map(p -> p.puesto() + " " + p.personas() + " pers. " + p.horas() + " h")
                        .collect(Collectors.joining(", ")) + ")") + ".");
        l.add("- Comisiones de promotores: " + soles(c.comisionesCentimos()) + ".");
        l.add("- Coste del producto de barra: " + soles(c.costeProductoCentimos()) + ".");
        l.add("- Ingresos " + soles(res.ingresosCentimos()) + " − costes " + soles(res.costesCentimos())
                + " = resultado " + soles(res.resultadoCentimos()) + pct(res.margenPct(), " de margen") + ".");

        var p = x.publico();
        if (p.compradores() > 0) {
            l.add("");
            l.add("## Público que compró");
            l.add("- " + p.compradores() + " compradores: " + p.nuevos() + " nuevos y " + p.recurrentes() + " que ya habían venido."
                    + (p.edadMedia() == null ? "" : " Edad media " + p.edadMedia() + " años.")
                    + " Aceptan publicidad: " + p.aceptanPublicidad() + ".");
            if (!p.distritos().isEmpty()) {
                l.add("- Distritos: " + p.distritos().stream().map(d -> d.distrito() + " " + d.clientes())
                        .collect(Collectors.joining(", ")) + ".");
            }
        }

        var comp = x.comparativa();
        if (comp.noches() > 0) {
            l.add("");
            l.add("## Frente a noches parecidas");
            l.add("- Referencia: media de " + comp.criterio() + ": " + comp.mediaDentro() + " personas, taquilla "
                    + soles(comp.mediaTaquillaCentimos()) + ", barra " + soles(comp.mediaBarraCentimos()) + ".");
            if (comp.dentroVsMediaPct() != null) {
                l.add("- Esta noche: gente " + signo(comp.dentroVsMediaPct()) + ", taquilla " + signo(comp.taquillaVsMediaPct())
                        + ", barra " + signo(comp.barraVsMediaPct()) + " respecto a esa media.");
            }
        }

        if (!x.incidencias().isEmpty() || x.notasDeCierre() != null) {
            l.add("");
            l.add("## Incidencias y notas de la jefatura de sala");
            x.incidencias().forEach(i -> l.add("- " + hora(i.ocurrioEn()) + " [" + i.tipo() + "] " + i.descripcion()));
            if (x.notasDeCierre() != null && !x.notasDeCierre().isBlank()) {
                l.add("- Notas de cierre: " + x.notasDeCierre().trim());
            }
        }

        return String.join("\n", l);
    }

    private static String pct(Integer valor, String sufijo) {
        return valor == null ? "" : " (" + valor + " %" + sufijo + ")";
    }

    private static String signo(Integer pct) {
        if (pct == null) return "sin dato";
        return (pct > 0 ? "+" : "") + pct + " %";
    }

    private static String hora(Instant instante) {
        return HORA.format(instante);
    }
}
