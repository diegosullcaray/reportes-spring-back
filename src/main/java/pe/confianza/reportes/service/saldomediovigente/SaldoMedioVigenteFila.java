package pe.confianza.reportes.service.saldomediovigente;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Saldo medio vigente MN al cierre del mes. */
public record SaldoMedioVigenteFila(
        LocalDate fecha,
        BigDecimal saldoMedioVigente
) {
}
