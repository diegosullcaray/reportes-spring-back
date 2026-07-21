package pe.confianza.reportes_programados.task_reportes_back.service.validacioncubo;

/**
 * Indicadores diarios del cubo comercial (columnas del archivo de referencia
 * VALIDACION_CUBO_.xlsx).
 *
 * TODO: la carpeta ".docs/VALIDACIONES DIARIAS" no incluye el SQL de origen,
 * solo el Excel con los indicadores; ajustar el FROM a la vista/tabla real del
 * cubo cuando el área la confirme. Las columnas ya coinciden con el Excel.
 */
public final class ValidacionCuboQueries {

    private ValidacionCuboQueries() {
    }

    public static final String INDICADORES_CUBO = """
            SET NOCOUNT ON;
            select FecCierre,
                   SumMonDesembolsoMN,
                   TasaMesCre,
                   TasaMinMesCre,
                   TicketPromMesMN,
                   SumMonApertura,
                   ContClientesCreRepro,
                   SumSalCapitalCreReproMN
            from storage.cubo.VVALIDACIONCUBO01
            where FecCierre=:corte
            """;
}
