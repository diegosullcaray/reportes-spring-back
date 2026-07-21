# 03 — Paralelismo y Scheduling (`@Async` + `CompletableFuture` + `@Scheduled`)
> **Proyecto:** Task Reportes — Orquestador de Reportes Financieros
> **Documentación Activa:** [README](./README.md) | [01_PRD_MIGRACION](./01_PRD_MIGRACION.md) | [02_TRD_ARQUITECTURA](./02_TRD_ARQUITECTURA.md) | [03_PARALELISMO_SCHEDULING](./03_PARALELISMO_SCHEDULING.md) | [04_EXCEL_CORREO](./04_EXCEL_CORREO.md) | [05_IMPLEMENTATION_PLAN](./05_IMPLEMENTATION_PLAN.md)
> **Versión:** 1.0.0
> **Fecha:** 2026-07-20
> **Estado:** 🟢 Especificación lista para construir

---

Este documento es el **núcleo técnico de la migración**: cómo reemplazar el modelo
asíncrono de Node.js (`Promise.all` sobre el event loop) por un paralelismo explícito y
controlado en Spring Boot, garantizando que el procesamiento siga siendo
**estrictamente paralelo, nunca secuencial** (RN-01).

## 1. Equivalencia Conceptual

```
Node.js                                     Spring Boot
─────────────────────────────────────       ─────────────────────────────────────────────
cron.schedule('30 6 * * *', job)      →     @Scheduled(cron = "0 30 6 * * *", zone = "America/Lima")

const [a, b, c] = await Promise.all([ →     CompletableFuture<List<A>> fa = repo.queryA(corte); // @Async
  queryA(), queryB(), queryC()               CompletableFuture<List<B>> fb = repo.queryB(corte); // @Async
]);                                          CompletableFuture<List<C>> fc = repo.queryC(corte); // @Async
                                             CompletableFuture.allOf(fa, fb, fc).join();
                                             var a = fa.join(); var b = fb.join(); var c = fc.join();
```

> **Regla de oro:** las `CompletableFuture` se **lanzan todas primero** (cada llamada
> `@Async` retorna de inmediato) y **recién después** se espera con `allOf(...)`.
> Llamar `.join()` inmediatamente después de cada invocación serializa la ejecución
> y viola RN-01.

---

## 2. Configuración del Executor (`AsyncConfig`)

`ThreadPoolTaskExecutor` **personalizado y dedicado** — nunca el executor por defecto (RN-06).

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Pool dedicado a la ejecución paralela de queries y generación de reportes.
     * Dimensionamiento inicial: core = max = 8 (workload IO-bound sobre la BD;
     * el límite real lo impone el pool de conexiones Hikari — ver §4).
     */
    @Bean(name = "reportTaskExecutor")
    public ThreadPoolTaskExecutor reportTaskExecutor(ExecutorProperties props) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.coreSize());        // 8
        executor.setMaxPoolSize(props.maxSize());          // 8
        executor.setQueueCapacity(props.queueCapacity());  // 50
        executor.setThreadNamePrefix(props.threadNamePrefix()); // "report-exec-"
        // Si el pool y la cola están llenos, ejecuta en el hilo llamador
        // (backpressure natural: el scheduler espera en vez de descartar tareas).
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Propaga el MDC (ejecucionId) del hilo llamador a los hilos del pool (RN-07).
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /** Manejador global para excepciones en métodos @Async void (no debería haber: siempre retornar CompletableFuture). */
    @Bean
    public AsyncUncaughtExceptionHandler asyncExceptionHandler() {
        return (ex, method, params) ->
            LoggerFactory.getLogger(AsyncConfig.class)
                         .error("Excepción no capturada en método async {}", method.getName(), ex);
    }
}
```

```java
/** Copia el contexto MDC del hilo que agenda la tarea al hilo del pool. */
public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contexto = MDC.getCopyOfContextMap();
        return () -> {
            if (contexto != null) MDC.setContextMap(contexto);
            try { runnable.run(); } finally { MDC.clear(); }
        };
    }
}
```

### Reglas del executor

| # | Regla |
|---|---|
| EX-01 | Todo `@Async` referencia el bean por nombre: `@Async("reportTaskExecutor")`. Prohibido `@Async` sin calificador. |
| EX-02 | Los métodos `@Async` retornan **siempre** `CompletableFuture<T>` (nunca `void`): sin él, las excepciones se pierden. |
| EX-03 | `@Async` solo funciona a través del **proxy de Spring**: el método debe ser `public`, en un bean distinto al llamador (AR-05). |
| EX-04 | `CallerRunsPolicy` como política de rechazo: ante saturación se degrada a ejecución en el llamador, jamás se descarta una query. |
| EX-05 | Todo `join()`/`get()` de espera global lleva **timeout** (`orTimeout` / `get(n, MINUTES)`) para que un query colgado no congele el scheduler. |

---

## 3. Servicio de Reporte — Patrón `Promise.all` → `allOf`

### 3.1 Repositorio con métodos `@Async`

```java
@Repository
public class ReporteJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ReporteJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<CarteraHeredadaFila>> consultarCarteraPorAgencia(LocalDate corte) {
        var params = new MapSqlParameterSource("corte", corte);
        var filas = jdbc.query(CarteraHeredadaQueries.CARTERA_POR_AGENCIA, params,
                               new CarteraHeredadaFilaRowMapper());
        return CompletableFuture.completedFuture(filas);
    }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<ResumenCanalFila>> consultarResumenPorCanal(LocalDate corte) { /* ... */ }

    @Async("reportTaskExecutor")
    public CompletableFuture<List<MoraFila>> consultarIndicadoresMora(LocalDate corte) { /* ... */ }
}
```

### 3.2 Service que orquesta el paralelismo

```java
@Service
public class CarteraHeredadaService implements ReporteService {

    private static final Logger log = LoggerFactory.getLogger(CarteraHeredadaService.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(15);

    private final ReporteJdbcRepository repository;
    private final ExcelGenerator excelGenerator;
    private final EmailService emailService;
    private final ReportesProperties properties;

    // constructor...

    @Override
    public String codigo() { return "cartera-heredada"; }

    @Override
    public ReporteResultado generar(LocalDate corte) {
        long inicio = System.currentTimeMillis();
        log.info("Iniciando reporte cartera-heredada, corte={}", corte);

        // 1) LANZAR TODO EN PARALELO (equivalente a construir el array de Promise.all)
        var fCartera = repository.consultarCarteraPorAgencia(corte);
        var fCanales = repository.consultarResumenPorCanal(corte);
        var fMora    = repository.consultarIndicadoresMora(corte);

        // 2) ESPERAR A QUE TODAS TERMINEN (equivalente a await Promise.all([...]))
        try {
            CompletableFuture.allOf(fCartera, fCanales, fMora)
                             .orTimeout(TIMEOUT.toMinutes(), TimeUnit.MINUTES)
                             .join();
        } catch (CompletionException e) {
            // Falla si CUALQUIER query falla — mismo semántica que Promise.all
            throw new ReporteException("Fallo en consultas de cartera-heredada", e.getCause());
        }

        // 3) RESULTADOS (join() aquí ya no bloquea: todas completaron)
        var cartera = fCartera.join();
        var canales = fCanales.join();
        var mora    = fMora.join();

        // 4) EXCEL (una hoja por dataset) — ver doc 04
        Path archivo = excelGenerator.generar(
            "cartera-heredada_" + corte,
            List.of(
                ExcelSheetSpec.de("Cartera por Agencia", cartera),
                ExcelSheetSpec.de("Resumen por Canal",  canales),
                ExcelSheetSpec.de("Indicadores de Mora", mora)
            ));

        // 5) CORREO — ver doc 04
        var def = properties.definicion(codigo());
        emailService.enviarConAdjunto(def.destinatarios(),
                                      def.asunto().formatted(corte),
                                      archivo);

        long duracion = System.currentTimeMillis() - inicio;
        return ReporteResultado.exitoso(codigo(), corte, archivo,
                                        cartera.size() + canales.size() + mora.size(), duracion);
    }
}
```

> **Verificación de CA-02/CA-03:** en los logs deben verse las tres queries iniciando
> casi simultáneamente en hilos `report-exec-1..3`, y la duración total ≈ la query más
> lenta (no la suma). Un test de integración lo valida con queries `pg_sleep` (FASE 4).

### 3.3 Variante — combinar sin bloquear (composición)

Cuando el resultado de las queries se combina antes del Excel, se puede componer:

```java
CompletableFuture<Path> archivo =
    fCartera.thenCombineAsync(fCanales, this::combinar, reportTaskExecutor)
            .thenCombineAsync(fMora, this::agregarMora, reportTaskExecutor)
            .thenApplyAsync(datos -> excelGenerator.generar("...", datos), reportTaskExecutor);
```

> Usar la sobrecarga `*Async(..., executor)` explícita: sin ella, la continuación corre
> en `ForkJoinPool.commonPool()`, fuera de nuestro pool controlado (viola RN-06).

---

## 4. Dimensionamiento del Pool

| Parámetro | Valor inicial | Justificación |
|---|---|---|
| `corePoolSize` = `maxPoolSize` | **8** | Workload IO-bound (espera de BD). Pool fijo = latencia predecible. |
| `queueCapacity` | **50** | Absorbe picos cuando varios crons coinciden en horario. |
| Hikari `maximum-pool-size` | **10** | ≥ hilos del executor + margen para el endpoint manual y Actuator. |

> ⚠️ **Regla de coherencia:** `hikari.maximum-pool-size ≥ executor.maxPoolSize`.
> Si hay más hilos que conexiones, los hilos "paralelos" se serializan esperando
> conexión — paralelismo ilusorio. Revisar también el `max_connections` del motor.

Ajuste fino en FASE 5 con las métricas de Micrometer (`executor.active`, `executor.queued`,
`hikaricp.connections.usage`).

---

## 5. Scheduling (`@Scheduled`)

### 5.1 Configuración del scheduler

```java
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    /**
     * Pool del scheduler: 1 hilo POR REPORTE activo (el hilo del scheduler queda
     * bloqueado en allOf().join() mientras el pool "reportTaskExecutor" trabaja).
     * Con 1 solo hilo (default de Spring), dos reportes con cron simultáneo se
     * ejecutarían en secuencia — inaceptable (RN-01/RN-03).
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);                       // ≥ nº de reportes que pueden coincidir
        scheduler.setThreadNamePrefix("report-sched-");
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
    }
}
```

### 5.2 Orquestador

```java
@Component
public class ReporteScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReporteScheduler.class);

    private final CarteraHeredadaService carteraHeredada;
    private final DesembolsoCanalService desembolsoCanal;
    private final FondeoEstableService fondeoEstable;

    // constructor...

    @Scheduled(cron = "${reportes.definiciones.cartera-heredada.cron}", zone = "${reportes.zona-horaria}")
    public void ejecutarCarteraHeredada() { ejecutar(carteraHeredada); }

    @Scheduled(cron = "${reportes.definiciones.desembolso-canal.cron}", zone = "${reportes.zona-horaria}")
    public void ejecutarDesembolsoCanal() { ejecutar(desembolsoCanal); }

    @Scheduled(cron = "${reportes.definiciones.fondeo-estable.cron}", zone = "${reportes.zona-horaria}")
    public void ejecutarFondeoEstable() { ejecutar(fondeoEstable); }

    /** Plantilla común: correlación, fecha de corte, captura de fallos (RN-03, RN-07). */
    private void ejecutar(ReporteService reporte) {
        MDC.put("ejecucionId", reporte.codigo() + "-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            var resultado = reporte.generar(LocalDate.now(ZONA).minusDays(1)); // corte = día anterior
            log.info("Reporte {} OK: {} filas en {} ms", reporte.codigo(),
                     resultado.filasTotales(), resultado.duracionMs());
        } catch (Exception e) {
            // RN-03: el fallo se registra y notifica, pero NUNCA se propaga al scheduler
            log.error("Reporte {} FALLÓ", reporte.codigo(), e);
            // notificación de fallo a soporte (correo simple, sin adjunto)
        } finally {
            MDC.clear();
        }
    }
}
```

### 5.3 Reglas de scheduling

| # | Regla |
|---|---|
| SC-01 | Cron en formato Spring de **6 campos** (anteponer `0 ` de segundos al cron de 5 campos de Node.js). |
| SC-02 | Todas las expresiones cron viven en `application.yml`; prohibido hardcodear crons en las anotaciones. |
| SC-03 | `zone = America/Lima` explícito: el horario no depende de la TZ del contenedor. |
| SC-04 | El scheduler captura **toda** excepción (RN-03): un reporte caído no cancela las siguientes ejecuciones programadas. |
| SC-05 | Una sola réplica del servicio en producción (los `@Scheduled` no coordinan entre instancias). Si a futuro se requiere HA, migrar a ShedLock o Quartz clusterizado. |

---

## 6. Ejecución Manual (endpoint de soporte)

```java
@RestController
@RequestMapping("/api/v1/reportes")
public class ReporteController {

    private final Map<String, ReporteService> reportes; // inyectados por código()
    private final ThreadPoolTaskExecutor reportTaskExecutor;

    @PostMapping("/{codigo}/ejecutar")
    public ResponseEntity<Map<String, String>> ejecutar(@PathVariable String codigo,
                                                        @RequestParam(required = false) LocalDate corte) {
        var reporte = Optional.ofNullable(reportes.get(codigo))
                              .orElseThrow(() -> new ReporteNoEncontradoException(codigo));
        var fecha = corte != null ? corte : LocalDate.now().minusDays(1);
        // Lanzar y responder de inmediato (CA-07: 202 Accepted)
        CompletableFuture.runAsync(() -> reporte.generar(fecha), reportTaskExecutor);
        return ResponseEntity.accepted()
                             .body(Map.of("codigo", codigo, "corte", fecha.toString(), "estado", "EN_PROCESO"));
    }
}
```

---

## 7. Errores Comunes a Evitar (checklist de code review)

| ❌ Anti-patrón | ✅ Correcto |
|---|---|
| `repo.queryA().join(); repo.queryB().join();` (serializa) | Lanzar todas, luego `allOf(...).join()` |
| `@Async` en método del mismo bean que lo llama (self-invocation: no pasa por el proxy, corre síncrono) | Métodos `@Async` en `ReporteJdbcRepository`, llamados desde el service |
| `@Async` sin calificador (usa el executor default) | `@Async("reportTaskExecutor")` |
| `thenApply` tras `allOf` sin executor (corre en commonPool) | `thenApplyAsync(fn, reportTaskExecutor)` |
| `allOf(...).join()` sin timeout | `.orTimeout(15, TimeUnit.MINUTES)` |
| Cron de 5 campos copiado literal de Node.js | Anteponer campo de segundos (SC-01) |
| `@Transactional` alrededor de la orquestación completa (retiene conexión durante Excel/correo) | Transacción (si hiciera falta) solo dentro de cada método de query |