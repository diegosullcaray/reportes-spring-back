package pe.confianza.reportes.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import pe.confianza.reportes.shared.ReporteException;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Configuración raíz {@code reportes.*}: zona horaria, directorio de salida,
 * remitente/soporte/firma de correo, webhook de Google Chat y la definición
 * (cron, asunto, PARA/CC, estrategia de corte) de cada reporte
 * (RN-05: nada hardcodeado en código).
 */
@Validated
@ConfigurationProperties(prefix = "reportes")
public record ReportesProperties(
        @NotBlank String zonaHoraria,
        @NotNull Path directorioTemporal,
        @NotBlank String correoRemitente,
        @NotEmpty List<String> correoSoporte,
        @DefaultValue("Equipo de Reportes") String firmaNombre,
        @DefaultValue("") String firmaCargo,
        @DefaultValue("") String googleChatWebhookUrl,
        @Min(1) @DefaultValue("20") int adjuntoMaxMb,
        @NotNull @Valid Map<String, Definicion> definiciones
) {

    public ZoneId zoneId() {
        return ZoneId.of(zonaHoraria);
    }

    /** Definición de un reporte; la clave del mapa es su código kebab-case. */
    public record Definicion(
            @NotBlank String cron,
            @NotBlank String asunto,
            @NotEmpty List<String> destinatarios,
            List<String> cc,
            @DefaultValue("DIA_ANTERIOR") EstrategiaCorte corte
    ) {
        public Definicion {
            cc = cc == null ? List.of() : List.copyOf(cc);
        }
    }

    /** Cómo se resuelve la fecha de corte al momento de disparar el cron. */
    public enum EstrategiaCorte {
        DIA_ANTERIOR {
            @Override
            public LocalDate resolver(LocalDate hoy) {
                return hoy.minusDays(1);
            }
        },
        FIN_MES_ANTERIOR {
            @Override
            public LocalDate resolver(LocalDate hoy) {
                return hoy.withDayOfMonth(1).minusDays(1);
            }
        };

        public abstract LocalDate resolver(LocalDate hoy);
    }

    public Definicion definicion(String codigo) {
        var def = definiciones.get(codigo);
        if (def == null) {
            throw new ReporteException(
                    "No existe configuración 'reportes.definiciones." + codigo + "' en application.yml");
        }
        return def;
    }
}
