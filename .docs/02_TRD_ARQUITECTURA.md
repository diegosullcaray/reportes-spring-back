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
| Despliegue | Jar ejecutable (`spring-boot-maven-plugin`) | — | `java -jar` como servicio del sistema; sin contenedores |

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
│   │   ├── DataSourceConfig.java             Hikari perezoso con la URL armada por DbProperties (NTLM/instancia)
│   │   ├── MdcTaskDecorator.java             Propaga el MDC (ejecucionId) a los hilos del pool (RN-07)
│   │   ├── SchedulingConfig.java             @EnableScheduling + registro DINÁMICO: un CronTrigger por bean
│   │   │                                     ReporteService leyendo reportes.definiciones.* (AR-07/SC-02)
│   │   └── properties/
│   │       ├── DbProperties.java             @ConfigurationProperties "db.*" — conexión por partes estilo SSMS
│   │       │                                 (server/instance/domain NTLM) y jdbcUrl() que arma la cadena
│   │       ├── ReportesProperties.java       @ConfigurationProperties "reportes.*" (record @Validated: zona horaria,
│   │       │                                 dir salida, remitente, soporte, firma, webhook Chat, definiciones por
│   │       │                                 reporte con cron/asunto/PARA/CC/estrategia de corte)
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
│   ├── mail/                               ← NOTIFICACIONES
│   │   ├── EmailService.java                 Adjuntos con PARA/CC, reintentos 2s/4s/8s, zip > 20 MB, borrado post-envío (doc 04 §3)
│   │   └── GoogleChatNotifier.java           Webhook opcional de Google Chat (resultado de la validación diaria)
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
│   ├── application-dev.yml                 ← perfil dev: SMTP local, destinatarios dev@localhost
│   └── logback-spring.xml                  ← patrón con %X{ejecucionId}
├── src/test/java/pe/confianza/reportes/    ← 17 tests: Excel, correo (reintentos), validación cubo,
│                                             properties, controller y arranque completo del contexto
└── pom.xml                                 ← empaquetado jar ejecutable (spring-boot-maven-plugin)
```

> La configuración local personal vive en `src/main/resources/application-local.yml`
> (perfil `local`, ignorado por git) — ver [doc 06](./06_DESPLIEGUE_LOCAL.md).

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

Las variables de entorno usan **los mismos nombres que el `.env` del proyecto
Node.js** (`DB_SERVER`, `DB_DOMAIN`, `EMAIL_USER`, `*_PARA`/`*_CC`, …) para que
la configuración existente se traslade sin renombrar nada — catálogo completo
en el [doc 06](./06_DESPLIEGUE_LOCAL.md).

```yaml
spring:
  application:
    name: task-reportes-back
  mail:
    # Google Workspace; EMAIL_PASSWORD = contraseña de aplicación (16 caracteres
    # juntos). Si el firewall bloquea el 465: EMAIL_PORT=587 + EMAIL_SSL=false
    # + EMAIL_STARTTLS=true.
    host: ${EMAIL_HOST:smtp.gmail.com}
    port: ${EMAIL_PORT:465}
    username: ${EMAIL_USER:usuario@confianza.pe}
    password: ${EMAIL_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.ssl.enable: ${EMAIL_SSL:true}
      mail.smtp.starttls.enable: ${EMAIL_STARTTLS:false}

# Conexión a SQL Server POR PARTES (como la ventana "Connect to Server" de SSMS).
# La cadena JDBC la arma DbProperties.jdbcUrl(): DB_DOMAIN definido → Windows
# Authentication (authenticationScheme=NTLM); DB_INSTANCE definido → instancia
# nombrada (instanceName=...); si no, puerto directo. El DataSource lo construye
# DataSourceConfig con Hikari en modo perezoso (arranca sin BD accesible).
db:
  server: ${DB_SERVER:localhost}
  database: ${DB_DATABASE:storage}
  domain: ${DB_DOMAIN:}
  username: ${DB_USERNAME:sa}
  password: ${DB_PASSWORD:}
  instance: ${DB_INSTANCE:}
  port: ${DB_PORT:1433}
  encryption: ${DB_ENCRYPTION:false}
  trust-certificate: ${DB_TRUST_CERTIFICATE:true}
  pool-size: 10                    # ≥ hilos del reportTaskExecutor (doc 03 §4)

reportes:
  zona-horaria: ${TZ_SCHEDULES:America/Lima}
  directorio-temporal: ${EXCEL_OUTPUT_PATH:./xlsx_output}
  correo-remitente: ${EMAIL_USER:usuario@confianza.pe}
  correo-soporte: ${MAIL_SOPORTE:michael.palacios@confianza.pe}   # fallos (RN-03)
  firma-nombre: ${EMAIL_FIRMA_NOMBRE:Equipo de Reportes}
  firma-cargo: ${EMAIL_FIRMA_CARGO:}
  google-chat-webhook-url: ${GOOGLE_CHAT_WEBHOOK_URL:}   # notifica la validación diaria (opcional)
  adjunto-max-mb: 20               # sobre este tamaño el adjunto se comprime a .zip (MA-06)
  executor:
    core-size: 8
    max-size: 8
    queue-capacity: 50
    thread-name-prefix: report-exec-
  # Un bloque por reporte; la clave es el código kebab-case que devuelve
  # ReporteService.codigo(). destinatarios/cc son listas separadas por comas
  # (mismas variables *_PARA/*_CC del .env de Node). `corte` define la fecha:
  # DIA_ANTERIOR (diarios) o FIN_MES_ANTERIOR (mensuales).
  definiciones:
    validacion-cubo:
      cron: "0 0 7 * * *"             # ← copiar EXACTO del cron de Node.js (formato Spring: 6 campos)
      asunto: "Validación Cubo Diaria - %s"
      destinatarios: ${VALIDACION_CUBO_PARA:michael.palacios@confianza.pe}
      cc: ${VALIDACION_CUBO_CC:}
      corte: DIA_ANTERIOR
    cartera-heredada:
      cron: "0 30 6 1 * *"
      asunto: "Cartera Heredada PDM - Stock %s"
      destinatarios: ${CARTERA_HEREDADA_PARA:abigail.jaimes@confianza.pe,karla.campos@confianza.pe,ricardo.lazo@confianza.pe,alvaro.calderon@confianza.pe}
      cc: ${CARTERA_HEREDADA_CC:michael.palacios@confianza.pe}
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

El despliegue usa **mecanismos propios de Spring Boot** (sin Docker): el
`spring-boot-maven-plugin` produce un **jar ejecutable autocontenido** que se
corre directo con la JVM y se configura por variables de entorno / perfiles.

```bash
./mvnw clean package                          # compila + tests → target/task-reportes-back-1.0.0.jar
java -jar target/task-reportes-back-1.0.0.jar # producción: sin perfiles dev/local
```

| Aspecto | Decisión |
|---|---|
| Artefacto | Jar ejecutable de Spring Boot (`./mvnw clean package`), corrido como servicio del sistema (systemd o equivalente) |
| Instancias | **1 instancia** (los crons registrados no son cluster-aware; 2 instancias = correos duplicados) |
| Zona horaria | `America/Lima` en el host + zona explícita en cada `CronTrigger` (SC-03) |
| Configuración | Variables de entorno (`DB_*`, `SMTP_*`, `MAIL_*`) sobre los placeholders de `application.yml`; perfiles `dev`/`local` solo para desarrollo |
| Recursos | Memoria dimensionada para POI streaming (SXSSF mantiene ~100 filas en RAM) |
| Health-check | `GET /actuator/health` |

> El **despliegue local** (catálogo de variables, dónde se cambian, perfiles
> `dev`/`local` y comandos de arranque) está detallado en el
> [doc 06 — Despliegue Local](./06_DESPLIEGUE_LOCAL.md).