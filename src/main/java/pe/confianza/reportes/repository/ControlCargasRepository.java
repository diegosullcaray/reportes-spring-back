package pe.confianza.reportes.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.confianza.reportes.repository.support.RowMapperUtils;
import pe.confianza.reportes.service.controlcargas.ControlCargasQueries;
import pe.confianza.reportes.service.controlcargas.ProcesoCarga;
import pe.confianza.reportes.shared.ReporteException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consulta síncrona (no @Async): el control de cargas corre cada pocos minutos
 * en su propio hilo del scheduler y es una sola query liviana — no amerita
 * ocupar el pool de reportes.
 *
 * El mapeo de columnas es tolerante porque los nombres reales que devuelve
 * mod_rep.com.RSRPD001 pueden diferir de los del módulo Node.js de referencia
 * (des_pro/fec_act/fec_rep/est_pro): se resuelve por lista de candidatos y, si
 * ninguno calza, por posición (1=proceso, 2=fec.actividad, 3=fec.reporte,
 * 4=estado). Si tampoco alcanza, el error lista las columnas reales del SP.
 */
@Repository
public class ControlCargasRepository {

    private static final Logger log = LoggerFactory.getLogger(ControlCargasRepository.class);

    private static final String[] CAND_PROCESO = {"des_pro", "despro", "des_proceso", "descripcion", "proceso", "nom_pro"};
    private static final String[] CAND_FEC_ACT = {"fec_act", "fecact", "fec_actividad", "fecha_actividad", "fec_actual"};
    private static final String[] CAND_FEC_REP = {"fec_rep", "fecrep", "fec_reporte", "fecha_reporte"};
    private static final String[] CAND_ESTADO  = {"est_pro", "estpro", "est_proceso", "estado", "des_est"};

    private final NamedParameterJdbcTemplate jdbc;

    public ControlCargasRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProcesoCarga> consultarEstadoProcesos() {
        return jdbc.query(ControlCargasQueries.ESTADO_PROCESOS, Map.of(), this::extraer);
    }

    private List<ProcesoCarga> extraer(ResultSet rs) throws SQLException {
        Map<String, Integer> columnas = columnasPorNombre(rs);

        int iProceso = resolverIndice(columnas, CAND_PROCESO);
        int iFecAct = resolverIndice(columnas, CAND_FEC_ACT);
        int iFecRep = resolverIndice(columnas, CAND_FEC_REP);
        int iEstado = resolverIndice(columnas, CAND_ESTADO);

        if (iProceso < 0 || iEstado < 0) {
            if (columnas.size() >= 4) {
                // Mismo orden que exponía el módulo Node.js: proceso, fec_act, fec_rep, estado.
                log.warn("mod_rep.com.RSRPD001 devolvió columnas con otros nombres {}; se mapea por posición 1..4",
                        columnas.keySet());
                iProceso = 1;
                iFecAct = 2;
                iFecRep = 3;
                iEstado = 4;
            } else {
                throw new ReporteException(
                        "No se reconocen las columnas de mod_rep.com.RSRPD001 @OPT='2'. Columnas devueltas: "
                                + columnas.keySet() + ". Ajustar el mapeo en ControlCargasRepository.");
            }
        }

        var filas = new ArrayList<ProcesoCarga>();
        while (rs.next()) {
            filas.add(new ProcesoCarga(
                    RowMapperUtils.texto(rs, iProceso),
                    iFecAct > 0 ? RowMapperUtils.fecha(rs, iFecAct) : null,
                    iFecRep > 0 ? RowMapperUtils.fecha(rs, iFecRep) : null,
                    RowMapperUtils.texto(rs, iEstado)));
        }
        return filas;
    }

    private static Map<String, Integer> columnasPorNombre(ResultSet rs) throws SQLException {
        var meta = rs.getMetaData();
        var columnas = new LinkedHashMap<String, Integer>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            columnas.putIfAbsent(meta.getColumnLabel(i).trim().toLowerCase(), i);
        }
        return columnas;
    }

    static int resolverIndice(Map<String, Integer> columnas, String... candidatos) {
        for (String candidato : candidatos) {
            Integer indice = columnas.get(candidato);
            if (indice != null) {
                return indice;
            }
        }
        return -1;
    }
}
