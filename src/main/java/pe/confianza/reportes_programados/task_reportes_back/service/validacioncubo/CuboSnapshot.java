package pe.confianza.reportes_programados.task_reportes_back.service.validacioncubo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Foto diaria de los indicadores del cubo comercial (columnas de VALIDACION_CUBO_.xlsx). */
public record CuboSnapshot(
        LocalDate fecCierre,
        BigDecimal sumMonDesembolsoMn,
        BigDecimal tasaMesCre,
        BigDecimal tasaMinMesCre,
        BigDecimal ticketPromMesMn,
        BigDecimal sumMonApertura,
        BigDecimal contClientesCreRepro,
        BigDecimal sumSalCapitalCreReproMn
) {

    /** Indicadores en el orden del Excel de referencia, para iterar validaciones. */
    public Map<String, BigDecimal> indicadores() {
        var map = new LinkedHashMap<String, BigDecimal>();
        map.put("SumMonDesembolsoMN", sumMonDesembolsoMn);
        map.put("TasaMesCre", tasaMesCre);
        map.put("TasaMinMesCre", tasaMinMesCre);
        map.put("TicketPromMesMN", ticketPromMesMn);
        map.put("SumMonApertura", sumMonApertura);
        map.put("ContClientesCreRepro", contClientesCreRepro);
        map.put("SumSalCapitalCreReproMN", sumSalCapitalCreReproMn);
        return map;
    }
}
