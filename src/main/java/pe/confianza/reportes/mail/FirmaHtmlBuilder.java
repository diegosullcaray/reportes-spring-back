package pe.confianza.reportes.mail;

import org.springframework.stereotype.Component;
import pe.confianza.reportes.config.properties.ReportesProperties;

/**
 * Firma corporativa (logo + nombre/cargo/dirección/web) que se agrega al pie
 * de TODO correo saliente (reportes y avisos de fallo a soporte) — igual a la
 * plantilla original de Node.js, ver .docs/FIRMA/images/firma/email-template.js.
 * El logo es opcional: si {@code reportes.firma-logo-path} no resuelve a un
 * recurso existente, {@link EmailService} simplemente omite la imagen y el
 * resto de la firma se ve igual.
 */
@Component
public class FirmaHtmlBuilder {

    private final ReportesProperties properties;

    public FirmaHtmlBuilder(ReportesProperties properties) {
        this.properties = properties;
    }

    public String html() {
        var sb = new StringBuilder();
        sb.append("<p>--<br>Saludos,</p>");
        sb.append("<table cellpadding='0' cellspacing='0' border='0' ")
          .append("style='font-family:Arial,sans-serif;font-size:13px;color:#333'><tr>");
        if (!properties.firmaLogoPath().isBlank()) {
            sb.append("<td style='padding-right:15px;vertical-align:middle'>")
              .append("<img src='cid:").append(EmailService.LOGO_CONTENT_ID)
              .append("' alt='").append(properties.firmaNombre())
              .append("' width='150' style='display:block'></td>");
        }
        sb.append("<td style='border-left:3px solid #0072CE;padding-left:15px;vertical-align:middle'>")
          .append("<strong style='color:#0072CE;font-size:14px'>").append(properties.firmaNombre()).append("</strong><br>");
        if (!properties.firmaCargo().isBlank()) {
            sb.append("<strong>").append(properties.firmaCargo()).append("</strong><br>");
        }
        if (!properties.firmaDireccion().isBlank()) {
            sb.append("<span style='color:#555'>").append(properties.firmaDireccion()).append("</span><br>");
        }
        if (!properties.firmaWeb().isBlank()) {
            sb.append("<a href='https://").append(properties.firmaWeb())
              .append("' style='color:#0072CE;font-weight:bold;text-decoration:none'>")
              .append(properties.firmaWeb()).append("</a>");
        }
        sb.append("</td></tr></table>");
        return sb.toString();
    }
}
