package pe.confianza.reportes_programados.task_reportes_back.service.validacioncubo;

import org.springframework.stereotype.Service;

import pe.confianza.reportes_programados.task_reportes_back.excel.ExcelSheetSpec;
import pe.confianza.reportes_programados.task_reportes_back.repository.ValidacionCuboRepository;
import pe.confianza.reportes_programados.task_reportes_back.service.ReporteService;
import pe.confianza.reportes_programados.task_reportes_back.service.ReporteSupport;
import pe.confianza.reportes_programados.task_reportes_back.shared.ReporteException;
import pe.confianza.reportes_programados.task_reportes_back.shared.ReporteResultado;

import static pe.confianza.reportes_programados.task_reportes_back.excel.ExcelSheetSpec.*;
import static pe.confianza.reportes_programados.task_reportes_back.excel.FormatoCelda.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Validación diaria del cubo comercial: compara los indicadores del corte contra
 * el día anterior y aplica reglas de consistencia. Reglas:
 * <ul>
 *   <li>ALERTA si el cubo no tiene datos para la fecha de corte.</li>
 *   <li>ALERTA si un indicador viene nulo o negativo.</li>
 *   <li>ADVERTENCIA si la variación absoluta contra el día anterior supera el umbral (30%).</li>
 * </ul>
 */
@Service
public class ValidacionCuboService implements ReporteService {

    private static final long TIMEOUT_MINUTOS = 15;
    private static final BigDecimal UMBRAL_VARIACION = new BigDecimal("0.30");

    private final ValidacionCuboRepository repository;
    private final ReporteSupport support;

    public ValidacionCuboService(ValidacionCuboRepository repository, ReporteSupport support) {
        this.repository = repository;
        this.support = support;
    }

    @Override
    public String codigo() {
        return "validacion-cubo";
    }

    @Override
    public ReporteResultado generar(LocalDate corte) {
        long inicio = System.currentTimeMillis();

        // Ambas fotos (corte y día anterior) se consultan en paralelo (RN-01).
        var fActual = repository.consultarIndicadores(corte);
        var fAnterior = repository.consultarIndicadores(corte.minusDays(1));
        try {
            CompletableFuture.allOf(fActual, fAnterior)
                    .orTimeout(TIMEOUT_MINUTOS, TimeUnit.MINUTES)
                    .join();
        } catch (CompletionException e) {
            throw new ReporteException("Fallo en consultas de " + codigo(), e.getCause());
        }

        CuboSnapshot actual = fActual.join().stream().findFirst().orElse(null);
        CuboSnapshot anterior = fAnterior.join().stream().findFirst().orElse(null);

        List<IndicadorCuboFila> indicadores = compararIndicadores(corte, actual, anterior);
        List<ValidacionFila> validaciones = validar(corte, actual, anterior);

        var hojas = List.<ExcelSheetSpec<?>>of(
                ExcelSheetSpec.de("Indicadores", columnasIndicadores(), indicadores),
                ExcelSheetSpec.de("Validaciones", columnasValidaciones(), validaciones));
        return support.completar(codigo(), corte, hojas, inicio);
    }

    static List<IndicadorCuboFila> compararIndicadores(LocalDate corte, CuboSnapshot actual, CuboSnapshot anterior) {
        if (actual == null) {
            return List.of();
        }
        Map<String, BigDecimal> anteriores = anterior != null ? anterior.indicadores() : Map.of();
        var filas = new ArrayList<IndicadorCuboFila>();
        actual.indicadores().forEach((nombre, valor) -> {
            BigDecimal previo = anteriores.get(nombre);
            filas.add(new IndicadorCuboFila(corte, nombre, valor, previo, variacion(valor, previo)));
        });
        return filas;
    }

    static List<ValidacionFila> validar(LocalDate corte, CuboSnapshot actual, CuboSnapshot anterior) {
        var resultados = new ArrayList<ValidacionFila>();

        if (actual == null) {
            resultados.add(new ValidacionFila("(cubo)", "Datos del día presentes", ValidacionFila.ALERTA,
                    "El cubo no tiene registros para la fecha de corte " + corte));
            return resultados;
        }
        resultados.add(new ValidacionFila("(cubo)", "Datos del día presentes", ValidacionFila.OK,
                "Foto del " + corte + " encontrada"));

        Map<String, BigDecimal> anteriores = anterior != null ? anterior.indicadores() : Map.of();
        actual.indicadores().forEach((nombre, valor) -> {
            if (valor == null) {
                resultados.add(new ValidacionFila(nombre, "Indicador no nulo", ValidacionFila.ALERTA,
                        "El indicador vino sin valor en el cubo"));
                return;
            }
            if (valor.signum() < 0) {
                resultados.add(new ValidacionFila(nombre, "Indicador no negativo", ValidacionFila.ALERTA,
                        "Valor negativo: " + valor));
                return;
            }
            BigDecimal var = variacion(valor, anteriores.get(nombre));
            if (var != null && var.abs().compareTo(UMBRAL_VARIACION) > 0) {
                resultados.add(new ValidacionFila(nombre, "Variación diaria dentro del umbral",
                        ValidacionFila.ADVERTENCIA,
                        "Variación de " + var.movePointRight(2).setScale(2, RoundingMode.HALF_UP)
                                + "% frente al día anterior (umbral 30%)"));
                return;
            }
            resultados.add(new ValidacionFila(nombre, "Consistencia diaria", ValidacionFila.OK, ""));
        });
        return resultados;
    }

    private static BigDecimal variacion(BigDecimal actual, BigDecimal anterior) {
        if (actual == null || anterior == null || anterior.signum() == 0) {
            return null;
        }
        return actual.subtract(anterior).divide(anterior.abs(), MathContext.DECIMAL64);
    }

    private static List<ExcelSheetSpec.ColumnaSpec<IndicadorCuboFila>> columnasIndicadores() {
        return List.of(
                col("FecCierre", IndicadorCuboFila::fecCierre, FECHA, 12),
                col("Indicador", IndicadorCuboFila::indicador, TEXTO, 26),
                col("ValorActual", IndicadorCuboFila::valorActual, MONTO, 18),
                col("ValorAnterior", IndicadorCuboFila::valorAnterior, MONTO, 18),
                col("VariacionPct", IndicadorCuboFila::variacionPct, PORCENTAJE, 14));
    }

    private static List<ExcelSheetSpec.ColumnaSpec<ValidacionFila>> columnasValidaciones() {
        return List.of(
                col("Indicador", ValidacionFila::indicador, TEXTO, 26),
                col("Regla", ValidacionFila::regla, TEXTO, 34),
                col("Resultado", ValidacionFila::resultado, TEXTO, 14),
                col("Detalle", ValidacionFila::detalle, TEXTO, 60));
    }
}
