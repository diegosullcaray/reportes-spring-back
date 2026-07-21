package pe.confianza.reportes.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Conexión a SQL Server por partes, igual que la ventana "Connect to Server"
 * de SSMS (y que el .env del proyecto Node.js):
 * <ul>
 *   <li>{@code domain} definido → Windows Authentication vía NTLM
 *       ({@code DOMINIO\\usuario}); vacío → SQL Server Authentication.</li>
 *   <li>{@code instance} definido (ej. SQLEXPRESS) → instancia nombrada vía
 *       SQL Browser; vacío → conexión directa al puerto {@code port}.</li>
 * </ul>
 * El usuario se indica SIN el prefijo de dominio (el dominio va aparte).
 */
@Validated
@ConfigurationProperties(prefix = "db")
public record DbProperties(
        @NotBlank String server,
        @NotBlank String database,
        @DefaultValue("") String domain,
        @NotBlank String username,
        String password,
        @DefaultValue("") String instance,
        @DefaultValue("1433") @Min(1) int port,
        @DefaultValue("false") boolean encryption,
        @DefaultValue("true") boolean trustCertificate,
        @DefaultValue("10") @Min(1) int poolSize
) {

    public String jdbcUrl() {
        var url = new StringBuilder("jdbc:sqlserver://").append(server);
        if (instance.isBlank()) {
            url.append(':').append(port);
        } else {
            url.append(";instanceName=").append(instance);
        }
        url.append(";databaseName=").append(database)
           .append(";encrypt=").append(encryption)
           .append(";trustServerCertificate=").append(trustCertificate);
        if (!domain.isBlank()) {
            // El driver de Microsoft exige integratedSecurity=true junto con
            // authenticationScheme=NTLM; sin esa bandera ignora el esquema y
            // cae silenciosamente a SQL Server Authentication.
            url.append(";integratedSecurity=true;authenticationScheme=NTLM;domain=").append(domain);
        }
        return url.toString();
    }
}
