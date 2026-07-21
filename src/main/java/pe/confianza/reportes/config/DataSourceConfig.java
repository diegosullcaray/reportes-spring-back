package pe.confianza.reportes.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.confianza.reportes.config.properties.DbProperties;

import javax.sql.DataSource;

/**
 * Construye el DataSource a partir de las propiedades por partes de
 * {@link DbProperties} (servidor, instancia, dominio NTLM), en lugar de exigir
 * una cadena JDBC completa. Se usa el constructor perezoso de Hikari para que
 * la aplicación arranque aunque la BD no esté accesible (la conexión se abre
 * en la primera query).
 */
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(DbProperties db) {
        var ds = new HikariDataSource();
        ds.setJdbcUrl(db.jdbcUrl());
        ds.setUsername(db.username());
        ds.setPassword(db.password());
        // Regla de coherencia (doc 03 §4): poolSize >= hilos del reportTaskExecutor.
        ds.setMaximumPoolSize(db.poolSize());
        ds.setConnectionTimeout(30_000);
        LoggerFactory.getLogger(DataSourceConfig.class)
                .info("DataSource SQL Server: {} (auth={})", db.jdbcUrl().replaceAll("password=[^;]*", "password=***"),
                        db.domain().isBlank() ? "SQL Server" : "Windows/NTLM dominio " + db.domain());
        return ds;
    }
}
