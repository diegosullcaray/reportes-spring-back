package pe.confianza.reportes.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import pe.confianza.reportes.repository.support.RowMapperUtils;
import pe.confianza.reportes.service.saldomediovigente.SaldoMedioVigenteFila;
import pe.confianza.reportes.service.saldomediovigente.SaldoMedioVigenteQueries;
import pe.confianza.reportes.service.saldomediovigente.SaldoVigenteDiarioFila;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public class SaldoMedioVigenteRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SaldoMedioVigenteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<SaldoMedioVigenteFila>> consultarSaldoMedioVigente(LocalDate corte) {
        var params = new MapSqlParameterSource("corte", corte);
        var filas = jdbc.query(SaldoMedioVigenteQueries.SALDO_MEDIO_VIGENTE, params, (rs, i) ->
                new SaldoMedioVigenteFila(
                        RowMapperUtils.fecha(rs, "HFECPRO"),
                        RowMapperUtils.decimal(rs, "HSALMEDMNVIGE")));
        return CompletableFuture.completedFuture(filas);
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<SaldoVigenteDiarioFila>> consultarSaldoVigenteDiario(LocalDate corte) {
        var params = new MapSqlParameterSource("corte", corte);
        var filas = jdbc.query(SaldoMedioVigenteQueries.SALDO_VIGENTE_DIARIO, params, (rs, i) ->
                new SaldoVigenteDiarioFila(
                        RowMapperUtils.fecha(rs, "sfecpro"),
                        RowMapperUtils.decimal(rs, "ssalvigmn")));
        return CompletableFuture.completedFuture(filas);
    }
}
