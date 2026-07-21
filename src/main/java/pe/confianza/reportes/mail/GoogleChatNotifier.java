package pe.confianza.reportes.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pe.confianza.reportes.config.properties.ReportesProperties;

import java.util.Map;

/**
 * Notificación a un espacio de Google Chat vía webhook entrante (paridad con el
 * control de cargas de Node.js). Si el webhook no está configurado, la corrida
 * sigue normal y solo se deja constancia en el log; un fallo del webhook jamás
 * tumba el reporte.
 */
@Component
public class GoogleChatNotifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleChatNotifier.class);

    private final RestClient http = RestClient.create();
    private final ReportesProperties properties;

    public GoogleChatNotifier(ReportesProperties properties) {
        this.properties = properties;
    }

    public void notificar(String texto) {
        String url = properties.googleChatWebhookUrl();
        if (url == null || url.isBlank()) {
            log.warn("GOOGLE_CHAT_WEBHOOK_URL no configurado; se omite la notificación a Chat");
            return;
        }
        try {
            http.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", texto))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Notificación enviada a Google Chat");
        } catch (Exception e) {
            log.warn("No se pudo notificar a Google Chat: {}", e.getMessage());
        }
    }
}
