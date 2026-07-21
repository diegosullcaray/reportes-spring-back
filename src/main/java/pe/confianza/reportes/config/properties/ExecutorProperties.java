package pe.confianza.reportes.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "reportes.executor")
public record ExecutorProperties(
        @Min(1) int coreSize,
        @Min(1) int maxSize,
        @Min(0) int queueCapacity,
        @NotBlank String threadNamePrefix
) {
}
