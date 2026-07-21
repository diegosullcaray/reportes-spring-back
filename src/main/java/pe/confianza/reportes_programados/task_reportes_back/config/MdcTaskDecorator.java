package pe.confianza.reportes_programados.task_reportes_back.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/** Copia el contexto MDC (ejecucionId) del hilo que agenda la tarea al hilo del pool (RN-07). */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contexto = MDC.getCopyOfContextMap();
        return () -> {
            if (contexto != null) {
                MDC.setContextMap(contexto);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
