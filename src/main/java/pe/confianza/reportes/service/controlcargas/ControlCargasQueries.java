package pe.confianza.reportes.service.controlcargas;

/**
 * SQL migrado 1:1 desde "control-cargas.query.js" del Node.js (AR-03):
 * el estado de los procesos de carga lo entrega el procedimiento
 * mod_rep.com.RSRPD001 con opción '2' (columnas des_pro, fec_act, fec_rep, est_pro).
 */
public final class ControlCargasQueries {

    private ControlCargasQueries() {
    }

    public static final String ESTADO_PROCESOS = """
            SET NOCOUNT ON;
            EXEC mod_rep.com.RSRPD001 @OPT = '2'
            """;
}
