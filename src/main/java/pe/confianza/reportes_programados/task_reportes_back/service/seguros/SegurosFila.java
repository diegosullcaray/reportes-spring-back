package pe.confianza.reportes_programados.task_reportes_back.service.seguros;

import java.math.BigDecimal;

/**
 * Fila del reporte de penetración de seguros por sectorista (47 columnas del
 * Excel de referencia, agrupadas por bloque de producto).
 * Validación de negocio (notas del área): sectorista, grupo, corredor y
 * territorio nulos se reportan como "SIN ASIGNAR".
 */
public record SegurosFila(
        String codSectorista,
        String sectorista,
        String grupo,
        String corredor,
        String territorio,
        Resumen resumen,
        BloquePymeCc pymeCc,
        BloqueAgro agro,
        BloqueConsumo consumo,
        BloqueCe creditoEducativo,
        BloqueIo iniciandoOficios
) {

    public record Resumen(
            Integer totalOperaciones,
            Integer totalSeguros,
            BigDecimal penetracionTotal,
            Integer multiriesgo,
            Integer multicredito,
            Integer proteccionCuota,
            Integer seguroAgro,
            Integer onco,
            Integer emprendiendoConfianza,
            Integer construyendoConfianza,
            Integer agropecuario,
            Integer consumo,
            Integer creditoEducativo,
            Integer iniciandoOficios,
            BigDecimal meta,
            BigDecimal avance
    ) {
    }

    public record BloquePymeCc(
            Integer totalOperaciones,
            Integer totalSeguros,
            Integer seguroMultiriesgo,
            Integer seguroMulticredito,
            Integer seguroProteccionCuota
    ) {
    }

    public record BloqueAgro(
            Integer totalOperaciones,
            Integer totalSeguros,
            BigDecimal penetracion,
            Integer seguroMultiriesgo,
            Integer seguroMulticredito,
            Integer seguroProteccionCuota,
            Integer seguroAgro
    ) {
    }

    public record BloqueConsumo(
            Integer totalOperaciones,
            Integer totalSeguros,
            BigDecimal penetracion,
            Integer seguroMulticredito,
            Integer seguroProteccionCuota
    ) {
    }

    public record BloqueCe(
            Integer totalOperaciones,
            Integer totalSeguros,
            BigDecimal penetracion
    ) {
    }

    public record BloqueIo(
            Integer totalOperaciones,
            Integer totalSeguros,
            BigDecimal penetracion,
            Integer seguroMultiriesgo,
            Integer seguroMulticredito,
            Integer seguroProteccionCuota
    ) {
    }
}
