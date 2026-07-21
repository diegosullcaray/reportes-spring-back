package pe.confianza.reportes.mail;

import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import pe.confianza.reportes.config.properties.ReportesProperties;
import pe.confianza.reportes.shared.ReporteException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Envío de correos genérico (AR-04): no conoce qué reporte lo invoca.
 * Reintentos con backoff 2s/4s/8s (MA-04); si el adjunto supera el límite del
 * relay se comprime a .zip (MA-06); el archivo se elimina solo tras envío
 * exitoso (RN-08) y se conserva ante fallo para reenvío manual. Si hay un logo
 * configurado ({@code reportes.firma-logo-path}), se embebe como imagen inline
 * (cid:{@value #LOGO_CONTENT_ID}) para que aparezca en la firma sin depender
 * de que el cliente de correo cargue imágenes externas. El default apunta al
 * logo corporativo empaquetado en el jar ({@code classpath:static/logo-confianza.png}).
 */
@Service
public class EmailService {

    /** Content-ID de la imagen de firma embebida; debe coincidir con el "cid:" del HTML del correo. */
    public static final String LOGO_CONTENT_ID = "firma-logo";

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final int MAX_INTENTOS = 3;
    private static final long BACKOFF_INICIAL_MS = 2000;

    private final JavaMailSender mailSender;
    private final ReportesProperties properties;

    public EmailService(JavaMailSender mailSender, ReportesProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void enviarConAdjunto(List<String> destinatarios, List<String> conCopia,
                                 String asunto, String cuerpoHtml, Path adjunto) {
        Path aEnviar = comprimirSiExcedeLimite(adjunto);
        enviarConReintentos(() -> {
            var mensaje = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.correoRemitente());
            helper.setTo(destinatarios.toArray(String[]::new));
            if (conCopia != null && !conCopia.isEmpty()) {
                helper.setCc(conCopia.toArray(String[]::new));
            }
            helper.setSubject(asunto);
            helper.setText(cuerpoHtml, true);
            embebirLogoSiConfigurado(helper);
            helper.addAttachment(aEnviar.getFileName().toString(), new FileSystemResource(aEnviar));
            mailSender.send(mensaje);
        }, asunto);
        eliminar(adjunto);
        if (!aEnviar.equals(adjunto)) {
            eliminar(aEnviar);
        }
        log.info("Correo '{}' enviado a {} destinatario(s), {} en copia", asunto,
                destinatarios.size(), conCopia == null ? 0 : conCopia.size());
    }

    /** Notificación simple sin adjunto (fallos de reporte a soporte, RN-03). */
    public void enviarTexto(List<String> destinatarios, String asunto, String cuerpoHtml) {
        enviarConReintentos(() -> {
            var mensaje = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.correoRemitente());
            helper.setTo(destinatarios.toArray(String[]::new));
            helper.setSubject(asunto);
            helper.setText(cuerpoHtml, true);
            embebirLogoSiConfigurado(helper);
            mailSender.send(mensaje);
        }, asunto);
    }

    private void embebirLogoSiConfigurado(MimeMessageHelper helper) throws MessagingException {
        String rutaLogo = properties.firmaLogoPath();
        if (rutaLogo == null || rutaLogo.isBlank()) {
            return;
        }
        Resource logo = resolverLogo(rutaLogo);
        if (!logo.exists()) {
            log.warn("reportes.firma-logo-path='{}' no existe; el correo se envía sin logo", rutaLogo);
            return;
        }
        helper.addInline(LOGO_CONTENT_ID, logo);
    }

    /**
     * Resuelve {@code reportes.firma-logo-path} con los prefijos habituales de
     * Spring: {@code classpath:} (empaquetado en el jar, funciona igual en dev
     * y producción) o {@code file:}/ruta plana (archivo en disco del servidor).
     */
    private static Resource resolverLogo(String ruta) {
        if (ruta.startsWith("classpath:")) {
            return new ClassPathResource(ruta.substring("classpath:".length()));
        }
        if (ruta.startsWith("file:")) {
            return new FileSystemResource(ruta.substring("file:".length()));
        }
        return new FileSystemResource(ruta);
    }

    private void enviarConReintentos(Envio envio, String asunto) {
        long espera = BACKOFF_INICIAL_MS;
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                envio.ejecutar();
                return;
            } catch (Exception e) {
                if (intento == MAX_INTENTOS) {
                    throw new ReporteException(
                            "Fallo el envío del correo '" + asunto + "' tras " + MAX_INTENTOS
                                    + " intentos; el adjunto se conserva para reenvío manual", e);
                }
                log.warn("Intento {}/{} de envío de '{}' falló: {}. Reintentando en {} ms",
                        intento, MAX_INTENTOS, asunto, e.getMessage(), espera);
                dormir(espera);
                espera *= 2;
            }
        }
    }

    private Path comprimirSiExcedeLimite(Path adjunto) {
        try {
            long limiteBytes = properties.adjuntoMaxMb() * 1024L * 1024L;
            if (Files.size(adjunto) <= limiteBytes) {
                return adjunto;
            }
            Path zip = adjunto.resolveSibling(adjunto.getFileName() + ".zip");
            try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
                out.putNextEntry(new ZipEntry(adjunto.getFileName().toString()));
                Files.copy(adjunto, out);
                out.closeEntry();
            }
            log.info("Adjunto {} supera {} MB; se envía comprimido como {}",
                    adjunto.getFileName(), properties.adjuntoMaxMb(), zip.getFileName());
            return zip;
        } catch (IOException e) {
            throw new ReporteException("No se pudo evaluar/comprimir el adjunto " + adjunto, e);
        }
    }

    private void eliminar(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            log.warn("No se pudo eliminar el archivo temporal {}", archivo, e);
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReporteException("Envío de correo interrumpido", e);
        }
    }

    @FunctionalInterface
    interface Envio {
        void ejecutar() throws Exception;
    }
}
