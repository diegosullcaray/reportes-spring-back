package pe.confianza.reportes_programados.task_reportes_back.service.carteravigenteagro;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Saldo vigente del producto agropecuario por jerarquía comercial. */
public record SaldoVigenteAgroFila(
        LocalDate fecha,
        String grupo,
        String territorio,
        String corredor,
        String unidad,
        String sectorista,
        BigDecimal saldoVigente
) {
}
