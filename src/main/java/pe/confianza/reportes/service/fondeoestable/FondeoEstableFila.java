package pe.confianza.reportes.service.fondeoestable;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Saldo de fondeo estable por matriz (agencia). */
public record FondeoEstableFila(
        LocalDate fecha,
        String matriz,
        BigDecimal saldoFondeoEstable
) {
}
