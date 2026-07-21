package pe.confianza.reportes.service.saldomediovigente;

import org.springframework.stereotype.Service;
import pe.confianza.reportes.excel.ExcelSheetSpec;
import pe.confianza.reportes.repository.SaldoMedioVigenteRepository;
import pe.confianza.reportes.service.ReporteService;
import pe.confianza.reportes.service.ReporteSupport;
import pe.confianza.reportes.shared.ReporteException;
import pe.confianza.reportes.shared.ReporteResultado;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static pe.confianza.reportes.excel.ExcelSheetSpec.col;
import static pe.confianza.reportes.excel.FormatoCelda.FECHA;
import static pe.confianza.reportes.excel.FormatoCelda.MONTO;

@Service
public class SaldoMedioVigenteService implements ReporteService {

    private static final long TIMEOUT_MINUTOS = 15;

    private final SaldoMedioVigenteRepository repository;
    private final ReporteSupport support;

    public SaldoMedioVigenteService(SaldoMedioVigenteRepository repository, ReporteSupport support) {
        this.repository = repository;
        this.support = support;
    }

    @Override
    public String codigo() {
        return "saldo-medio-vigente";
    }

    @Override
    public ReporteResultado generar(LocalDate corte) {
        long inicio = System.currentTimeMillis();

        // Ambas consultas se lanzan primero y recién después se espera (RN-01).
        var fSaldoMedio = repository.consultarSaldoMedioVigente(corte);
        var fSerieDiaria = repository.consultarSaldoVigenteDiario(corte);
        try {
            CompletableFuture.allOf(fSaldoMedio, fSerieDiaria)
                    .orTimeout(TIMEOUT_MINUTOS, TimeUnit.MINUTES)
                    .join();
        } catch (CompletionException e) {
            throw new ReporteException("Fallo en consultas de " + codigo(), e.getCause());
        }

        var hojas = List.<ExcelSheetSpec<?>>of(
                ExcelSheetSpec.de("Saldo Medio Vigente", columnasSaldoMedio(), fSaldoMedio.join()),
                ExcelSheetSpec.de("Saldo Vigente Diario", columnasSerieDiaria(), fSerieDiaria.join()));
        return support.completar(codigo(), corte, hojas, inicio);
    }

    private static List<ExcelSheetSpec.ColumnaSpec<SaldoMedioVigenteFila>> columnasSaldoMedio() {
        return List.of(
                col("HFECPRO", SaldoMedioVigenteFila::fecha, FECHA, 12),
                col("HSALMEDMNVIGE", SaldoMedioVigenteFila::saldoMedioVigente, MONTO, 18));
    }

    private static List<ExcelSheetSpec.ColumnaSpec<SaldoVigenteDiarioFila>> columnasSerieDiaria() {
        return List.of(
                col("sfecpro", SaldoVigenteDiarioFila::fecha, FECHA, 12),
                col("ssalvigmn", SaldoVigenteDiarioFila::saldoVigente, MONTO, 18));
    }
}
