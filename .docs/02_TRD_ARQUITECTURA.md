# 02 — TRD Arquitectura y Stack (Spring Boot)
> **Proyecto:** Task Reportes — Orquestador de Reportes Financieros
> **Documentación Activa:** [README](./README.md) | [01_PRD_MIGRACION](./01_PRD_MIGRACION.md) | [02_TRD_ARQUITECTURA](./02_TRD_ARQUITECTURA.md) | [03_PARALELISMO_SCHEDULING](./03_PARALELISMO_SCHEDULING.md) | [04_EXCEL_CORREO](./04_EXCEL_CORREO.md) | [05_IMPLEMENTATION_PLAN](./05_IMPLEMENTATION_PLAN.md)
> **Versión:** 1.0.0
> **Fecha:** 2026-07-20
> **Estado:** 🟢 Especificación lista para construir

---

## 1. Stack Tecnológico Base

| Capa | Tecnología | Versión | Notas |
|---|---|---|---|
| Lenguaje | Java | 21 LTS | Records para DTOs; virtual threads NO para el pool de reportes (pool clásico dimensionado) |
| Framework | **Spring Boot** | 3.3+ | starters: web, jdbc, mail, validation, actuator |
| Build | **Maven** | 3.9+ | `pom.xml` (ver §2) |
| Acceso a datos | **Spring JDBC** (`JdbcTemplate` / `NamedParameterJdbcTemplate`) | — | Las queries son SQL crudo complejo → JDBC directo, no JPA (ver §5) |
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

    <!-- Driver de BD (ajustar según el motor real: postgresql / mssql-jdbc / ojdbc11) -->
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
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
│   ├── TaskReportesApplication.java        ← @SpringBootApplication @EnableScheduling @EnableAsync
│   │
│   ├── config/                             ← CONFIGURACIÓN
│   │   ├── AsyncConfig.java                  ThreadPoolTaskExecutor "reportTaskExecutor" (doc 03 §2)
│   │   ├── SchedulingConfig.java             Pool del scheduler (doc 03 §5)
│   │   ├── MailConfig.java                   Ajustes finos de JavaMailSender (si aplica)
│   │   └── properties/
│   │       ├── ReportesProperties.java       @ConfigurationProperties "reportes.*" (crons, destinatarios)
│   │       └── ExecutorProperties.java       @ConfigurationProperties "reportes.executor.*"
│   │
│   ├── scheduler/                          ← ORQUESTADOR
│   │   └── ReporteScheduler.java             @Scheduled por reporte; delega en los services; captura fallos
│   │
│   ├── service/                            ← SERVICIOS DE REPORTES (lógica paralela)
│   │   ├── ReporteService.java               Interfaz común: String codigo(); ReporteResultado generar(LocalDate corte)
│   │   ├── carteraheredada/
│   │   │   ├── CarteraHeredadaService.java     Orquesta N queries en paralelo → Excel → correo
│   │   │   └── CarteraHeredadaQueries.java     SQL crudo migrado 1:1 desde Node.js
│   │   ├── desembolsocanal/
│   │   │   ├── DesembolsoCanalService.java
│   │   │   └── DesembolsoCanalQueries.java
│   │   └── fondeoestable/
│   │       ├── FondeoEstableService.java
│   │       └── FondeoEstableQueries.java
│   │
│   ├── repository/                         ← ACCESO A DATOS
│   │   ├── ReporteJdbcRepository.java        Métodos @Async que ejecutan queries y devuelven CompletableFuture<List<T>>
│   │   └── rowmapper/                        RowMappers → Records (CarteraHeredadaFila, DesembolsoCanalFila, ...)
│   │
│   ├── excel/                              ← GENERADOR DE EXCEL
│   │   ├── ExcelGenerator.java               API genérica: hojas, cabeceras, estilos, autosize (doc 04 §2)
│   │   └── ExcelSheetSpec.java               Especificación declarativa de una hoja (título, columnas, filas)
│   │
│   ├── mail/                               ← SERVICIO DE CORREO
│   │   └── EmailService.java                 Envío con adjuntos; asunto normado; reintentos (doc 04 §3)
│   │
│   ├── web/                                ← API MANUAL (soporte)
│   │   ├── ReporteController.java            POST /api/v1/reportes/{codigo}/ejecutar → 202
│   │   └── GlobalExceptionHandler.java       ApiError { status, message, timestamp }
│   │
│   └── shared/
│       ├── ReporteResultado.java             Record: codigo, corte, rutaArchivo, filasTotales, duracionMs, estado
│       ├── ReporteException.java             Errores de negocio de la generación
│       └── CorrelacionUtils.java             MDC: reporteId + ejecucionId (RN-07)
│
├── src/main/resources/
│   ├── application.yml                     ← perfiles dev / prod (ver §4)
│   └── logback-spring.xml                  ← patrón con %X{ejecucionId}
├── src/test/java/...                       ← tests unitarios + integración (Awaitility para asíncronos)
├── Dockerfile
└── pom.xml
```

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
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10        # ≥ hilos del reportTaskExecutor (doc 03 §4)
      connection-timeout: 30000
  mail:
    host: ${SMTP_HOST}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USER}
    password: ${SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.connectiontimeout: 10000
      mail.smtp.timeout: 30000

reportes:
  zona-horaria: America/Lima
  directorio-temporal: ${TMP_REPORTES:/tmp/reportes}
  executor:
    core-size: 8
    max-size: 8
    queue-capacity: 50
    thread-name-prefix: report-exec-
  definiciones:
    cartera-heredada:
      cron: "0 30 6 * * *"            # ← copiar EXACTO del cron de Node.js (formato Spring: 6 campos)
      asunto: "Reporte Cartera Heredada - %s"
      destinatarios: [riesgos@empresa.pe, finanzas@empresa.pe]
    desembolso-canal:
      cron: "0 0 7 * * MON-FRI"
      asunto: "Reporte Desembolso Canal - %s"
      destinatarios: [canales@empresa.pe]
    fondeo-estable:
      cron: "0 0 8 1 * *"
      asunto: "Reporte Fondeo Estable - %s"
      destinatarios: [tesoreria@empresa.pe, finanzas@empresa.pe]

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, scheduledtasks
```

> ⚠️ **CRÍTICO — Formato cron:** Node.js (`node-cron`) usa **5 campos** (`min hora día mes díaSem`);
> Spring usa **6 campos** (agrega `segundos` al inicio). Al migrar, anteponer `0 `:
> `30 6 * * *` (Node) → `0 30 6 * * *` (Spring). Validar además la **zona horaria**
> con `zone = "America/Lima"` en cada `@Scheduled` (ver doc 03 §5).

---

## 5. Convenciones de Código

| Artefacto | Patrón | Ejemplo |
|---|---|---|
| Service de reporte | `PascalCase` + sufijo `Service` | `CarteraHeredadaService` |
| Clase de queries | Sufijo `Queries` (constantes `static final String`) | `CarteraHeredadaQueries` |
| DTO de fila | Record + sufijo `Fila` | `DesembolsoCanalFila` |
| Propiedades | `kebab-case` en YAML → `camelCase` en Java | `queue-capacity` → `queueCapacity` |
| Código de reporte | `kebab-case` (clave en YAML y en la API manual) | `cartera-heredada` |
| Hilos del pool | Prefijo configurable | `report-exec-1`, `report-exec-2` |

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
| Instancias | **1 réplica** (los `@Scheduled` no son cluster-aware; 2 réplicas = correos duplicados) |
| Zona horaria del contenedor | `TZ=America/Lima` + `zone` explícito en cada cron |
| Recursos | Memoria dimensionada para POI streaming (SXSSF mantiene ~100 filas en RAM) |
| Health-check | `GET /actuator/health` |
| Orquestación | Dokploy / Coolify, imagen en registry privado (mismo pipeline que el MIS Host) |