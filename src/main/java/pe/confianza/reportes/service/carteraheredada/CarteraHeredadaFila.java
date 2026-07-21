package pe.confianza.reportes.service.carteraheredada;

import java.time.LocalDate;

/** Fila del stock heredado PDM (equivale a las columnas del Excel "PDM Heredado"). */
public record CarteraHeredadaFila(
        LocalDate fechaCierre,
        Long operacion,
        Integer modulo,
        Integer tipOpe,
        Integer subTipo,
        String asesorOper,
        String asesorOrig,
        Long numGrupo,
        String nomGrupo,
        Integer indHeredado
) {
}
