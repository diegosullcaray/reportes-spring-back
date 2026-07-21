# Documentación — Migración `task-reportes-back` (Node.js → Spring Boot)

> **Proyecto:** Task Reportes — Orquestador de Reportes Financieros
> **Tipo:** Migración de backend Node.js (cron jobs + reportes Excel + correo) a **Spring Boot 3**
> **Versión:** 1.0.0
> **Fecha:** 2026-07-20

---

## Índice de Documentos

| Doc | Nombre | Contenido |
|---|---|---|
| [01](./01_PRD_MIGRACION.md) | **PRD — Product Requirements** | Problema, objetivo, alcance, reglas de negocio y criterios de aceptación de la migración |
| [02](./02_TRD_ARQUITECTURA.md) | **TRD — Arquitectura y Stack** | Stack Java/Spring Boot, `pom.xml`, estructura de paquetes, convenciones y configuración |
| [03](./03_PARALELISMO_SCHEDULING.md) | **Paralelismo y Scheduling** | `ThreadPoolTaskExecutor`, `@Async`, `CompletableFuture.allOf()`, `@Scheduled` (equivalencia con `Promise.all` y cron de Node.js) |
| [04](./04_EXCEL_CORREO.md) | **Generación Excel y Correo** | Apache POI (SXSSF) para `.xlsx` y `JavaMailSender` con adjuntos (reemplazo de exceljs/nodemailer) |
| [05](./05_IMPLEMENTATION_PLAN.md) | **Implementation Plan** | Ruta crítica por fases con checkboxes, dependencias y criterios de salida |

---

## Resumen Ejecutivo

`task-reportes-back` es un orquestador Node.js de tareas programadas que:

1. Se dispara por **cron jobs**.
2. Ejecuta **múltiples consultas SQL complejas en paralelo** (`Promise.all`).
3. Exporta los resultados a archivos **Excel (`.xlsx`)**.
4. Envía los archivos por **correo electrónico** a distintos destinatarios.

La migración lo reconstruye sobre **Spring Boot 3 + Java 21** manteniendo el
procesamiento **estrictamente en paralelo** (no secuencial) mediante un
`ThreadPoolTaskExecutor` personalizado, `@Async` y `CompletableFuture.allOf()`.

### Mapa de Equivalencias Node.js → Spring Boot

| Concepto en Node.js | Equivalente en Spring Boot |
|---|---|
| `node-cron` / `cron.schedule(...)` | `@Scheduled(cron = "...")` + `@EnableScheduling` |
| `Promise.all([...])` | `CompletableFuture.allOf(...)` sobre métodos `@Async` |
| Event loop + I/O asíncrono | `ThreadPoolTaskExecutor` dedicado (`reportTaskExecutor`) |
| `exceljs` / `xlsx` | **Apache POI** (`SXSSFWorkbook` streaming) |
| `nodemailer` | **Spring Boot Starter Mail** (`JavaMailSender` + `MimeMessageHelper`) |
| `pg` / `mysql2` con pool propio | Spring JDBC (`JdbcTemplate`) + HikariCP |
| `.env` + `process.env` | `application.yml` + perfiles (`dev` / `prod`) |
| `console.log` / `winston` | SLF4J + Logback (MDC con id de ejecución) |

### Reportes a Migrar (inventario inicial)

| Reporte | Descripción | Estado |
|---|---|---|
| **Cartera Heredada** | Consolidado de cartera heredada por agencia/canal | ⏳ Por migrar |
| **Desembolso Canal** | Desembolsos del día agrupados por canal de venta | ⏳ Por migrar |
| **Fondeo Estable** | Posición de fondeo estable (depósitos/captaciones) | ⏳ Por migrar |

> El inventario se completa en la FASE 0 del [plan de implementación](./05_IMPLEMENTATION_PLAN.md)
> relevando todos los cron jobs, queries y destinatarios del proyecto Node.js actual.