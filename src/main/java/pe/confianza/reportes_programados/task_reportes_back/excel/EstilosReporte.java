package pe.confianza.reportes_programados.task_reportes_back.excel;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Workbook;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * Estilos del workbook, creados una única vez y reutilizados en todas las celdas
 * (XL-04: crear un CellStyle por celda revienta el límite de estilos de Excel).
 */
public final class EstilosReporte {

    private final CellStyle cabecera;
    private final CellStyle entero;
    private final CellStyle monto;
    private final CellStyle fecha;
    private final CellStyle porcentaje;

    private EstilosReporte(Workbook wb) {
        var formatos = wb.createDataFormat();

        Font fuenteCabecera = wb.createFont();
        fuenteCabecera.setBold(true);
        fuenteCabecera.setColor(IndexedColors.WHITE.getIndex());
        cabecera = wb.createCellStyle();
        cabecera.setFont(fuenteCabecera);
        cabecera.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cabecera.setBorderBottom(BorderStyle.THIN);

        entero = wb.createCellStyle();
        entero.setDataFormat(formatos.getFormat("#,##0"));

        monto = wb.createCellStyle();
        monto.setDataFormat(formatos.getFormat("#,##0.00"));

        fecha = wb.createCellStyle();
        fecha.setDataFormat(formatos.getFormat("dd/mm/yyyy"));

        porcentaje = wb.createCellStyle();
        porcentaje.setDataFormat(formatos.getFormat("0.00%"));
    }

    public static EstilosReporte crear(Workbook wb) {
        return new EstilosReporte(wb);
    }

    public CellStyle cabecera() {
        return cabecera;
    }

    /** Escribe el valor con el tipo nativo y estilo que corresponde al formato (XL-03). */
    public void escribir(Cell celda, Object valor, FormatoCelda formato) {
        if (valor == null) {
            celda.setCellValue("");
            return;
        }
        switch (formato) {
            case ENTERO -> {
                celda.setCellValue(((Number) valor).doubleValue());
                celda.setCellStyle(entero);
            }
            case MONTO -> {
                celda.setCellValue(aDouble(valor));
                celda.setCellStyle(monto);
            }
            case PORCENTAJE -> {
                celda.setCellValue(aDouble(valor));
                celda.setCellStyle(porcentaje);
            }
            case FECHA -> {
                switch (valor) {
                    case LocalDate ld -> celda.setCellValue(ld);
                    case LocalDateTime ldt -> celda.setCellValue(ldt);
                    case Date d -> celda.setCellValue(d);
                    default -> celda.setCellValue(valor.toString());
                }
                celda.setCellStyle(fecha);
            }
            case TEXTO -> celda.setCellValue(valor.toString());
        }
    }

    private static double aDouble(Object valor) {
        if (valor instanceof BigDecimal bd) {
            return bd.doubleValue();
        }
        return ((Number) valor).doubleValue();
    }
}
