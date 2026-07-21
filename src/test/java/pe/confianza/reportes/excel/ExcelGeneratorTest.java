package pe.confianza.reportes.excel;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.confianza.reportes.config.properties.ReportesProperties;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.confianza.reportes.excel.ExcelSheetSpec.col;

class ExcelGeneratorTest {

    record Fila(LocalDate fecha, String nombre, BigDecimal monto, Integer cantidad) {
    }

    @TempDir
    Path tempDir;

    private ExcelGenerator generador() {
        var props = new ReportesProperties("America/Lima", tempDir, "test@localhost",
                List.of("soporte@localhost"), "Equipo de Reportes", "", "", "", "", "", 20, Map.of());
        return new ExcelGenerator(props);
    }

    private static List<ExcelSheetSpec.ColumnaSpec<Fila>> columnas() {
        return List.of(
                col("Fecha", Fila::fecha, FormatoCelda.FECHA, 12),
                col("Nombre", Fila::nombre, FormatoCelda.TEXTO, 20),
                col("Monto", Fila::monto, FormatoCelda.MONTO, 14),
                col("Cantidad", Fila::cantidad, FormatoCelda.ENTERO, 10));
    }

    @Test
    void generaExcelConCabecerasYTiposNativos() throws Exception {
        var filas = List.of(
                new Fila(LocalDate.of(2026, 6, 30), "AG ABANCAY", new BigDecimal("4273093.04"), 10),
                new Fila(LocalDate.of(2026, 6, 30), "AG PIURA", new BigDecimal("2160708.77"), null));

        Path archivo = generador().generar("test_2026-06-30",
                List.of(ExcelSheetSpec.de("Datos", columnas(), filas)));

        assertThat(archivo).exists();
        try (InputStream in = Files.newInputStream(archivo); var wb = new XSSFWorkbook(in)) {
            var hoja = wb.getSheet("Datos");
            assertThat(hoja).isNotNull();
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Fecha");
            assertThat(hoja.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Monto");
            // XL-03: montos y fechas como celdas nativas, nunca texto
            assertThat(hoja.getRow(1).getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(hoja.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(4273093.04);
            assertThat(hoja.getRow(1).getCell(0).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 6, 30));
            assertThat(hoja.getRow(1).getCell(1).getStringCellValue()).isEqualTo("AG ABANCAY");
            // Nulos como celda vacía
            assertThat(hoja.getRow(2).getCell(3).getStringCellValue()).isEmpty();
        }
    }

    @Test
    void hojaVaciaIncluyeLeyendaSinRegistros() throws Exception {
        Path archivo = generador().generar("vacio_2026-06-30",
                List.of(ExcelSheetSpec.de("Vacia", columnas(), List.of())));

        try (InputStream in = Files.newInputStream(archivo); var wb = new XSSFWorkbook(in)) {
            var hoja = wb.getSheet("Vacia");
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Fecha");
            assertThat(hoja.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("Sin registros para el período");
        }
    }
}
