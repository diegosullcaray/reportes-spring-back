package pe.confianza.reportes_programados.task_reportes_back.service.saldopuntual;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Saldo medio de pasivos por agencia y producto. */
public record SaldoMedioFila(
        LocalDate fecha,
        String agencia,
        String producto,
        BigDecimal saldoMedioMn
) {
}
