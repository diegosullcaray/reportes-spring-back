package pe.confianza.reportes_programados.task_reportes_back.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import pe.confianza.reportes_programados.task_reportes_back.config.properties.ReportesProperties;
import pe.confianza.reportes_programados.task_reportes_back.scheduler.ReporteScheduler;
import pe.confianza.reportes_programados.task_reportes_back.service.ReporteService;

import java.util.List;

/**
 * Registra dinámicamente un cron por cada bean {@link ReporteService} usando su
 * definición en {@code reportes.definiciones.*} (SC-02, AR-07: agregar un reporte
 * nuevo no requiere tocar esta clase).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    private final List<ReporteService> reportes;
    private final ReportesProperties properties;
    private final ReporteScheduler reporteScheduler;

    public SchedulingConfig(List<ReporteService> reportes,
                            ReportesProperties properties,
                            ReporteScheduler reporteScheduler) {
        this.reportes = reportes;
        this.properties = properties;
        this.reporteScheduler = reporteScheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        // 1 hilo por reporte que pueda coincidir en horario: con el default de Spring
        // (1 hilo) dos crons simultáneos se serializarían, violando RN-01/RN-03.
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(4, reportes.size()));
        scheduler.setThreadNamePrefix("report-sched-");
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);

        for (ReporteService reporte : reportes) {
            var def = properties.definicion(reporte.codigo());
            var trigger = new CronTrigger(def.cron(), properties.zoneId());
            registrar.addTriggerTask(() -> reporteScheduler.ejecutarProgramado(reporte), trigger);
            log.info("Reporte '{}' programado con cron '{}' zona {}",
                    reporte.codigo(), def.cron(), properties.zonaHoraria());
        }
    }
}
