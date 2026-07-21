package pe.confianza.reportes.mail;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import pe.confianza.reportes.config.properties.ReportesProperties;
import pe.confianza.reportes.shared.ReporteException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    @TempDir
    Path tempDir;

    private JavaMailSender mailSender;
    private EmailService emailService;

    private ReportesProperties props;

    @BeforeEach
    void setUp() {
        mailSender = Mockito.mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        props = new ReportesProperties("America/Lima", tempDir, "test@localhost",
                List.of("soporte@localhost"), "Equipo de Reportes", "", "", "", "", "", 20, Map.of());
        emailService = new EmailService(mailSender, props);
    }

    @Test
    void eliminaElAdjuntoTrasEnvioExitoso() throws Exception {
        Path adjunto = Files.writeString(tempDir.resolve("reporte.xlsx"), "contenido");

        emailService.enviarConAdjunto(List.of("a@b.pe"), List.of("cc@b.pe"), "Asunto - 2026-06-30", "<p>ok</p>", adjunto);

        verify(mailSender).send(any(MimeMessage.class));
        assertThat(adjunto).doesNotExist();
    }

    @Test
    void reintentaAnteFalloTransitorioYConservaElOrden() throws Exception {
        Path adjunto = Files.writeString(tempDir.resolve("reporte.xlsx"), "contenido");
        doThrow(new MailSendException("relay caído"))
                .doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        emailService.enviarConAdjunto(List.of("a@b.pe"), List.of("cc@b.pe"), "Asunto - 2026-06-30", "<p>ok</p>", adjunto);

        verify(mailSender, times(2)).send(any(MimeMessage.class));
        assertThat(adjunto).doesNotExist();
    }

    @Test
    void conservaElAdjuntoSiAgotaLosReintentos() throws Exception {
        Path adjunto = Files.writeString(tempDir.resolve("reporte.xlsx"), "contenido");
        doThrow(new MailSendException("relay caído"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() ->
                emailService.enviarConAdjunto(List.of("a@b.pe"), List.of("cc@b.pe"), "Asunto - 2026-06-30", "<p>ok</p>", adjunto))
                .isInstanceOf(ReporteException.class)
                .hasMessageContaining("3 intentos");

        verify(mailSender, times(3)).send(any(MimeMessage.class));
        // MA-04: el archivo se conserva para reenvío manual
        assertThat(adjunto).exists();
    }

    @Test
    void embebeElLogoInlineCuandoEstaConfigurado() throws Exception {
        Path logo = Files.write(tempDir.resolve("logo.png"), new byte[] {1, 2, 3, 4});
        props = new ReportesProperties("America/Lima", tempDir, "test@localhost",
                List.of("soporte@localhost"), "Equipo de Reportes", "", "", "", logo.toString(), "", 20, Map.of());
        emailService = new EmailService(mailSender, props);
        Path adjunto = Files.writeString(tempDir.resolve("reporte.xlsx"), "contenido");
        var captor = ArgumentCaptor.forClass(MimeMessage.class);

        emailService.enviarConAdjunto(List.of("a@b.pe"), List.of(), "Asunto - 2026-06-30", "<p>ok</p>", adjunto);

        verify(mailSender).send(captor.capture());
        assertThat(contieneContentId(captor.getValue(), EmailService.LOGO_CONTENT_ID)).isTrue();
    }

    @Test
    void resuelveElLogoDesdeElClasspathPorDefecto() throws Exception {
        props = new ReportesProperties("America/Lima", tempDir, "test@localhost",
                List.of("soporte@localhost"), "Equipo de Reportes", "", "", "",
                "classpath:static/logo-confianza.png", "", 20, Map.of());
        emailService = new EmailService(mailSender, props);
        Path adjunto = Files.writeString(tempDir.resolve("reporte.xlsx"), "contenido");
        var captor = ArgumentCaptor.forClass(MimeMessage.class);

        emailService.enviarConAdjunto(List.of("a@b.pe"), List.of(), "Asunto - 2026-06-30", "<p>ok</p>", adjunto);

        verify(mailSender).send(captor.capture());
        assertThat(contieneContentId(captor.getValue(), EmailService.LOGO_CONTENT_ID)).isTrue();
    }

    @Test
    void noFallaSiElLogoConfiguradoNoExiste() throws Exception {
        props = new ReportesProperties("America/Lima", tempDir, "test@localhost",
                List.of("soporte@localhost"), "Equipo de Reportes", "", "", "",
                tempDir.resolve("no-existe.png").toString(), "", 20, Map.of());
        emailService = new EmailService(mailSender, props);
        Path adjunto = Files.writeString(tempDir.resolve("reporte.xlsx"), "contenido");

        emailService.enviarConAdjunto(List.of("a@b.pe"), List.of(), "Asunto - 2026-06-30", "<p>ok</p>", adjunto);

        verify(mailSender).send(any(MimeMessage.class));
    }

    private static boolean contieneContentId(Part parte, String contentId) throws Exception {
        String[] cid = parte.getHeader("Content-ID");
        if (cid != null && cid.length > 0 && cid[0].contains(contentId)) {
            return true;
        }
        // Antes de saveChanges() getContentType() no refleja aún la estructura real
        // (JavaMail calcula las cabeceras de forma perezosa); por eso se intenta
        // directamente el contenido en vez de filtrar primero por content-type.
        if (parte.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                if (contieneContentId(multipart.getBodyPart(i), contentId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
