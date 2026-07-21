package pe.confianza.reportes_programados.task_reportes_back.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import pe.confianza.reportes_programados.task_reportes_back.repository.support.RowMapperUtils;
import pe.confianza.reportes_programados.task_reportes_back.service.carteraheredada.CarteraHeredadaFila;
import pe.confianza.reportes_programados.task_reportes_back.service.carteraheredada.CarteraHeredadaQueries;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public class CarteraHeredadaRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CarteraHeredadaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<CarteraHeredadaFila>> consultarStockHeredado(LocalDate corte) {
        var params = new MapSqlParameterSource("corte", corte.format(RowMapperUtils.YYYYMMDD));
        var filas = jdbc.query(CarteraHeredadaQueries.STOCK_HEREDADO_PDM, params, (rs, i) ->
                new CarteraHeredadaFila(
                        RowMapperUtils.fecha(rs, "HFECPRO"),
                        RowMapperUtils.largo(rs, "BCODOPE"),
                        RowMapperUtils.entero(rs, "BCODMOD"),
                        RowMapperUtils.entero(rs, "BTIPOPE"),
                        RowMapperUtils.entero(rs, "BSUBTIP"),
                        RowMapperUtils.texto(rs, "BCODUBT"),
                        RowMapperUtils.texto(rs, "RSECOPE"),
                        RowMapperUtils.largo(rs, "BNUMGRU"),
                        RowMapperUtils.texto(rs, "SNOMG"),
                        RowMapperUtils.entero(rs, "HFLAG")));
        return CompletableFuture.completedFuture(filas);
    }
}
