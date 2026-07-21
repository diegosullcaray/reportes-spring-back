package pe.confianza.reportes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TaskReportesApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskReportesApplication.class, args);
    }
}
