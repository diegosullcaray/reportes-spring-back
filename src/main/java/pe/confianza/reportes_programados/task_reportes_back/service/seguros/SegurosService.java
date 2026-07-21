package pe.confianza.reportes_programados.task_reportes_back.service.seguros;

import org.springframework.stereotype.Service;

import pe.confianza.reportes_programados.task_reportes_back.excel.ExcelSheetSpec;
import pe.confianza.reportes_programados.task_reportes_back.repository.SegurosRepository;
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

@Service
public class SegurosService implements ReporteService {

    private static final long TIMEOUT_MINUTOS = 15;

    private final SegurosRepository repository;
    private final ReporteSupport support;

    public SegurosService(SegurosRepository repository, ReporteSupport support) {
        this.repository = repository;
        this.support = support;
    }

    @Override
    public String codigo() {
        return "reporte-seguros";
    }

    @Override
    public ReporteResultado generar(LocalDate corte) {
        long inicio = System.currentTimeMillis();

        var fSeguros = repository.consultarPenetracionSeguros(corte);
        try {
            CompletableFuture.allOf(fSeguros)
                    .orTimeout(TIMEOUT_MINUTOS, TimeUnit.MINUTES)
                    .join();
        } catch (CompletionException e) {
            throw new ReporteException("Fallo en consultas de " + codigo(), e.getCause());
        }

        var hojas = List.<ExcelSheetSpec<?>>of(
                ExcelSheetSpec.de("Penetracion Seguros", columnas(), fSeguros.join()));
        return support.completar(codigo(), corte, hojas, inicio);
    }

    private static List<ExcelSheetSpec.ColumnaSpec<SegurosFila>> columnas() {
        var cols = new ArrayList<ExcelSheetSpec.ColumnaSpec<SegurosFila>>();
        cols.add(col("Cod_BT Sectorista", SegurosFila::codSectorista, TEXTO, 16));
        cols.add(col("Sectorista", SegurosFila::sectorista, TEXTO, 32));
        cols.add(col("Grupo", SegurosFila::grupo, TEXTO, 16));
        cols.add(col("Corredor", SegurosFila::corredor, TEXTO, 22));
        cols.add(col("Territorio", SegurosFila::territorio, TEXTO, 18));

        cols.add(col("Total Operaciones", f -> f.resumen().totalOperaciones(), ENTERO, 14));
        cols.add(col("Total Seguros", f -> f.resumen().totalSeguros(), ENTERO, 12));
        cols.add(col("%Penetracion", f -> f.resumen().penetracionTotal(), PORCENTAJE, 12));
        cols.add(col("Multiriesgo", f -> f.resumen().multiriesgo(), ENTERO, 11));
        cols.add(col("Multicredito", f -> f.resumen().multicredito(), ENTERO, 11));
        cols.add(col("Protección de cuota", f -> f.resumen().proteccionCuota(), ENTERO, 16));
        cols.add(col("Agropecuario", f -> f.resumen().seguroAgro(), ENTERO, 12));
        cols.add(col("ONCO", f -> f.resumen().onco(), ENTERO, 8));
        cols.add(col("Emprendiendo Confianza", f -> f.resumen().emprendiendoConfianza(), ENTERO, 18));
        cols.add(col("Construyendo Confianza", f -> f.resumen().construyendoConfianza(), ENTERO, 18));
        cols.add(col("Agropecuario (Seg)", f -> f.resumen().agropecuario(), ENTERO, 14));
        cols.add(col("Consumo", f -> f.resumen().consumo(), ENTERO, 10));
        cols.add(col("Credito Educativo", f -> f.resumen().creditoEducativo(), ENTERO, 14));
        cols.add(col("Iniciando Oficios", f -> f.resumen().iniciandoOficios(), ENTERO, 14));
        cols.add(col("Meta", f -> f.resumen().meta(), MONTO, 10));
        cols.add(col("Avance", f -> f.resumen().avance(), PORCENTAJE, 10));

        cols.add(col("PYME-CC Total Operaciones", f -> f.pymeCc().totalOperaciones(), ENTERO, 16));
        cols.add(col("PYME-CC Total Seguros", f -> f.pymeCc().totalSeguros(), ENTERO, 14));
        cols.add(col("PYME-CC Seguro Multiriesgo", f -> f.pymeCc().seguroMultiriesgo(), ENTERO, 16));
        cols.add(col("PYME-CC Seguro Multicredito", f -> f.pymeCc().seguroMulticredito(), ENTERO, 16));
        cols.add(col("PYME-CC Seguro Protección Cuota", f -> f.pymeCc().seguroProteccionCuota(), ENTERO, 18));

        cols.add(col("Agro Total Operaciones", f -> f.agro().totalOperaciones(), ENTERO, 14));
        cols.add(col("Agro Total Seguros", f -> f.agro().totalSeguros(), ENTERO, 12));
        cols.add(col("Agro % Penetración", f -> f.agro().penetracion(), PORCENTAJE, 12));
        cols.add(col("Agro Seguro Multiriesgo", f -> f.agro().seguroMultiriesgo(), ENTERO, 14));
        cols.add(col("Agro Seguro Multicredito", f -> f.agro().seguroMulticredito(), ENTERO, 14));
        cols.add(col("Agro Seguro PC", f -> f.agro().seguroProteccionCuota(), ENTERO, 12));
        cols.add(col("Agro Seguro Agro", f -> f.agro().seguroAgro(), ENTERO, 12));

        cols.add(col("Consumo Total Operaciones", f -> f.consumo().totalOperaciones(), ENTERO, 16));
        cols.add(col("Consumo Total Seguros", f -> f.consumo().totalSeguros(), ENTERO, 14));
        cols.add(col("Consumo % Penetración", f -> f.consumo().penetracion(), PORCENTAJE, 14));
        cols.add(col("Consumo Seguro MC", f -> f.consumo().seguroMulticredito(), ENTERO, 12));
        cols.add(col("Consumo Seguro PC", f -> f.consumo().seguroProteccionCuota(), ENTERO, 12));

        cols.add(col("CE Total Operaciones", f -> f.creditoEducativo().totalOperaciones(), ENTERO, 14));
        cols.add(col("CE Total Seguros", f -> f.creditoEducativo().totalSeguros(), ENTERO, 12));
        cols.add(col("CE % Penetración", f -> f.creditoEducativo().penetracion(), PORCENTAJE, 12));

        cols.add(col("IO Total Operaciones", f -> f.iniciandoOficios().totalOperaciones(), ENTERO, 14));
        cols.add(col("IO Total Seguros", f -> f.iniciandoOficios().totalSeguros(), ENTERO, 12));
        cols.add(col("IO % Penetración", f -> f.iniciandoOficios().penetracion(), PORCENTAJE, 12));
        cols.add(col("IO Seguro MR", f -> f.iniciandoOficios().seguroMultiriesgo(), ENTERO, 10));
        cols.add(col("IO Seguro MC", f -> f.iniciandoOficios().seguroMulticredito(), ENTERO, 10));
        cols.add(col("IO Seguro PC", f -> f.iniciandoOficios().seguroProteccionCuota(), ENTERO, 10));
        return cols;
    }
}
