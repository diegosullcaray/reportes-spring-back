package pe.confianza.reportes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "db.startup-check=false")
class TaskReportesApplicationTests {

    @Test
    void contextLoads() {
        // Verifica que la configuración (properties tipadas, executor, scheduler,
        // registro dinámico de crons y beans de los 10 reportes) arranca completa.
    }
}
