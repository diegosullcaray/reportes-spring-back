package pe.confianza.reportes.service.saldopuntual;

import java.math.BigDecimal;

/** Saldo puntual de pasivos por agencia y producto. */
public record SaldoPuntualFila(
        Integer codAgencia,
        String agencia,
        String producto,
        BigDecimal saldoMn,
        String matriz,
        String macro,
        String territorio
) {
}
