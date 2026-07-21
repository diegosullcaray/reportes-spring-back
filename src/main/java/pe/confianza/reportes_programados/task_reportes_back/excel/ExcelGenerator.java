package pe.confianza.reportes_programados.task_reportes_back.excel;

import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import pe.confianza.reportes_programados.task_reportes_back.config.properties.ReportesProperties;
import pe.confianza.reportes_programados.task_reportes_back.shared.ReporteException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generador genérico de .xlsx en modo streaming (SXSSF, ventana de 100 filas).
 * No conoce el negocio: recibe {@link ExcelSheetSpec} y escribe (AR-04).
 */
@Component
public class ExcelGenerator {

    private static final int VENTANA_FILAS = 100;
    private static final String LEYENDA_VACIO = "Sin registros para el período";

    private final ReportesProperties properties;

    public ExcelGenerator(ReportesProperties properties) {
        this.properties = properties;
    }

    public Path generar(String nombreArchivo, List<ExcelSheetSpec<?>> hojas) {
        Path destino = properties.directorioTemporal().resolve(nombreArchivo + ".xlsx");
        try (var workbook = new SXSSFWorkbook(VENTANA_FILAS)) {
            workbook.setCompressTempFiles(true);
            var estilos = EstilosReporte.crear(workbook);

            for (var spec : hojas) {
                escribirHoja(workbook, spec, estilos);
            }

            Files.createDirectories(destino.getParent());
            try (var out = Files.newOutputStream(destino)) {
                workbook.write(out);
            }
            workbook.dispose();
            return destino;
        } catch (IOException e) {
            throw new ReporteException("No se pudo generar el Excel " + nombreArchivo, e);
        }
    }

    private <T> void escribirHoja(SXSSFWorkbook wb, ExcelSheetSpec<T> spec, EstilosReporte estilos) {
        var hoja = wb.createSheet(spec.nombreHoja());

        // Cabeceras con estilo y autofiltro (XL-02); anchos fijos por columna (XL-05).
        var filaCabecera = hoja.createRow(0);
        for (int c = 0; c < spec.columnas().size(); c++) {
            var col = spec.columnas().get(c);
            var celda = filaCabecera.createCell(c);
            celda.setCellValue(col.titulo());
            celda.setCellStyle(estilos.cabecera());
            hoja.setColumnWidth(c, col.anchoCaracteres() * 256);
        }
        hoja.setAutoFilter(new CellRangeAddress(0, 0, 0, spec.columnas().size() - 1));

        int r = 1;
        for (T fila : spec.filas()) {
            var row = hoja.createRow(r++);
            for (int c = 0; c < spec.columnas().size(); c++) {
                var col = spec.columnas().get(c);
                estilos.escribir(row.createCell(c), col.extractor().apply(fila), col.formato());
            }
        }

        if (spec.filas().isEmpty()) {
            hoja.createRow(1).createCell(0).setCellValue(LEYENDA_VACIO);
        }
    }
}
