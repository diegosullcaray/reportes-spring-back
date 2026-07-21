package pe.confianza.reportes.service.controlcargas;

import java.util.List;

/** Resultado de una validación de control de cargas (respuesta de la API y base del mensaje a Chat). */
public record ControlCargasResultado(
        boolean success,
        ResumenCritico resumenCritico,
        Totales totales,
        List<ProcesoCarga> procesosPendientes,
        List<ProcesoCarga> procesosFinalizados
) {

    public record ResumenCritico(
            boolean carterasActivasYPasivasListas,
            String mensaje,
            List<ProcesoCarga> procesosCriticosEvaluados
    ) {
    }

    public record Totales(int totalProcesos, int pendientes, int finalizados) {
    }

    public static ControlCargasResultado sinRegistros() {
        return new ControlCargasResultado(false,
                new ResumenCritico(false,
                        "No se encontraron registros de procesos al ejecutar mod_rep.com.RSRPD001", List.of()),
                new Totales(0, 0, 0), List.of(), List.of());
    }
}
