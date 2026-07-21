package pe.confianza.reportes.service.carteravigenteagro;

import java.time.LocalDate;

/** Variación de clientes agro entre el cierre anterior y el actual por sectorista. */
public record ClientesAgroFila(
        LocalDate fecha,
        String grupo,
        String territorio,
        String corredor,
        String unidad,
        String sectorista,
        Integer cierreMesAnterior,
        Integer cierreActual,
        Integer variacion
) {
}
