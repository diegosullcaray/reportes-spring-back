package pe.confianza.reportes_programados.task_reportes_back.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import pe.confianza.reportes_programados.task_reportes_back.config.properties.ReportesProperties;
import pe.confianza.reportes_programados.task_reportes_back.shared.ReporteException;

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
 * exitoso (RN-08) y se conserva ante fallo para reenvío manual.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final int MAX_INTENTOS = 3;
    private static final long BACKOFF_INICIAL_MS = 2000;

    private final JavaMailSender mailSender;
    private final ReportesProperties properties;

    public EmailService(JavaMailSender mailSender, ReportesProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void enviarConAdjunto(List<String> destinatarios, String asunto, String cuerpoHtml, Path adjunto) {
        Path aEnviar = comprimirSiExcedeLimite(adjunto);
        enviarConReintentos(() -> {
            var mensaje = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.correoRemitente());
            helper.setTo(destinatarios.toArray(String[]::new));
            helper.setSubject(asunto);
            helper.setText(cuerpoHtml, true);
            helper.addAttachment(aEnviar.getFileName().toString(), new FileSystemResource(aEnviar));
            mailSender.send(mensaje);
        }, asunto);
        eliminar(adjunto);
        if (!aEnviar.equals(adjunto)) {
            eliminar(aEnviar);
        }
        log.info("Correo '{}' enviado a {} destinatario(s)", asunto, destinatarios.size());
    }

    /** Notificación simple sin adjunto (fallos de reporte a soporte, RN-03). */
    public void enviarTexto(List<String> destinatarios, String asunto, String cuerpoHtml) {
        enviarConReintentos(() -> {
            var mensaje = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.correoRemitente());
            helper.setTo(destinatarios.toArray(String[]::new));
            helper.setSubject(asunto);
            helper.setText(cuerpoHtml, true);
            mailSender.send(mensaje);
        }, asunto);
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
