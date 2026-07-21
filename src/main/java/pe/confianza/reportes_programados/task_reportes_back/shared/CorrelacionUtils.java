package pe.confianza.reportes_programados.task_reportes_back.shared;

import org.slf4j.MDC;

import java.util.UUID;

/** Id de correlación por corrida de reporte, visible en todos sus logs vía MDC (RN-07). */
public final class CorrelacionUtils {

    public static final String MDC_KEY = "ejecucionId";

    private CorrelacionUtils() {
    }

    public static String iniciarEjecucion(String codigoReporte) {
        String ejecucionId = codigoReporte + "-" + UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_KEY, ejecucionId);
        return ejecucionId;
    }

    public static void finalizarEjecucion() {
        MDC.clear();
    }
}
