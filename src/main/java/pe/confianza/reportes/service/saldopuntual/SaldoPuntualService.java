package pe.confianza.reportes.service.saldopuntual;

import org.springframework.stereotype.Service;
import pe.confianza.reportes.excel.ExcelSheetSpec;
import pe.confianza.reportes.repository.SaldoPuntualRepository;
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
import static pe.confianza.reportes.excel.FormatoCelda.ENTERO;
import static pe.confianza.reportes.excel.FormatoCelda.FECHA;
import static pe.confianza.reportes.excel.FormatoCelda.MONTO;
import static pe.confianza.reportes.excel.FormatoCelda.TEXTO;

@Service
public class SaldoPuntualService implements ReporteService {

    private static final long TIMEOUT_MINUTOS = 15;

    private final SaldoPuntualRepository repository;
    private final ReporteSupport support;

    public SaldoPuntualService(SaldoPuntualRepository repository, ReporteSupport support) {
        this.repository = repository;
        this.support = support;
    }

    @Override
    public String codigo() {
        return "saldo-puntual-medio";
    }

    @Override
    public ReporteResultado generar(LocalDate corte) {
        long inicio = System.currentTimeMillis();

        // Ambas consultas se lanzan primero y recién después se espera (RN-01).
        var fPuntual = repository.consultarSaldoPuntual(corte);
        var fMedio = repository.consultarSaldoMedio(corte);
        try {
            CompletableFuture.allOf(fPuntual, fMedio)
                    .orTimeout(TIMEOUT_MINUTOS, TimeUnit.MINUTES)
                    .join();
        } catch (CompletionException e) {
            throw new ReporteException("Fallo en consultas de " + codigo(), e.getCause());
        }

        var hojas = List.<ExcelSheetSpec<?>>of(
                ExcelSheetSpec.de("Saldo Puntual", columnasPuntual(), fPuntual.join()),
                ExcelSheetSpec.de("Saldo Medio", columnasMedio(), fMedio.join()));
        return support.completar(codigo(), corte, hojas, inicio);
    }

    private static List<ExcelSheetSpec.ColumnaSpec<SaldoPuntualFila>> columnasPuntual() {
        return List.of(
                col("RCODAGEH", SaldoPuntualFila::codAgencia, ENTERO, 10),
                col("RDESAGEH", SaldoPuntualFila::agencia, TEXTO, 22),
                col("RDESCPROD02", SaldoPuntualFila::producto, TEXTO, 16),
                col("SSALMN", SaldoPuntualFila::saldoMn, MONTO, 16),
                col("RDESMAT", SaldoPuntualFila::matriz, TEXTO, 22),
                col("RDESMAC", SaldoPuntualFila::macro, TEXTO, 16),
                col("RDESTER", SaldoPuntualFila::territorio, TEXTO, 16));
    }

    private static List<ExcelSheetSpec.ColumnaSpec<SaldoMedioFila>> columnasMedio() {
        return List.of(
                col("hfecpro", SaldoMedioFila::fecha, FECHA, 12),
                col("RDESAGEH", SaldoMedioFila::agencia, TEXTO, 22),
                col("RDESCPROD02", SaldoMedioFila::producto, TEXTO, 16),
                col("HSALMEDMN", SaldoMedioFila::saldoMedioMn, MONTO, 16));
    }
}
