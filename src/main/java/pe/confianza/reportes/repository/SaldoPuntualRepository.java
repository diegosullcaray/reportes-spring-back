package pe.confianza.reportes.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import pe.confianza.reportes.repository.support.RowMapperUtils;
import pe.confianza.reportes.service.saldopuntual.SaldoMedioFila;
import pe.confianza.reportes.service.saldopuntual.SaldoPuntualFila;
import pe.confianza.reportes.service.saldopuntual.SaldoPuntualQueries;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public class SaldoPuntualRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SaldoPuntualRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<SaldoPuntualFila>> consultarSaldoPuntual(LocalDate corte) {
        var params = new MapSqlParameterSource("corte", corte);
        var filas = jdbc.query(SaldoPuntualQueries.SALDO_PUNTUAL, params, (rs, i) ->
                new SaldoPuntualFila(
                        RowMapperUtils.entero(rs, "RCODAGEH"),
                        RowMapperUtils.texto(rs, "RDESAGEH"),
                        RowMapperUtils.texto(rs, "RDESCPROD02"),
                        RowMapperUtils.decimal(rs, "SSALMN"),
                        RowMapperUtils.texto(rs, "RDESMAT"),
                        RowMapperUtils.texto(rs, "RDESMAC"),
                        RowMapperUtils.texto(rs, "RDESTER")));
        return CompletableFuture.completedFuture(filas);
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<SaldoMedioFila>> consultarSaldoMedio(LocalDate corte) {
        var params = new MapSqlParameterSource("corte", corte);
        var filas = jdbc.query(SaldoPuntualQueries.SALDO_MEDIO, params, (rs, i) ->
                new SaldoMedioFila(
                        RowMapperUtils.fecha(rs, "hfecpro"),
                        RowMapperUtils.texto(rs, "RDESAGEH"),
                        RowMapperUtils.texto(rs, "RDESCPROD02"),
                        RowMapperUtils.decimal(rs, "HSALMEDMN")));
        return CompletableFuture.completedFuture(filas);
    }
}
