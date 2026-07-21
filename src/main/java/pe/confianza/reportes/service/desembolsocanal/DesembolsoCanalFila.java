package pe.confianza.reportes.service.desembolsocanal;

import java.math.BigDecimal;

/** Desembolsos del período agrupados por jerarquía comercial y canal (BT/CT). */
public record DesembolsoCanalFila(
        String territorio,
        String corredor,
        String unidad,
        String codSectorista,
        String sectorista,
        String tipoOperacion,
        Integer operaciones,
        BigDecimal monto,
        String canal
) {
}
