package pe.confianza.reportes_programados.task_reportes_back.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pe.confianza.reportes_programados.task_reportes_back.config.properties.ReportesProperties;
import pe.confianza.reportes_programados.task_reportes_back.mail.EmailService;
import pe.confianza.reportes_programados.task_reportes_back.service.ReporteService;
import pe.confianza.reportes_programados.task_reportes_back.shared.CorrelacionUtils;

import java.time.LocalDate;

/**
 * Orquestador sin lógica de negocio (AR-01): resuelve la fecha de corte, invoca
 * al service y captura todo fallo para que un reporte caído nunca interrumpa a
 * los demás ni al scheduler (RN-03).
 */
@Component
public class ReporteScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReporteScheduler.class);

    private final ReportesProperties properties;
    private final EmailService emailService;

    public ReporteScheduler(ReportesProperties properties, EmailService emailService) {
        this.properties = properties;
        this.emailService = emailService;
    }

    public void ejecutarProgramado(ReporteService reporte) {
        var def = properties.definicion(reporte.codigo());
        LocalDate corte = def.corte().resolver(LocalDate.now(properties.zoneId()));
        ejecutar(reporte, corte);
    }

    public void ejecutar(ReporteService reporte, LocalDate corte) {
        String ejecucionId = CorrelacionUtils.iniciarEjecucion(reporte.codigo());
        try {
            log.info("Iniciando reporte {}, corte={}", reporte.codigo(), corte);
            var resultado = reporte.generar(corte);
            log.info("Reporte {} OK: {} filas en {} ms", reporte.codigo(),
                    resultado.filasTotales(), resultado.duracionMs());
        } catch (Exception e) {
            log.error("Reporte {} FALLÓ (ejecucionId={})", reporte.codigo(), ejecucionId, e);
            notificarFallo(reporte.codigo(), corte, ejecucionId, e);
        } finally {
            CorrelacionUtils.finalizarEjecucion();
        }
    }

    private void notificarFallo(String codigo, LocalDate corte, String ejecucionId, Exception e) {
        try {
            emailService.enviarTexto(properties.correoSoporte(),
                    "[FALLO] Reporte " + codigo + " - " + corte,
                    "<p>El reporte <b>" + codigo + "</b> con corte <b>" + corte + "</b> falló.</p>"
                            + "<p>Id de ejecución: <code>" + ejecucionId + "</code></p>"
                            + "<p>Error: " + e.getMessage() + "</p>");
        } catch (Exception mailEx) {
            log.error("Tampoco se pudo notificar el fallo del reporte {} a soporte", codigo, mailEx);
        }
    }
}
