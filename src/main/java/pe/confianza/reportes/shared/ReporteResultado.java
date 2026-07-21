package pe.confianza.reportes.shared;

import java.nio.file.Path;
import java.time.LocalDate;

/** Resultado de una corrida de reporte. */
public record ReporteResultado(
        String codigo,
        LocalDate corte,
        Path rutaArchivo,
        long filasTotales,
        long duracionMs,
        Estado estado
) {

    public enum Estado { EXITOSO, FALLIDO }

    public static ReporteResultado exitoso(String codigo, LocalDate corte, Path rutaArchivo,
                                           long filasTotales, long duracionMs) {
        return new ReporteResultado(codigo, corte, rutaArchivo, filasTotales, duracionMs, Estado.EXITOSO);
    }
}
