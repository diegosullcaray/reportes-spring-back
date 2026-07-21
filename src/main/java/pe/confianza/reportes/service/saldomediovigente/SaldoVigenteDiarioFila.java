package pe.confianza.reportes.service.saldomediovigente;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Serie diaria de saldo vigente MN dentro del mes de corte. */
public record SaldoVigenteDiarioFila(
        LocalDate fecha,
        BigDecimal saldoVigente
) {
}
