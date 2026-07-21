package pe.confianza.reportes_programados.task_reportes_back.service.validacioncubo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Comparativo día actual vs. día anterior de un indicador del cubo. */
public record IndicadorCuboFila(
        LocalDate fecCierre,
        String indicador,
        BigDecimal valorActual,
        BigDecimal valorAnterior,
        BigDecimal variacionPct
) {
}
