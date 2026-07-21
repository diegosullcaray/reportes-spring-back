package pe.confianza.reportes.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.confianza.reportes.config.properties.ReportesProperties;
import pe.confianza.reportes.scheduler.ReporteScheduler;
import pe.confianza.reportes.service.ReporteService;
import pe.confianza.reportes.shared.ReporteNoEncontradoException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Endpoint manual de soporte para re-ejecuciones bajo demanda (CA-07). */
@RestController
@RequestMapping("/api/v1/reportes")
@Tag(name = "Reportes", description = "Listado y re-ejecución manual de los reportes programados")
public class ReporteController {

    private final Map<String, ReporteService> reportes;
    private final ReporteScheduler reporteScheduler;
    private final ReportesProperties properties;
    private final ThreadPoolTaskExecutor reportTaskExecutor;

    public ReporteController(List<ReporteService> reportes,
                             ReporteScheduler reporteScheduler,
                             ReportesProperties properties,
                             @Qualifier("reportTaskExecutor") ThreadPoolTaskExecutor reportTaskExecutor) {
        this.reportes = reportes.stream()
                .collect(Collectors.toUnmodifiableMap(ReporteService::codigo, Function.identity()));
        this.reporteScheduler = reporteScheduler;
        this.properties = properties;
        this.reportTaskExecutor = reportTaskExecutor;
    }

    @Operation(summary = "Listar los reportes registrados",
            description = "Devuelve el código de cada reporte, su cron (formato Spring de 6 campos) "
                    + "y la estrategia de fecha de corte (DIA_ANTERIOR o FIN_MES_ANTERIOR).")
    @GetMapping
    public List<Map<String, String>> listar() {
        return reportes.keySet().stream().sorted()
                .map(codigo -> Map.of(
                        "codigo", codigo,
                        "cron", properties.definicion(codigo).cron(),
                        "estrategiaCorte", properties.definicion(codigo).corte().name()))
                .toList();
    }

    @Operation(summary = "Re-ejecutar un reporte bajo demanda",
            description = "Dispara la corrida completa (queries en paralelo → Excel → correo) en el pool "
                    + "dedicado y responde de inmediato con 202; el resultado se ve en los logs (ejecucionId) "
                    + "y en la bandeja de los destinatarios. Sin `corte`, se usa la estrategia configurada "
                    + "del reporte (día anterior o fin de mes anterior).")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Reporte lanzado (EN_PROCESO)"),
            @ApiResponse(responseCode = "400", description = "Fecha de corte futura o inválida"),
            @ApiResponse(responseCode = "404", description = "El código de reporte no existe")
    })
    @PostMapping("/{codigo}/ejecutar")
    public ResponseEntity<Map<String, String>> ejecutar(
            @Parameter(description = "Código del reporte (ver GET /api/v1/reportes)", example = "fondeo-estable")
            @PathVariable String codigo,
            @Parameter(description = "Fecha de corte ISO (yyyy-MM-dd); opcional, no puede ser futura",
                    example = "2026-06-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate corte) {

        var reporte = reportes.get(codigo);
        if (reporte == null) {
            throw new ReporteNoEncontradoException(codigo);
        }
        LocalDate hoy = LocalDate.now(properties.zoneId());
        LocalDate fecha = corte != null ? corte : properties.definicion(codigo).corte().resolver(hoy);
        if (fecha.isAfter(hoy)) {
            throw new IllegalArgumentException("La fecha de corte " + fecha + " no puede ser futura");
        }

        // Lanzar y responder de inmediato (202 Accepted); la corrida usa el pool dedicado.
        CompletableFuture.runAsync(() -> reporteScheduler.ejecutar(reporte, fecha), reportTaskExecutor);
        return ResponseEntity.accepted()
                .body(Map.of("codigo", codigo, "corte", fecha.toString(), "estado", "EN_PROCESO"));
    }
}
