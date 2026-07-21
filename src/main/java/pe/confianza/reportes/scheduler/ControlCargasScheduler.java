package pe.confianza.reportes.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.confianza.reportes.service.controlcargas.ControlCargasService;
import pe.confianza.reportes.shared.CorrelacionUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Validación automática de control de cargas (equivalente del schedule de
 * Node.js: cada 5 minutos por defecto, configurable con CONTROL_CARGAS_CRON en
 * formato Spring de 6 campos). Cada corrida notifica su resultado a Google
 * Chat vía el propio service; un fallo se loguea y nunca detiene el ciclo
 * siguiente (RN-03).
 */
@Component
public class ControlCargasScheduler {

    private static final Logger log = LoggerFactory.getLogger(ControlCargasScheduler.class);

    /** Evita solapamiento si una consulta a BD tarda más que la ventana del cron. */
    private final AtomicBoolean enEjecucion = new AtomicBoolean(false);

    private final ControlCargasService controlCargasService;

    public ControlCargasScheduler(ControlCargasService controlCargasService) {
        this.controlCargasService = controlCargasService;
    }

    @Scheduled(cron = "${reportes.control-cargas.cron}", zone = "${reportes.zona-horaria}")
    public void validacionProgramada() {
        if (!enEjecucion.compareAndSet(false, true)) {
            log.warn("Control de cargas: ejecución anterior aún en curso, se omite este ciclo");
            return;
        }
        CorrelacionUtils.iniciarEjecucion("control-cargas");
        try {
            controlCargasService.validarCargas();
        } catch (Exception e) {
            log.error("Error en la validación programada de control de cargas", e);
        } finally {
            CorrelacionUtils.finalizarEjecucion();
            enEjecucion.set(false);
        }
    }
}
