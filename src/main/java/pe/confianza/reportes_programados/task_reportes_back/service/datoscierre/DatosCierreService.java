package pe.confianza.reportes_programados.task_reportes_back.service.datoscierre;

import org.springframework.stereotype.Service;

import pe.confianza.reportes_programados.task_reportes_back.excel.ExcelSheetSpec;
import pe.confianza.reportes_programados.task_reportes_back.repository.DatosCierreRepository;
import pe.confianza.reportes_programados.task_reportes_back.service.ReporteService;
import pe.confianza.reportes_programados.task_reportes_back.service.ReporteSupport;
import pe.confianza.reportes_programados.task_reportes_back.shared.ReporteException;
import pe.confianza.reportes_programados.task_reportes_back.shared.ReporteResultado;

import static pe.confianza.reportes_programados.task_reportes_back.excel.ExcelSheetSpec.*;
import static pe.confianza.reportes_programados.task_reportes_back.excel.FormatoCelda.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Reporte "Datos Cierre": ratio CE, clientes nuevos rurales y migrantes. */
@Service
public class DatosCierreService implements ReporteService {

    private static final long TIMEOUT_MINUTOS = 15;

    private final DatosCierreRepository repository;
    private final ReporteSupport support;

    public DatosCierreService(DatosCierreRepository repository, ReporteSupport support) {
        this.repository = repository;
        this.support = support;
    }

    @Override
    public String codigo() {
        return "datos-cierre";
    }

    @Override
    public ReporteResultado generar(LocalDate corte) {
        long inicio = System.currentTimeMillis();

        // Las tres consultas se lanzan primero y recién después se espera (RN-01).
        var fHabilitados = repository.consultarCeHabilitados(corte);
        var fDesembolsos = repository.consultarCeDesembolsos(corte);
        var fClientes = repository.consultarClientesRuralesMigrantes(corte);
        try {
            CompletableFuture.allOf(fHabilitados, fDesembolsos, fClientes)
                    .orTimeout(TIMEOUT_MINUTOS, TimeUnit.MINUTES)
                    .join();
        } catch (CompletionException e) {
            throw new ReporteException("Fallo en consultas de " + codigo(), e.getCause());
        }

        var indicadoresCe = new ArrayList<CeIndicadorFila>();
        indicadoresCe.addAll(fHabilitados.join());
        indicadoresCe.addAll(fDesembolsos.join());

        var hojas = List.<ExcelSheetSpec<?>>of(
                ExcelSheetSpec.de("Ratio CE", columnasCe(), indicadoresCe),
                ExcelSheetSpec.de("Clientes Rurales Migrantes", columnasClientes(), fClientes.join()));
        return support.completar(codigo(), corte, hojas, inicio);
    }

    private static List<ExcelSheetSpec.ColumnaSpec<CeIndicadorFila>> columnasCe() {
        return List.of(
                col("Concepto", CeIndicadorFila::concepto, TEXTO, 30),
                col("NumOperaciones", CeIndicadorFila::numOperaciones, ENTERO, 16),
                col("MontoDesembolsadoMN", CeIndicadorFila::montoDesembolsadoMn, MONTO, 20));
    }

    private static List<ExcelSheetSpec.ColumnaSpec<ClienteRuralMigranteFila>> columnasClientes() {
        return List.of(
                col("HFECPRO", ClienteRuralMigranteFila::fecha, FECHA, 12),
                col("HINDRUR", ClienteRuralMigranteFila::indicadorRural, TEXTO, 16),
                col("HINDMIG", ClienteRuralMigranteFila::indicadorMigrante, TEXTO, 12),
                col("NROCLI", ClienteRuralMigranteFila::numClientes, ENTERO, 10));
    }
}
