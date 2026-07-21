package pe.confianza.reportes.shared;

/** Error de negocio en la generación o envío de un reporte. */
public class ReporteException extends RuntimeException {

    public ReporteException(String message) {
        super(message);
    }

    public ReporteException(String message, Throwable cause) {
        super(message, cause);
    }
}
