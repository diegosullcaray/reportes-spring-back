package pe.confianza.reportes.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import pe.confianza.reportes.repository.support.RowMapperUtils;
import pe.confianza.reportes.service.datoscierre.CeIndicadorFila;
import pe.confianza.reportes.service.datoscierre.ClienteRuralMigranteFila;
import pe.confianza.reportes.service.datoscierre.DatosCierreQueries;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public class DatosCierreRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DatosCierreRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<CeIndicadorFila>> consultarCeHabilitados(LocalDate corte) {
        return CompletableFuture.completedFuture(
                jdbc.query(DatosCierreQueries.CE_HABILITADOS,
                        new MapSqlParameterSource("corte", corte), this::mapearCe));
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<CeIndicadorFila>> consultarCeDesembolsos(LocalDate corte) {
        return CompletableFuture.completedFuture(
                jdbc.query(DatosCierreQueries.CE_DESEMBOLSOS,
                        new MapSqlParameterSource("corte", corte), this::mapearCe));
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<ClienteRuralMigranteFila>> consultarClientesRuralesMigrantes(LocalDate corte) {
        return CompletableFuture.completedFuture(
                jdbc.query(DatosCierreQueries.CLIENTES_RURALES_MIGRANTES,
                        new MapSqlParameterSource("corte", corte), (rs, i) ->
                                new ClienteRuralMigranteFila(
                                        RowMapperUtils.fecha(rs, "HFECPRO"),
                                        RowMapperUtils.texto(rs, "HINDRUR"),
                                        RowMapperUtils.texto(rs, "HINDMIG"),
                                        RowMapperUtils.entero(rs, "NROCLI"))));
    }

    private CeIndicadorFila mapearCe(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new CeIndicadorFila(
                RowMapperUtils.texto(rs, "concepto"),
                RowMapperUtils.entero(rs, "numope"),
                RowMapperUtils.decimal(rs, "mondesmn"));
    }
}
