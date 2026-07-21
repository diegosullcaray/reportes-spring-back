package pe.confianza.reportes.service.sacatugarra;

import org.springframework.stereotype.Service;
import pe.confianza.reportes.excel.ExcelSheetSpec;
import pe.confianza.reportes.repository.SacaTuGarraRepository;
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
import static pe.confianza.reportes.excel.FormatoCelda.MONTO;
import static pe.confianza.reportes.excel.FormatoCelda.PORCENTAJE;
import static pe.confianza.reportes.excel.FormatoCelda.TEXTO;

@Service
public class SacaTuGarraService implements ReporteService {

    private static final long TIMEOUT_MINUTOS = 15;

    private final SacaTuGarraRepository repository;
    private final ReporteSupport support;

    public SacaTuGarraService(SacaTuGarraRepository repository, ReporteSupport support) {
        this.repository = repository;
        this.support = support;
    }

    @Override
    public String codigo() {
        return "saca-tu-garra";
    }

    @Override
    public ReporteResultado generar(LocalDate corte) {
        long inicio = System.currentTimeMillis();

        var fBase = repository.consultarBase(corte);
        try {
            CompletableFuture.allOf(fBase)
                    .orTimeout(TIMEOUT_MINUTOS, TimeUnit.MINUTES)
                    .join();
        } catch (CompletionException e) {
            throw new ReporteException("Fallo en consultas de " + codigo(), e.getCause());
        }

        var hojas = List.<ExcelSheetSpec<?>>of(
                ExcelSheetSpec.de("BASE", columnas(), fBase.join()));
        return support.completar(codigo(), corte, hojas, inicio);
    }

    private static List<ExcelSheetSpec.ColumnaSpec<SacaTuGarraFila>> columnas() {
        return List.of(
                col("Usuario", SacaTuGarraFila::usuario, TEXTO, 14),
                col("Var. Saldo Vigente", SacaTuGarraFila::varSaldoVigente, MONTO, 18),
                col("Productividad", SacaTuGarraFila::productividad, ENTERO, 14),
                col("Efectividad -30 a 0", SacaTuGarraFila::efectividadMenos30a0, PORCENTAJE, 18),
                col("Efectividad 1 a 30", SacaTuGarraFila::efectividad1a30, PORCENTAJE, 18));
    }
}
