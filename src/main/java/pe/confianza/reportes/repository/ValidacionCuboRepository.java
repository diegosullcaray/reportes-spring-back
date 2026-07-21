package pe.confianza.reportes.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import pe.confianza.reportes.repository.support.RowMapperUtils;
import pe.confianza.reportes.service.validacioncubo.CuboSnapshot;
import pe.confianza.reportes.service.validacioncubo.ValidacionCuboQueries;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public class ValidacionCuboRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ValidacionCuboRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Devuelve la foto de indicadores de la fecha; lista vacía si el cubo no tiene esa fecha. */
    @Async("reportTaskExecutor")
    public CompletableFuture<List<CuboSnapshot>> consultarIndicadores(LocalDate fecha) {
        var params = new MapSqlParameterSource("corte", fecha);
        var filas = jdbc.query(ValidacionCuboQueries.INDICADORES_CUBO, params, (rs, i) ->
                new CuboSnapshot(
                        RowMapperUtils.fecha(rs, "FecCierre"),
                        RowMapperUtils.decimal(rs, "SumMonDesembolsoMN"),
                        RowMapperUtils.decimal(rs, "TasaMesCre"),
                        RowMapperUtils.decimal(rs, "TasaMinMesCre"),
                        RowMapperUtils.decimal(rs, "TicketPromMesMN"),
                        RowMapperUtils.decimal(rs, "SumMonApertura"),
                        RowMapperUtils.decimal(rs, "ContClientesCreRepro"),
                        RowMapperUtils.decimal(rs, "SumSalCapitalCreReproMN")));
        return CompletableFuture.completedFuture(filas);
    }
}
