package pe.confianza.reportes.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.confianza.reportes.config.properties.ReportesProperties;
import pe.confianza.reportes.scheduler.ReporteScheduler;
import pe.confianza.reportes.service.ReporteService;
import pe.confianza.reportes.shared.ReporteResultado;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReporteControllerTest {

    private MockMvc mockMvc;
    private ReporteScheduler scheduler;
    private ThreadPoolTaskExecutor executor;

    private final ReporteService reporteFake = new ReporteService() {
        @Override
        public String codigo() {
            return "cartera-heredada";
        }

        @Override
        public ReporteResultado generar(LocalDate corte) {
            return ReporteResultado.exitoso(codigo(), corte, Path.of("x.xlsx"), 0, 1);
        }
    };

    @BeforeEach
    void setUp() {
        scheduler = Mockito.mock(ReporteScheduler.class);
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.initialize();
        var props = new ReportesProperties("America/Lima", Path.of("/tmp"), "a@b.pe", List.of("s@b.pe"), 20,
                Map.of("cartera-heredada", new ReportesProperties.Definicion(
                        "0 30 6 1 * *", "Cartera Heredada PDM - Stock %s", List.of("r@b.pe"),
                        ReportesProperties.EstrategiaCorte.FIN_MES_ANTERIOR)));
        var controller = new ReporteController(List.of(reporteFake), scheduler, props, executor);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void ejecutarRespondeAcceptedYDisparaElReporte() throws Exception {
        mockMvc.perform(post("/api/v1/reportes/cartera-heredada/ejecutar")
                        .param("corte", "2026-06-30"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.codigo").value("cartera-heredada"))
                .andExpect(jsonPath("$.corte").value("2026-06-30"))
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"));

        await().untilAsserted(() ->
                Mockito.verify(scheduler).ejecutar(any(ReporteService.class), eq(LocalDate.of(2026, 6, 30))));
    }

    @Test
    void reporteInexistenteResponde404() throws Exception {
        mockMvc.perform(post("/api/v1/reportes/no-existe/ejecutar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No existe el reporte 'no-existe'"));
    }

    @Test
    void corteFuturoResponde400() throws Exception {
        mockMvc.perform(post("/api/v1/reportes/cartera-heredada/ejecutar")
                        .param("corte", LocalDate.now().plusDays(5).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listadoExponeCodigoYCron() throws Exception {
        mockMvc.perform(get("/api/v1/reportes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("cartera-heredada"))
                .andExpect(jsonPath("$[0].cron").value("0 30 6 1 * *"))
                .andExpect(jsonPath("$[0].estrategiaCorte").value("FIN_MES_ANTERIOR"));
    }
}
