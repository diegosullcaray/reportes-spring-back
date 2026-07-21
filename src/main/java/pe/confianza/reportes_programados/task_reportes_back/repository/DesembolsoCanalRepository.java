package pe.confianza.reportes_programados.task_reportes_back.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import pe.confianza.reportes_programados.task_reportes_back.repository.support.RowMapperUtils;
import pe.confianza.reportes_programados.task_reportes_back.service.desembolsocanal.DesembolsoCanalFila;
import pe.confianza.reportes_programados.task_reportes_back.service.desembolsocanal.DesembolsoCanalQueries;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public class DesembolsoCanalRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DesembolsoCanalRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<DesembolsoCanalFila>> consultarDesembolsosPorCanal(LocalDate corte) {
        var params = new MapSqlParameterSource("corte", corte);
        var filas = jdbc.query(DesembolsoCanalQueries.DESEMBOLSOS_POR_CANAL, params, (rs, i) ->
                new DesembolsoCanalFila(
                        RowMapperUtils.texto(rs, "RDESTER"),
                        RowMapperUtils.texto(rs, "RDESCOR"),
                        RowMapperUtils.texto(rs, "rdesuni"),
                        RowMapperUtils.texto(rs, "RCODSEC"),
                        RowMapperUtils.texto(rs, "RDESSEC"),
                        RowMapperUtils.texto(rs, "HDTIPOPE"),
                        RowMapperUtils.entero(rs, "Ope"),
                        RowMapperUtils.decimal(rs, "Monto"),
                        RowMapperUtils.texto(rs, "canal")));
        return CompletableFuture.completedFuture(filas);
    }
}
