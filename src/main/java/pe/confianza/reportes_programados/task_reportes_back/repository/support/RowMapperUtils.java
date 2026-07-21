package pe.confianza.reportes_programados.task_reportes_back.repository.support;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Helpers de mapeo tolerantes a los tipos heterogéneos del warehouse (char(8) vs date). */
public final class RowMapperUtils {

    public static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    public static final String SIN_ASIGNAR = "SIN ASIGNAR";

    private RowMapperUtils() {
    }

    /** Lee una fecha venga como DATE/DATETIME o como texto 'yyyyMMdd' / 'yyyy-MM-dd'. */
    public static LocalDate fecha(ResultSet rs, String columna) throws SQLException {
        Object valor = rs.getObject(columna);
        return switch (valor) {
            case null -> null;
            case java.sql.Date d -> d.toLocalDate();
            case Timestamp t -> t.toLocalDateTime().toLocalDate();
            case String s -> parseFecha(s.trim());
            default -> parseFecha(valor.toString().trim());
        };
    }

    private static LocalDate parseFecha(String s) {
        if (s.isEmpty()) {
            return null;
        }
        return s.contains("-") ? LocalDate.parse(s) : LocalDate.parse(s, YYYYMMDD);
    }

    public static BigDecimal decimal(ResultSet rs, String columna) throws SQLException {
        return rs.getBigDecimal(columna);
    }

    public static BigDecimal decimal(ResultSet rs, int columna) throws SQLException {
        return rs.getBigDecimal(columna);
    }

    public static Integer entero(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    public static Integer entero(ResultSet rs, int columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    public static Long largo(ResultSet rs, String columna) throws SQLException {
        long valor = rs.getLong(columna);
        return rs.wasNull() ? null : valor;
    }

    public static String texto(ResultSet rs, String columna) throws SQLException {
        String valor = rs.getString(columna);
        return valor == null ? null : valor.trim();
    }

    public static String texto(ResultSet rs, int columna) throws SQLException {
        String valor = rs.getString(columna);
        return valor == null ? null : valor.trim();
    }

    /** Validación de jerarquía comercial: NULL o vacío se reporta como 'SIN ASIGNAR'. */
    public static String textoOSinAsignar(ResultSet rs, String columna) throws SQLException {
        String valor = texto(rs, columna);
        return (valor == null || valor.isBlank()) ? SIN_ASIGNAR : valor;
    }

    public static String textoOSinAsignar(ResultSet rs, int columna) throws SQLException {
        String valor = texto(rs, columna);
        return (valor == null || valor.isBlank()) ? SIN_ASIGNAR : valor;
    }
}
