package pe.confianza.reportes_programados.task_reportes_back.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import pe.confianza.reportes_programados.task_reportes_back.service.seguros.SegurosFila;
import pe.confianza.reportes_programados.task_reportes_back.service.seguros.SegurosQueries;

import static pe.confianza.reportes_programados.task_reportes_back.repository.support.RowMapperUtils.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public class SegurosRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SegurosRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<SegurosFila>> consultarPenetracionSeguros(LocalDate corte) {
        var params = new MapSqlParameterSource("corte", corte);
        var filas = jdbc.query(SegurosQueries.PENETRACION_SEGUROS, params, SegurosRepository::mapear);
        return CompletableFuture.completedFuture(filas);
    }

    private static SegurosFila mapear(ResultSet rs, int i) throws SQLException {
        // Validación de negocio: jerarquía comercial nula se reporta como "SIN ASIGNAR".
        return new SegurosFila(
                textoOSinAsignar(rs, "SCODDESCRIP"),
                textoOSinAsignar(rs, "DESCRIPCION"),
                textoOSinAsignar(rs, "RDESGRU"),
                textoOSinAsignar(rs, "RDESCOR"),
                textoOSinAsignar(rs, "RDESTER"),
                new SegurosFila.Resumen(
                        entero(rs, "Total_Ope"),
                        entero(rs, "Total_Seg"),
                        decimal(rs, "P_Pene_Total"),
                        entero(rs, "Multiriesgo"),
                        entero(rs, "MultiCredito"),
                        entero(rs, "Prot_Cuota"),
                        entero(rs, "Agro"),
                        entero(rs, "ONCO"),
                        entero(rs, "EMP_CONFIA"),
                        entero(rs, "CONST_CONFIA"),
                        entero(rs, "AGRO2"),
                        entero(rs, "CONSUMO"),
                        entero(rs, "CRED_EDUC"),
                        entero(rs, "INI_OFICI"),
                        decimal(rs, "METASEG"),
                        decimal(rs, "Avance")),
                new SegurosFila.BloquePymeCc(
                        entero(rs, "T_Ope_Pyme_CC"),
                        entero(rs, "T_SEG_PYME_CC"),
                        entero(rs, "T_SEG_PYME_CC_MR"),
                        entero(rs, "T_SEG_PYME_CC_MC"),
                        entero(rs, "T_SEG_PYME_CC_PC")),
                new SegurosFila.BloqueAgro(
                        entero(rs, "T_Ope_Agro"),
                        entero(rs, "T_SEG_AGRO_T"),
                        decimal(rs, "PEN_AGRO"),
                        entero(rs, "AGRO_MULTIR"),
                        entero(rs, "AGRO_MULTIC"),
                        entero(rs, "AGRO_PC"),
                        entero(rs, "AGRO_agro")),
                new SegurosFila.BloqueConsumo(
                        entero(rs, "T_Ope_Consumo"),
                        entero(rs, "T_SEG_Consumo_T"),
                        decimal(rs, "PEN_Consum"),
                        entero(rs, "MC_Consumo"),
                        entero(rs, "PC_Consumo")),
                new SegurosFila.BloqueCe(
                        entero(rs, "T_Ope_CE"),
                        entero(rs, "T_SEG_CE_T"),
                        decimal(rs, "PEN_CE")),
                new SegurosFila.BloqueIo(
                        entero(rs, "T_Ope_IO"),
                        entero(rs, "T_SEG_IO_T"),
                        decimal(rs, "PEN_IO"),
                        entero(rs, "mr_io"),
                        entero(rs, "mc_io"),
                        entero(rs, "pc_io")));
    }
}
