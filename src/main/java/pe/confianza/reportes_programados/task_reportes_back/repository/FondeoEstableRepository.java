package pe.confianza.reportes_programados.task_reportes_back.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import pe.confianza.reportes_programados.task_reportes_back.repository.support.RowMapperUtils;
import pe.confianza.reportes_programados.task_reportes_back.service.fondeoestable.FondeoEstableFila;
import pe.confianza.reportes_programados.task_reportes_back.service.fondeoestable.FondeoEstableQueries;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public class FondeoEstableRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public FondeoEstableRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<FondeoEstableFila>> consultarSaldoFondeoEstable(LocalDate corte) {
        var params = new MapSqlParameterSource("corte", corte);
        var filas = jdbc.query(FondeoEstableQueries.SALDO_FONDEO_ESTABLE, params, (rs, i) ->
                new FondeoEstableFila(
                        RowMapperUtils.fecha(rs, "FECHA"),
                        RowMapperUtils.texto(rs, "RDESMAT"),
                        RowMapperUtils.decimal(rs, "HSALFESI")));
        return CompletableFuture.completedFuture(filas);
    }
}
