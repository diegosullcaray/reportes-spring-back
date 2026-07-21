package pe.confianza.reportes_programados.task_reportes_back.service.carteraheredada;

import org.springframework.stereotype.Service;

import pe.confianza.reportes_programados.task_reportes_back.excel.ExcelSheetSpec;
import pe.confianza.reportes_programados.task_reportes_back.repository.CarteraHeredadaRepository;
import pe.confianza.reportes_programados.task_reportes_back.service.ReporteService;
import pe.confianza.reportes_programados.task_reportes_back.service.ReporteSupport;
import pe.confianza.reportes_programados.task_reportes_back.shared.ReporteException;
import pe.confianza.reportes_programados.task_reportes_back.shared.ReporteResultado;

import static pe.confianza.reportes_programados.task_reportes_back.excel.ExcelSheetSpec.*;
import static pe.confianza.reportes_programados.task_reportes_back.excel.FormatoCelda.*;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Service
public class CarteraHeredadaService implements ReporteService {

    private static final long TIMEOUT_MINUTOS = 15;

    private final CarteraHeredadaRepository repository;
    private final ReporteSupport support;

    public CarteraHeredadaService(CarteraHeredadaRepository repository, ReporteSupport support) {
        this.repository = repository;
        this.support = support;
    }

    @Override
    public String codigo() {
        return "cartera-heredada";
    }

    @Override
    public ReporteResultado generar(LocalDate corte) {
        long inicio = System.currentTimeMillis();

        var fStock = repository.consultarStockHeredado(corte);
        try {
            CompletableFuture.allOf(fStock)
                    .orTimeout(TIMEOUT_MINUTOS, TimeUnit.MINUTES)
                    .join();
        } catch (CompletionException e) {
            throw new ReporteException("Fallo en consultas de " + codigo(), e.getCause());
        }

        var hojas = List.<ExcelSheetSpec<?>>of(
                ExcelSheetSpec.de("PDM Heredado", columnas(), fStock.join()));
        return support.completar(codigo(), corte, hojas, inicio);
    }

    private static List<ExcelSheetSpec.ColumnaSpec<CarteraHeredadaFila>> columnas() {
        return List.of(
                col("FechaCierre", CarteraHeredadaFila::fechaCierre, FECHA, 12),
                col("Operación", CarteraHeredadaFila::operacion, ENTERO, 12),
                col("Modulo", CarteraHeredadaFila::modulo, ENTERO, 8),
                col("TipOpe", CarteraHeredadaFila::tipOpe, ENTERO, 8),
                col("SubTipo", CarteraHeredadaFila::subTipo, ENTERO, 8),
                col("AsesorOper", CarteraHeredadaFila::asesorOper, TEXTO, 14),
                col("AsesorOrig", CarteraHeredadaFila::asesorOrig, TEXTO, 14),
                col("NumGrupo", CarteraHeredadaFila::numGrupo, ENTERO, 10),
                col("NomGrupo", CarteraHeredadaFila::nomGrupo, TEXTO, 30),
                col("IndHeredado", CarteraHeredadaFila::indHeredado, ENTERO, 12));
    }
}
