package pe.confianza.reportes.web;

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

    @GetMapping
    public List<Map<String, String>> listar() {
        return reportes.keySet().stream().sorted()
                .map(codigo -> Map.of(
                        "codigo", codigo,
                        "cron", properties.definicion(codigo).cron(),
                        "estrategiaCorte", properties.definicion(codigo).corte().name()))
                .toList();
    }

    @PostMapping("/{codigo}/ejecutar")
    public ResponseEntity<Map<String, String>> ejecutar(
            @PathVariable String codigo,
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
