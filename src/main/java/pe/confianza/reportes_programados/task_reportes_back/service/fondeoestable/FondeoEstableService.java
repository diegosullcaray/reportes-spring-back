package pe.confianza.reportes_programados.task_reportes_back.service.fondeoestable;

import org.springframework.stereotype.Service;

import pe.confianza.reportes_programados.task_reportes_back.excel.ExcelSheetSpec;
import pe.confianza.reportes_programados.task_reportes_back.repository.FondeoEstableRepository;
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
public class FondeoEstableService implements ReporteService {

    private static final long TIMEOUT_MINUTOS = 15;

    private final FondeoEstableRepository repository;
    private final ReporteSupport support;

    public FondeoEstableService(FondeoEstableRepository repository, ReporteSupport support) {
        this.repository = repository;
        this.support = support;
    }

    @Override
    public String codigo() {
        return "fondeo-estable";
    }

    @Override
    public ReporteResultado generar(LocalDate corte) {
        long inicio = System.currentTimeMillis();

        var fSaldos = repository.consultarSaldoFondeoEstable(corte);
        try {
            CompletableFuture.allOf(fSaldos)
                    .orTimeout(TIMEOUT_MINUTOS, TimeUnit.MINUTES)
                    .join();
        } catch (CompletionException e) {
            throw new ReporteException("Fallo en consultas de " + codigo(), e.getCause());
        }

        var hojas = List.<ExcelSheetSpec<?>>of(
                ExcelSheetSpec.de("Fondeo Estable", columnas(), fSaldos.join()));
        return support.completar(codigo(), corte, hojas, inicio);
    }

    private static List<ExcelSheetSpec.ColumnaSpec<FondeoEstableFila>> columnas() {
        return List.of(
                col("Fecha", FondeoEstableFila::fecha, FECHA, 12),
                col("Matriz", FondeoEstableFila::matriz, TEXTO, 26),
                col("Saldo Fondeo Estable", FondeoEstableFila::saldoFondeoEstable, MONTO, 20));
    }
}
