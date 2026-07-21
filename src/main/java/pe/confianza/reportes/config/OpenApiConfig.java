package pe.confianza.reportes.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
                .title("task-reportes-back")
                .description("""
                        Orquestador de reportes financieros programados (migración Node.js → Spring Boot).
                        Los reportes corren solos por cron; esta API es de soporte: listar los reportes
                        registrados y re-ejecutar uno bajo demanda.""")
                .version("1.0.0")
                .contact(new Contact().name("Equipo de Reportes")));
    }
}
