package pe.confianza.reportes_programados.task_reportes_back.service.datoscierre;

import java.math.BigDecimal;

/** Indicador de desembolsos de Crédito Educativo (habilitados vs. concretados). */
public record CeIndicadorFila(
        String concepto,
        Integer numOperaciones,
        BigDecimal montoDesembolsadoMn
) {
}
