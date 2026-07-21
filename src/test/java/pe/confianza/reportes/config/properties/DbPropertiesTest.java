package pe.confianza.reportes.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DbPropertiesTest {

    @Test
    void sqlServerAuthConPuertoDirecto() {
        var db = new DbProperties("srv-bd", "storage", "", "usuario", "secreto",
                "", 1433, false, true, 10);

        assertThat(db.jdbcUrl()).isEqualTo(
                "jdbc:sqlserver://srv-bd:1433;databaseName=storage;encrypt=false;trustServerCertificate=true");
    }

    @Test
    void windowsAuthNtlmConInstanciaNombrada() {
        var db = new DbProperties("SERVIDOR", "storage", "DOMINIO", "usuario", "secreto",
                "SQLEXPRESS", 1433, false, true, 10);

        assertThat(db.jdbcUrl()).isEqualTo(
                "jdbc:sqlserver://SERVIDOR;instanceName=SQLEXPRESS;databaseName=storage"
                        + ";encrypt=false;trustServerCertificate=true"
                        + ";authenticationScheme=NTLM;domain=DOMINIO");
    }

    @Test
    void encriptacionConfigurable() {
        var db = new DbProperties("srv", "dwh", "", "u", "p", "", 1450, true, false, 10);

        assertThat(db.jdbcUrl()).contains(":1450;")
                .contains("encrypt=true")
                .contains("trustServerCertificate=false");
    }
}
