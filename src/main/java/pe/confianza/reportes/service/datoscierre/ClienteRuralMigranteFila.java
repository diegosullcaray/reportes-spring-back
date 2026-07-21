package pe.confianza.reportes.service.datoscierre;

import java.time.LocalDate;

/** Clientes nuevos del mes clasificados por ruralidad y condición migrante. */
public record ClienteRuralMigranteFila(
        LocalDate fecha,
        String indicadorRural,
        String indicadorMigrante,
        Integer numClientes
) {
}
