package pe.confianza.reportes.service.desembolsocanal;

import org.springframework.stereotype.Service;
import pe.confianza.reportes.excel.ExcelSheetSpec;
import pe.confianza.reportes.repository.DesembolsoCanalRepository;
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
import static pe.confianza.reportes.excel.FormatoCelda.TEXTO;

@Service
public class DesembolsoCanalService implements ReporteService {

    private static final long TIMEOUT_MINUTOS = 15;

    private final DesembolsoCanalRepository repository;
    private final ReporteSupport support;

    public DesembolsoCanalService(DesembolsoCanalRepository repository, ReporteSupport support) {
        this.repository = repository;
        this.support = support;
    }

    @Override
    public String codigo() {
        return "desembolso-canal";
    }

    @Override
    public ReporteResultado generar(LocalDate corte) {
        long inicio = System.currentTimeMillis();

        var fDesembolsos = repository.consultarDesembolsosPorCanal(corte);
        try {
            CompletableFuture.allOf(fDesembolsos)
                    .orTimeout(TIMEOUT_MINUTOS, TimeUnit.MINUTES)
                    .join();
        } catch (CompletionException e) {
            throw new ReporteException("Fallo en consultas de " + codigo(), e.getCause());
        }

        var hojas = List.<ExcelSheetSpec<?>>of(
                ExcelSheetSpec.de("Desembolsos Canal", columnas(), fDesembolsos.join()));
        return support.completar(codigo(), corte, hojas, inicio);
    }

    private static List<ExcelSheetSpec.ColumnaSpec<DesembolsoCanalFila>> columnas() {
        return List.of(
                col("RDESTER", DesembolsoCanalFila::territorio, TEXTO, 22),
                col("RDESCOR", DesembolsoCanalFila::corredor, TEXTO, 24),
                col("rdesuni", DesembolsoCanalFila::unidad, TEXTO, 26),
                col("RCODSEC", DesembolsoCanalFila::codSectorista, TEXTO, 12),
                col("RDESSEC", DesembolsoCanalFila::sectorista, TEXTO, 32),
                col("HDTIPOPE", DesembolsoCanalFila::tipoOperacion, TEXTO, 28),
                col("Ope", DesembolsoCanalFila::operaciones, ENTERO, 8),
                col("Monto", DesembolsoCanalFila::monto, MONTO, 14),
                col("canal", DesembolsoCanalFila::canal, TEXTO, 8));
    }
}
