package pe.confianza.reportes_programados.task_reportes_back.service.sacatugarra;

import java.math.BigDecimal;

/** Base "Saca tu Garra": variación de saldo, productividad y ratios de recuperación por asesor. */
public record SacaTuGarraFila(
        String usuario,
        BigDecimal varSaldoVigente,
        Integer productividad,
        BigDecimal efectividadMenos30a0,
        BigDecimal efectividad1a30
) {
}
