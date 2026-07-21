package pe.confianza.reportes_programados.task_reportes_back.shared;

/** El código de reporte solicitado no corresponde a ningún ReporteService registrado. */
public class ReporteNoEncontradoException extends RuntimeException {

    public ReporteNoEncontradoException(String codigo) {
        super("No existe el reporte '" + codigo + "'");
    }
}
