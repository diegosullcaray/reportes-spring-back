package pe.confianza.reportes_programados.task_reportes_back.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import pe.confianza.reportes_programados.task_reportes_back.repository.support.RowMapperUtils;
import pe.confianza.reportes_programados.task_reportes_back.service.sacatugarra.SacaTuGarraFila;
import pe.confianza.reportes_programados.task_reportes_back.service.sacatugarra.SacaTuGarraQueries;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public class SacaTuGarraRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SacaTuGarraRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<SacaTuGarraFila>> consultarBase(LocalDate corte) {
        var params = new MapSqlParameterSource("corte", corte);
        var filas = jdbc.query(SacaTuGarraQueries.BASE_SACA_TU_GARRA, params, (rs, i) ->
                new SacaTuGarraFila(
                        RowMapperUtils.texto(rs, "HASEOPER"),
                        RowMapperUtils.decimal(rs, "VAR_VIGENTE"),
                        RowMapperUtils.entero(rs, "PRODUCTIVDAD"),
                        RowMapperUtils.decimal(rs, "RatioRecuperacion0_30"),
                        RowMapperUtils.decimal(rs, "RatioRecuperacion1_30")));
        return CompletableFuture.completedFuture(filas);
    }
}
