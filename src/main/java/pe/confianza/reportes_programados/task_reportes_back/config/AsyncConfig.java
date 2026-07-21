package pe.confianza.reportes_programados.task_reportes_back.config;

import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import pe.confianza.reportes_programados.task_reportes_back.config.properties.ExecutorProperties;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Pool dedicado a la ejecución paralela de queries y generación de reportes (RN-06).
     * Workload IO-bound sobre la BD; el límite real lo impone el pool de conexiones Hikari.
     */
    @Bean(name = "reportTaskExecutor")
    public ThreadPoolTaskExecutor reportTaskExecutor(ExecutorProperties props) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.coreSize());
        executor.setMaxPoolSize(props.maxSize());
        executor.setQueueCapacity(props.queueCapacity());
        executor.setThreadNamePrefix(props.threadNamePrefix());
        // Backpressure natural: ante saturación se ejecuta en el hilo llamador, jamás se descarta (EX-04).
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean
    public AsyncUncaughtExceptionHandler asyncExceptionHandler() {
        return (ex, method, params) ->
                LoggerFactory.getLogger(AsyncConfig.class)
                        .error("Excepción no capturada en método async {}", method.getName(), ex);
    }
}
