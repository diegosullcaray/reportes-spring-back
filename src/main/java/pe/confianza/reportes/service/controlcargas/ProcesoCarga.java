package pe.confianza.reportes.service.controlcargas;

import java.time.LocalDate;
import java.util.Set;

/** Estado de un proceso de carga del warehouse (fila de mod_rep.com.RSRPD001). */
public record ProcesoCarga(
        String proceso,
        LocalDate fechaActividad,
        LocalDate fechaReporte,
        String estado
) {

    private static final Set<String> ESTADOS_EXITOSOS = Set.of("FINALIZADO", "OK", "COMPLETADO", "TERMINADO");

    public boolean finalizoConExito() {
        return estado != null && ESTADOS_EXITOSOS.contains(estado.toUpperCase());
    }

    /** Crítico = cargas de carteras activas/pasivas: sin ellas ningún reporte es confiable. */
    public boolean esCritico() {
        String nombre = proceso.toLowerCase();
        return (nombre.contains("activa") || nombre.contains("pasiva")) && nombre.contains("cartera");
    }

    public boolean esCriticoAlterno() {
        String nombre = proceso.toLowerCase();
        return nombre.contains("activa") || nombre.contains("pasiva");
    }
}
