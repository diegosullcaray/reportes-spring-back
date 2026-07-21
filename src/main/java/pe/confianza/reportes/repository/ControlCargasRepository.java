package pe.confianza.reportes.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.confianza.reportes.repository.support.RowMapperUtils;
import pe.confianza.reportes.service.controlcargas.ControlCargasQueries;
import pe.confianza.reportes.service.controlcargas.ProcesoCarga;

import java.util.List;
import java.util.Map;

/**
 * Consulta síncrona (no @Async): el control de cargas corre cada pocos minutos
 * en su propio hilo del scheduler y es una sola query liviana — no amerita
 * ocupar el pool de reportes.
 */
@Repository
public class ControlCargasRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ControlCargasRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProcesoCarga> consultarEstadoProcesos() {
        return jdbc.query(ControlCargasQueries.ESTADO_PROCESOS, Map.of(), (rs, i) ->
                new ProcesoCarga(
                        RowMapperUtils.texto(rs, "des_pro"),
                        RowMapperUtils.fecha(rs, "fec_act"),
                        RowMapperUtils.fecha(rs, "fec_rep"),
                        RowMapperUtils.texto(rs, "est_pro")));
    }
}
