# 02 — TRD Arquitectura y Stack (Spring Boot)
> **Proyecto:** Task Reportes — Orquestador de Reportes Financieros
> **Documentación Activa:** [README](./README.md) | [01_PRD_MIGRACION](./01_PRD_MIGRACION.md) | [02_TRD_ARQUITECTURA](./02_TRD_ARQUITECTURA.md) | [03_PARALELISMO_SCHEDULING](./03_PARALELISMO_SCHEDULING.md) | [04_EXCEL_CORREO](./04_EXCEL_CORREO.md) | [05_IMPLEMENTATION_PLAN](./05_IMPLEMENTATION_PLAN.md) | [06_DESPLIEGUE_LOCAL](./06_DESPLIEGUE_LOCAL.md)
> **Versión:** 2.0.0
> **Fecha:** 2026-07-21
> **Estado:** ✅ Construido — este documento refleja la arquitectura implementada en `src/main/java/pe/confianza/reportes`

---

## 1. Stack Tecnológico Base

| Capa | Tecnología | Versión | Notas |
|---|---|---|---|
| Lenguaje | Java | 21 LTS | Records para DTOs; virtual threads NO para el pool de reportes (pool clásico dimensionado) |
| Framework | **Spring Boot** | 3.3+ | starters: web, jdbc, mail, validation, actuator |
| Build | **Maven** | 3.9+ | `pom.xml` (ver §2) |
| Acceso a datos | **Spring JDBC** (`NamedParameterJdbcTemplate`) | — | Las queries son SQL crudo complejo → JDBC directo, no JPA (ver §5) |
| Base de datos | **SQL Server** (driver `mssql-jdbc`) | — | Motor real de las queries migradas (bases `dma`, `dwh`, `storage`); los scripts T-SQL con tablas temporales corren como un solo batch (`sp_executesql`), por lo que las `#temp` no contaminan las conexiones del pool |
| Pool de conexiones | HikariCP | — | Dimensionado acorde al pool de hilos (§4 del doc 03) |
| Excel | **Apache POI** | 5.2+ | `poi-ooxml` con `SXSSFWorkbook` (streaming) |
| Correo | **Spring Boot Starter Mail** | — | `JavaMailSender` + `MimeMessageHelper` (adjuntos) |
| Scheduling | Spring `@Scheduled` | — | `@EnableScheduling`; expresiones cron 1:1 con Node.js |
| Concurrencia | `ThreadPoolTaskExecutor` + `@Async` + `CompletableFuture` | — | Ver [doc 03](./03_PARALELISMO_SCHEDULING.md) |
| Observabilidad | Actuator + Micrometer | — | health, métricas del executor, MDC en logs |
| Contenedores | Docker (multi-stage) | — | JRE 21 alpine; Dokploy / Coolify |

> **Decisión JDBC vs JPA:** el proyecto es de *lectura analítica* (queries SQL complejas ya
> escritas en Node.js) sin modelo de dominio mutable. `JdbcTemplate` permite migrar las
> queries **tal cual** y mapear a Records. JPA/Hibernate no aporta valor aquí y añade
> overhead. Si más adelante se agrega persistencia propia (bitácora de ejecuciones), se
> puede sumar Spring Data JDBC sin conflicto.

---

## 2. `pom.xml` — Dependencias Obligatorias

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
    <relativePath/>
  </parent>

  <groupId>pe.confianza.reportes</groupId>
  <artifactId>task-reportes-back</artifactId>
  <version>1.0.0</version>
  <name>task-reportes-back</name>
  <description>Orquestador de reportes financieros programados (migración Node.js → Spring Boot)</description>

  <properties>
    <java.version>21</java.version>
    <poi.version>5.2.5</poi.version>
  </properties>

  <dependencies>
    <!-- Web: endpoint manual de disparo + Actuator -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Acceso a datos: JdbcTemplate + HikariCP -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>

    <!-- Correo: JavaMailSender -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-mail</artifactId>
    </dependency>

    <!-- Validación de propiedades de configuración -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Observabilidad -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Apache POI: generación de .xlsx -->
    <dependency>
      <groupId>org.apache.poi</groupId>
      <artifactId>poi-ooxml</artifactId>
      <version>${poi.version}</version>
    </dependency>

    <!-- Motor real: SQL Server (las queries migradas usan las bases dma / dwh / storage) -->
    <dependency>
      <groupId>com.microsoft.sqlserver</groupId>
      <artifactId>mssql-jdbc</artifactId>
      <scope>runtime</scope>
    </dependency>

    <!-- Tests -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.awaitility</groupId>
      <artifactId>awaitility</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

---

## 3. Estructura del Proyecto (paquetes)

Diseño limpio con separación estricta: **Configuración · Orquestador (Scheduler) ·
Servicios de Reportes (lógica paralela) · Generador de Excel · Servicio de Correo**.

```
task-reportes-back/
├── src/main/java/pe/confianza/reportes/
│   ├── TaskReportesApplication.java        ← @SpringBootApplication @ConfigurationPropertiesScan
│   │
│   ├── config/                             ← CONFIGURACIÓN
│   │   ├── AsyncConfig.java                  @EnableAsync + ThreadPoolTaskExecutor "reportTaskExecutor" (doc 03 §2)
│   │   ├── MdcTaskDecorator.java             Propaga el MDC (ejecucionId) a los hilos del pool (RN-07)
│   │   ├── SchedulingConfig.java             @EnableScheduling + registro DINÁMICO: un CronTrigger por bean
│   │   │                                     ReporteService leyendo reportes.definiciones.* (AR-07/SC-02)
│   │   └── properties/
│   │       ├── ReportesProperties.java       @ConfigurationProperties "reportes.*" (record @Validated: zona horaria,
│   │       │                                 dir temporal, remitente, soporte, adjunto-max-mb, definiciones por
│   │       │                                 reporte con cron/asunto/destinatarios/estrategia de corte)
│   │       └── ExecutorProperties.java       @ConfigurationProperties "reportes.executor.*"
│   │
│   ├── scheduler/                          ← ORQUESTADOR
│   │   └── ReporteScheduler.java             Sin lógica de negocio (AR-01): resuelve corte según la estrategia
│   │                                         (DIA_ANTERIOR | FIN_MES_ANTERIOR), correlación MDC, captura fallos
│   │                                         y notifica a soporte (RN-03). Usado por cron y por la API manual.
│   │
│   ├── service/                            ← SERVICIOS DE REPORTES (lógica paralela)
│   │   ├── ReporteService.java               Interfaz común: String codigo(); ReporteResultado generar(LocalDate corte)
│   │   ├── ReporteSupport.java               Paso final común: Excel → cuerpo HTML (advertencia de hojas vacías,
│   │   │                                     RN-04) → correo → ReporteResultado
│   │   ├── carteraheredada/                  CarteraHeredadaService + CarteraHeredadaQueries + CarteraHeredadaFila
│   │   ├── desembolsocanal/                  DesembolsoCanalService + Queries + Fila
│   │   ├── fondeoestable/                    FondeoEstableService + Queries + Fila
│   │   ├── saldomediovigente/                2 queries en paralelo → 2 hojas (Diana)
│   │   ├── sacatugarra/                      Base Saca tu Garra (Giancarlo)
│   │   ├── datoscierre/                      3 queries en paralelo: ratio CE + clientes rurales/migrantes
│   │   ├── seguros/                          Penetración de seguros, 47 columnas (Giovani)
│   │   ├── saldopuntual/                     Saldo puntual + saldo medio (Giovani)
│   │   ├── carteravigenteagro/               Saldo vigente actual/anterior + clientes (Giovani)
│   │   └── validacioncubo/                   VALIDACIÓN DIARIA: indicadores del cubo + reglas OK/ALERTA/ADVERTENCIA
│   │
│   ├── repository/                         ← ACCESO A DATOS (un repositorio por reporte)
│   │   ├── CarteraHeredadaRepository.java    Métodos @Async("reportTaskExecutor") → CompletableFuture<List<Fila>>
│   │   ├── ... (DesembolsoCanal, FondeoEstable, SaldoMedioVigente, SacaTuGarra,
│   │   │        DatosCierre, Seguros, SaldoPuntual, CarteraVigenteAgro, ValidacionCubo)
│   │   └── support/RowMapperUtils.java       Helpers de mapeo (fechas char(8)/date, SIN ASIGNAR, decimales)
│   │
│   ├── excel/                              ← GENERADOR DE EXCEL
│   │   ├── ExcelGenerator.java               API genérica SXSSF: hojas, cabeceras, autofiltro, leyenda vacío (doc 04 §2)
│   │   ├── ExcelSheetSpec.java               Especificación declarativa de una hoja (título, columnas, filas)
│   │   ├── EstilosReporte.java               Estilos creados una sola vez por workbook (XL-04)
│   │   └── FormatoCelda.java                 TEXTO | ENTERO | MONTO | FECHA | PORCENTAJE
│   │
│   ├── mail/                               ← SERVICIO DE CORREO
│   │   └── EmailService.java                 Adjuntos con reintentos 2s/4s/8s, zip > 20 MB, borrado post-envío (doc 04 §3)
│   │
│   ├── web/                                ← API MANUAL (soporte)
│   │   ├── ReporteController.java            GET /api/v1/reportes · POST /api/v1/reportes/{codigo}/ejecutar → 202
│   │   ├── GlobalExceptionHandler.java       404 reporte inexistente · 400 corte futuro/inválido · 500 genérico
│   │   └── ApiError.java                     { status, message, timestamp }
│   │
│   └── shared/
│       ├── ReporteResultado.java             Record: codigo, corte, rutaArchivo, filasTotales, duracionMs, estado
│       ├── ReporteException.java             Errores de negocio de la generación
│       ├── ReporteNoEncontradoException.java Código de reporte no registrado (→ 404)
│       └── CorrelacionUtils.java             MDC: ejecucionId por corrida (RN-07)
│
├── src/main/resources/
│   ├── application.yml                     ← configuración base + definiciones de los 10 reportes (ver §4)
│   ├── application-dev.yml                 ← perfil dev: Mailpit local, destinatarios dev@localhost
│   └── logback-spring.xml                  ← patrón con %X{ejecucionId}
├── src/test/java/pe/confianza/reportes/    ← 17 tests: Excel, correo (reintentos), validación cubo,
│                                             properties, controller y arranque completo del contexto
├── Dockerfile                              ← multi-stage (maven → JRE 21 alpine)
├── docker-compose.yml                      ← app + Mailpit para despliegue local (doc 06)
├── .env.example                            ← plantilla de variables de entorno (doc 06)
└── pom.xml
```

> **Nota de implementación (scheduler):** a diferencia del sketch original con un
> método `@Scheduled` por reporte, `SchedulingConfig` registra los crons
> **dinámicamente**: recorre los beans `ReporteService` y crea un `CronTrigger`
> con el cron y la zona de `reportes.definiciones.<codigo>`. Así, agregar un
> reporte nuevo no toca el scheduler (AR-07) y ningún cron queda hardcodeado
> (SC-02). Si un reporte no tiene bloque en el YAML, la aplicación **falla al
> arranque** con un mensaje claro — validación deliberada para evitar reportes
> silenciosamente sin programar.

### Reglas de arquitectura (no negociables)

| # | Regla |
|---|---|
| AR-01 | El **scheduler no contiene lógica de negocio**: solo resuelve la fecha de corte, invoca al service del reporte y registra el resultado/fallo. |
| AR-02 | Cada reporte vive en su **propio subpaquete** de `service/` con sus queries al lado; un reporte no importa clases de otro reporte. |
| AR-03 | El **SQL vive en constantes** de las clases `*Queries.java` (text blocks de Java 21), migrado carácter a carácter desde Node.js; prohibido concatenar SQL con datos de entrada (usar parámetros nombrados). |
| AR-04 | `ExcelGenerator` y `EmailService` son **genéricos y sin conocimiento del negocio**: reciben especificaciones (`ExcelSheetSpec`, destinatarios, asunto) y no saben qué reporte los invoca. |
| AR-05 | Los métodos `@Async` viven en **beans distintos** a sus llamadores (proxy de Spring); prohibido el self-invocation. |
| AR-06 | Toda clase de configuración usa `@ConfigurationProperties` tipado + `@Validated`; prohibido `@Value` disperso. |
| AR-07 | Nuevo reporte = nuevo subpaquete + bean `ReporteService` + bloque en `application.yml`. **Cero cambios** en scheduler genérico, Excel o correo. |

---

## 4. Configuración (`application.yml`)

```yaml
spring:
  application:
    name: task-reportes-back
  datasource:
    # SQL Server; el default apunta a localhost para desarrollo (ver doc 06)
    url: ${DB_URL:jdbc:sqlserver://localhost:1433;databaseName=storage;encrypt=true;trustServerCertificate=true}
    username: ${DB_USER:sa}
    password: ${DB_PASSWORD:changeit}
    hikari:
      maximum-pool-size: 10        # ≥ hilos del reportTaskExecutor (doc 03 §4)
      connection-timeout: 30000
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:1025}        # default: Mailpit local (doc 06)
    username: ${SMTP_USER:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.connectiontimeout: 10000
      mail.smtp.timeout: 30000

reportes:
  zona-horaria: America/Lima
  directorio-temporal: ${TMP_REPORTES:/tmp/reportes}
  correo-remitente: ${MAIL_FROM:reportes@confianza.pe}
  correo-soporte: [ "${MAIL_SOPORTE:soporte-ti@confianza.pe}" ]   # notificación de fallos (RN-03)
  adjunto-max-mb: 20               # sobre este tamaño el adjunto se comprime a .zip (MA-06)
  executor:
    core-size: 8
    max-size: 8
    queue-capacity: 50
    thread-name-prefix: report-exec-
  # Un bloque por reporte; la clave es el código kebab-case que devuelve
  # ReporteService.codigo(). `corte` define la estrategia de fecha de corte:
  # DIA_ANTERIOR (diarios) o FIN_MES_ANTERIOR (mensuales).
  definiciones:
    validacion-cubo:
      cron: "0 0 7 * * *"             # ← copiar EXACTO del cron de Node.js (formato Spring: 6 campos)
      asunto: "Validación Cubo Diaria - %s"
      destinatarios: [ "${MAIL_VALIDACIONES:mis-datos@confianza.pe}" ]
      corte: DIA_ANTERIOR
    cartera-heredada:
      cron: "0 30 6 1 * *"
      asunto: "Cartera Heredada PDM - Stock %s"
      destinatarios: [ "${MAIL_RIESGOS:riesgos@confianza.pe}" ]
      corte: FIN_MES_ANTERIOR
    # ... desembolso-canal, fondeo-estable, saldo-medio-vigente, saca-tu-garra,
    #     datos-cierre, reporte-seguros, saldo-puntual-medio, cartera-vigente-agro
    #     (ver src/main/resources/application.yml — misma estructura)

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, scheduledtasks
```

> ⚠️ **Placeholders en listas YAML:** dentro de una lista *flow* (`[ ... ]`) los
> placeholders `${VAR:default}` deben ir **entre comillas** — la llave `{` rompe
> el parser de YAML si va sin comillas.

> ⚠️ **CRÍTICO — Formato cron:** Node.js (`node-cron`) usa **5 campos** (`min hora día mes díaSem`);
> Spring usa **6 campos** (agrega `segundos` al inicio). Al migrar, anteponer `0 `:
> `30 6 * * *` (Node) → `0 30 6 * * *` (Spring). Validar además la **zona horaria**
> con `zone = "America/Lima"` en cada `@Scheduled` (ver doc 03 §5).

---

## 5. Convenciones de Código

| Artefacto | Patrón | Ejemplo |
|---|---|---|
| Service de reporte | `PascalCase` + sufijo `Service` | `CarteraHeredadaService` |
| Repositorio de reporte | Sufijo `Repository`, métodos `@Async("reportTaskExecutor")` | `CarteraHeredadaRepository` |
| Clase de queries | Sufijo `Queries` (constantes `static final String`, text blocks) | `CarteraHeredadaQueries` |
| DTO de fila | Record + sufijo `Fila` | `DesembolsoCanalFila` |
| Propiedades | `kebab-case` en YAML → `camelCase` en Java | `queue-capacity` → `queueCapacity` |
| Código de reporte | `kebab-case` (clave en YAML y en la API manual) | `cartera-heredada` |
| Estrategia de corte | Enum en la definición del reporte | `DIA_ANTERIOR`, `FIN_MES_ANTERIOR` |
| Hilos del pool | Prefijo configurable | `report-exec-1`, `report-exec-2` |
| Hilos del scheduler | Prefijo fijo | `report-sched-1` |
| Archivo generado | `<codigo>_<corte>.xlsx` en el dir temporal (XL-06) | `fondeo-estable_2026-06-30.xlsx` |

---

## 6. Estrategia de Despliegue

### Dockerfile (multi-stage)

```dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/task-reportes-back-*.jar app.jar
ENV TZ=America/Lima
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

| Aspecto | Decisión |
|---|---|
| Instancias | **1 réplica** (los crons registrados no son cluster-aware; 2 réplicas = correos duplicados) |
| Zona horaria del contenedor | `TZ=America/Lima` + zona explícita en cada `CronTrigger` |
| Recursos | Memoria dimensionada para POI streaming (SXSSF mantiene ~100 filas en RAM) |
| Health-check | `GET /actuator/health` |
| Orquestación | Dokploy / Coolify, imagen en registry privado (mismo pipeline que el MIS Host) |

> El **despliegue local** (variables de entorno, Mailpit, comandos de arranque y
> verificación) está detallado en el [doc 06 — Despliegue Local](./06_DESPLIEGUE_LOCAL.md).