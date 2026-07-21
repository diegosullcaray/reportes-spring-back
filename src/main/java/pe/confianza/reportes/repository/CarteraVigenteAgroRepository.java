package pe.confianza.reportes.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import pe.confianza.reportes.repository.support.RowMapperUtils;
import pe.confianza.reportes.service.carteravigenteagro.CarteraVigenteAgroQueries;
import pe.confianza.reportes.service.carteravigenteagro.ClientesAgroFila;
import pe.confianza.reportes.service.carteravigenteagro.SaldoVigenteAgroFila;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public class CarteraVigenteAgroRepository {

    private static final RowMapper<SaldoVigenteAgroFila> MAPPER_SALDO = (rs, i) ->
            new SaldoVigenteAgroFila(
                    RowMapperUtils.fecha(rs, "fecha"),
                    RowMapperUtils.texto(rs, "grupo"),
                    RowMapperUtils.texto(rs, "territorio"),
                    RowMapperUtils.texto(rs, "corredor"),
                    RowMapperUtils.texto(rs, "unidad"),
                    RowMapperUtils.texto(rs, "sectorista"),
                    RowMapperUtils.decimal(rs, 7));

    private final NamedParameterJdbcTemplate jdbc;

    public CarteraVigenteAgroRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<SaldoVigenteAgroFila>> consultarSaldoVigenteActual(LocalDate corte) {
        return CompletableFuture.completedFuture(
                jdbc.query(CarteraVigenteAgroQueries.SALDO_VIGENTE_ACTUAL,
                        new MapSqlParameterSource("corte", corte), MAPPER_SALDO));
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<SaldoVigenteAgroFila>> consultarSaldoVigenteAnterior(LocalDate corte) {
        return CompletableFuture.completedFuture(
                jdbc.query(CarteraVigenteAgroQueries.SALDO_VIGENTE_ANTERIOR,
                        new MapSqlParameterSource("corte", corte), MAPPER_SALDO));
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<ClientesAgroFila>> consultarClientes(LocalDate corte) {
        return CompletableFuture.completedFuture(
                jdbc.query(CarteraVigenteAgroQueries.CLIENTES,
                        new MapSqlParameterSource("corte", corte), (rs, i) ->
                                new ClientesAgroFila(
                                        RowMapperUtils.fecha(rs, "fecha"),
                                        RowMapperUtils.texto(rs, "grupo"),
                                        RowMapperUtils.texto(rs, "territorio"),
                                        RowMapperUtils.texto(rs, "corredor"),
                                        RowMapperUtils.texto(rs, "unidad"),
                                        RowMapperUtils.texto(rs, "sectorista"),
                                        RowMapperUtils.entero(rs, "CierreMesAnterior"),
                                        RowMapperUtils.entero(rs, "CierreActual"),
                                        RowMapperUtils.entero(rs, "LOGICA"))));
    }
}
