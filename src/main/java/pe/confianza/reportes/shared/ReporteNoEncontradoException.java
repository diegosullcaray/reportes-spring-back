package pe.confianza.reportes.shared;

/** El código de reporte solicitado no corresponde a ningún ReporteService registrado. */
public class ReporteNoEncontradoException extends RuntimeException {

    public ReporteNoEncontradoException(String codigo) {
        super("No existe el reporte '" + codigo + "'");
    }
}
