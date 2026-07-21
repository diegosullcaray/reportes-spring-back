package pe.confianza.reportes.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import pe.confianza.reportes.config.properties.DbProperties;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Prueba la conexión a SQL Server apenas termina el arranque y deja el
 * resultado explícito en el log. Como Hikari es perezoso (deliberado: la app
 * debe arrancar aunque la BD esté caída), sin este chequeo un problema de
 * credenciales/red recién se descubría al disparar el primer reporte; ahora
 * se ve de inmediato al arrancar. Un fallo aquí NO tumba la aplicación: los
 * reportes reintentarán conectar en cada corrida (RN-03).
 */
@Component
public class DataSourceStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSourceStartupCheck.class);

    private final DataSource dataSource;
    private final DbProperties db;

    public DataSourceStartupCheck(DataSource dataSource, DbProperties db) {
        this.dataSource = dataSource;
        this.db = db;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!db.startupCheck()) {
            return;
        }
        String destino = db.server() + (db.instance().isBlank() ? ":" + db.port() : "\\" + db.instance())
                + "/" + db.database();
        String auth = db.domain().isBlank() ? "SQL Server (usuario " + db.username() + ")"
                : "Windows/NTLM (" + db.domain() + "\\" + db.username() + ")";
        try (Connection con = dataSource.getConnection()) {
            log.info("✅ Conexión a BD verificada: {} — autenticación {} — {}",
                    destino, auth, con.getMetaData().getDatabaseProductVersion());
        } catch (Exception e) {
            log.error("""
                    ❌ NO se pudo conectar a la base de datos al arrancar: {} — autenticación {}
                       Causa: {}
                       La aplicación sigue levantada (los crons quedan programados), pero TODOS los \
                    reportes fallarán hasta que la conexión se restablezca. Revisar DB_SERVER/DB_DOMAIN/\
                    DB_USERNAME/DB_PASSWORD (doc 06 §2.1) y la conectividad de red.""",
                    destino, auth, causaRaiz(e));
        }
    }

    private static String causaRaiz(Throwable e) {
        Throwable causa = e;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        return causa.getClass().getSimpleName() + ": " + causa.getMessage();
    }
}
