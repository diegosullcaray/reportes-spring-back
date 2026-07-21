package pe.confianza.reportes_programados.task_reportes_back.service.carteravigenteagro;

import org.springframework.stereotype.Service;

import pe.confianza.reportes_programados.task_reportes_back.excel.ExcelSheetSpec;
import pe.confianza.reportes_programados.task_reportes_back.repository.CarteraVigenteAgroRepository;
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
public class CarteraVigenteAgroService implements ReporteService {

    private static final long TIMEOUT_MINUTOS = 15;

    private final CarteraVigenteAgroRepository repository;
    private final ReporteSupport support;

    public CarteraVigenteAgroService(CarteraVigenteAgroRepository repository, ReporteSupport support) {
        this.repository = repository;
        this.support = support;
    }

    @Override
    public String codigo() {
        return "cartera-vigente-agro";
    }

    @Override
    public ReporteResultado generar(LocalDate corte) {
        long inicio = System.currentTimeMillis();

        // Las tres consultas se lanzan primero y recién después se espera (RN-01).
        var fActual = repository.consultarSaldoVigenteActual(corte);
        var fAnterior = repository.consultarSaldoVigenteAnterior(corte);
        var fClientes = repository.consultarClientes(corte);
        try {
            CompletableFuture.allOf(fActual, fAnterior, fClientes)
                    .orTimeout(TIMEOUT_MINUTOS, TimeUnit.MINUTES)
                    .join();
        } catch (CompletionException e) {
            throw new ReporteException("Fallo en consultas de " + codigo(), e.getCause());
        }

        var hojas = List.<ExcelSheetSpec<?>>of(
                ExcelSheetSpec.de("Saldo Vigente", columnasSaldo(), fActual.join()),
                ExcelSheetSpec.de("Saldo Vigente Mes Anterior", columnasSaldo(), fAnterior.join()),
                ExcelSheetSpec.de("Clientes", columnasClientes(), fClientes.join()));
        return support.completar(codigo(), corte, hojas, inicio);
    }

    private static List<ExcelSheetSpec.ColumnaSpec<SaldoVigenteAgroFila>> columnasSaldo() {
        return List.of(
                col("fecha", SaldoVigenteAgroFila::fecha, FECHA, 12),
                col("grupo", SaldoVigenteAgroFila::grupo, TEXTO, 14),
                col("territorio", SaldoVigenteAgroFila::territorio, TEXTO, 16),
                col("corredor", SaldoVigenteAgroFila::corredor, TEXTO, 24),
                col("unidad", SaldoVigenteAgroFila::unidad, TEXTO, 24),
                col("sectorista", SaldoVigenteAgroFila::sectorista, TEXTO, 32),
                col("saldo_vigente", SaldoVigenteAgroFila::saldoVigente, MONTO, 16));
    }

    private static List<ExcelSheetSpec.ColumnaSpec<ClientesAgroFila>> columnasClientes() {
        return List.of(
                col("fecha", ClientesAgroFila::fecha, FECHA, 12),
                col("grupo", ClientesAgroFila::grupo, TEXTO, 14),
                col("territorio", ClientesAgroFila::territorio, TEXTO, 16),
                col("corredor", ClientesAgroFila::corredor, TEXTO, 24),
                col("unidad", ClientesAgroFila::unidad, TEXTO, 24),
                col("sectorista", ClientesAgroFila::sectorista, TEXTO, 32),
                col("CierreMesAnterior", ClientesAgroFila::cierreMesAnterior, ENTERO, 16),
                col("CierreActual", ClientesAgroFila::cierreActual, ENTERO, 14),
                col("LOGICA", ClientesAgroFila::variacion, ENTERO, 10));
    }
}
