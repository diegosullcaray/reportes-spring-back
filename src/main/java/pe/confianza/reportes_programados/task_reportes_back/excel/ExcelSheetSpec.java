package pe.confianza.reportes_programados.task_reportes_back.excel;

import java.util.List;
import java.util.function.Function;

/** Especificación declarativa de una hoja: nombre, columnas y filas (AR-04). */
public record ExcelSheetSpec<T>(
        String nombreHoja,
        List<ColumnaSpec<T>> columnas,
        List<T> filas
) {

    public record ColumnaSpec<T>(
            String titulo,
            Function<T, Object> extractor,
            FormatoCelda formato,
            int anchoCaracteres
    ) {
    }

    public static <T> ExcelSheetSpec<T> de(String nombreHoja, List<ColumnaSpec<T>> columnas, List<T> filas) {
        return new ExcelSheetSpec<>(nombreHoja, columnas, filas);
    }

    public static <T> ColumnaSpec<T> col(String titulo, Function<T, Object> extractor,
                                         FormatoCelda formato, int anchoCaracteres) {
        return new ColumnaSpec<>(titulo, extractor, formato, anchoCaracteres);
    }
}
