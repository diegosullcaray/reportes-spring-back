package pe.confianza.reportes.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.confianza.reportes.config.properties.ReportesProperties;
import pe.confianza.reportes.excel.ExcelGenerator;
import pe.confianza.reportes.excel.ExcelSheetSpec;
import pe.confianza.reportes.mail.EmailService;
import pe.confianza.reportes.mail.FirmaHtmlBuilder;
import pe.confianza.reportes.shared.ReporteResultado;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Paso final común a todos los reportes: genera el Excel, arma el cuerpo del
 * correo (con advertencia de hojas vacías, RN-04), envía y devuelve el resultado.
 */
@Component
public class ReporteSupport {

    private static final Logger log = LoggerFactory.getLogger(ReporteSupport.class);

    private final ExcelGenerator excelGenerator;
    private final EmailService emailService;
    private final ReportesProperties properties;
    private final FirmaHtmlBuilder firma;

    public ReporteSupport(ExcelGenerator excelGenerator, EmailService emailService,
                          ReportesProperties properties, FirmaHtmlBuilder firma) {
        this.excelGenerator = excelGenerator;
        this.emailService = emailService;
        this.properties = properties;
        this.firma = firma;
    }

    public ReporteResultado completar(String codigo, LocalDate corte,
                                      List<ExcelSheetSpec<?>> hojas, long inicioMs) {
        Path archivo = excelGenerator.generar(codigo + "_" + corte, hojas);

        var def = properties.definicion(codigo);
        String asunto = def.asunto().formatted(corte);
        emailService.enviarConAdjunto(def.destinatarios(), def.cc(), asunto,
                cuerpoHtml(asunto, corte, hojas), archivo);

        long filasTotales = hojas.stream().mapToLong(h -> h.filas().size()).sum();
        long duracion = System.currentTimeMillis() - inicioMs;
        log.info("Reporte {} generado y enviado: {} filas en {} ms", codigo, filasTotales, duracion);
        return ReporteResultado.exitoso(codigo, corte, archivo, filasTotales, duracion);
    }

    private String cuerpoHtml(String asunto, LocalDate corte, List<ExcelSheetSpec<?>> hojas) {
        var sb = new StringBuilder();
        sb.append("<h3>").append(asunto).append("</h3>");
        sb.append("<p>Fecha de corte: <b>").append(corte).append("</b></p>");
        sb.append("<table border='1' cellpadding='4' cellspacing='0'>")
          .append("<tr><th>Hoja</th><th>Filas</th></tr>");
        for (var hoja : hojas) {
            sb.append("<tr><td>").append(hoja.nombreHoja()).append("</td><td>")
              .append(hoja.filas().size()).append("</td></tr>");
        }
        sb.append("</table>");
        List<String> vacias = hojas.stream()
                .filter(h -> h.filas().isEmpty())
                .map(ExcelSheetSpec::nombreHoja)
                .toList();
        if (!vacias.isEmpty()) {
            sb.append("<p><b>Advertencia:</b> las siguientes hojas no tienen registros para el período: ")
              .append(String.join(", ", vacias)).append("</p>");
        }
        sb.append(firma.html());
        sb.append("<p style='color:#888;font-size:11px'>Correo generado automáticamente por task-reportes-back.</p>");
        return sb.toString();
    }
}
