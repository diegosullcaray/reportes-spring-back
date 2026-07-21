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
| [02](./02_TRD_ARQUITECTURA.md) | **TRD — Arquitectura y Stack** ✅ actualizado a lo construido | Stack Java/Spring Boot, `pom.xml`, estructura de paquetes real, convenciones y configuración |
| [03](./03_PARALELISMO_SCHEDULING.md) | **Paralelismo y Scheduling** | `ThreadPoolTaskExecutor`, `@Async`, `CompletableFuture.allOf()`, scheduler dinámico (equivalencia con `Promise.all` y cron de Node.js) |
| [04](./04_EXCEL_CORREO.md) | **Generación Excel y Correo** | Apache POI (SXSSF) para `.xlsx` y `JavaMailSender` con adjuntos (reemplazo de exceljs/nodemailer) |
| [05](./05_IMPLEMENTATION_PLAN.md) | **Implementation Plan** | Ruta crítica por fases con checkboxes, dependencias y criterios de salida |
| [06](./06_DESPLIEGUE_LOCAL.md) | **Despliegue Local** ✅ nuevo | Variables de entorno (dónde se cambian), perfiles `dev`/`local`, comandos de arranque con `mvnw`/jar y verificación — solo Spring Boot, sin Docker |

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

### Reportes Migrados (inventario)

| Código | Reporte | Frecuencia | Estado |
|---|---|---|---|
| `control-cargas` | Validación del estado de cargas (mod_rep.com.RSRPD001) → notifica a Google Chat | Cada 5 min | ✅ Migrado |
| `validacion-cubo` | Validación diaria del cubo comercial (indicadores + reglas) | Diaria | ✅ Migrado (falta confirmar vista real del cubo) |
| `cartera-heredada` | Stock PDM heredado (bases `dma`/`dwh`) | Mensual | ✅ Migrado |
| `desembolso-canal` | Desembolsos BT/CT por jerarquía comercial | Mensual | ✅ Migrado |
| `fondeo-estable` | Saldo de fondeo estable por matriz | Mensual | ✅ Migrado |
| `saldo-medio-vigente` | Saldo medio vigente + serie diaria (Diana) | Mensual | ✅ Migrado |
| `saca-tu-garra` | Base Saca tu Garra por asesor (Giancarlo) | Mensual | ✅ Migrado |
| `datos-cierre` | Ratio CE + clientes nuevos rurales/migrantes | Mensual | ✅ Migrado |
| `reporte-seguros` | Penetración de seguros, 47 columnas (Giovani) | Mensual | ✅ Migrado |
| `saldo-puntual-medio` | Saldo puntual y saldo medio de pasivos (Giovani) | Mensual | ✅ Migrado |
| `cartera-vigente-agro` | Saldo vigente agro + variación de clientes (Giovani) | Mensual | ✅ Migrado |

> Pendientes de convivencia: copiar los **crons exactos** de Node.js (RN-02) y
> confirmar los **destinatarios reales** por reporte (hoy variables `MAIL_*`).