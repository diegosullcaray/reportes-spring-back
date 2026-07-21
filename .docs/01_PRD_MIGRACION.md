    # 01 — PRD Migración `task-reportes-back` (Node.js → Spring Boot)
> **Proyecto:** Task Reportes — Orquestador de Reportes Financieros
> **Documentación Activa:** [README](./README.md) | [01_PRD_MIGRACION](./01_PRD_MIGRACION.md) | [02_TRD_ARQUITECTURA](./02_TRD_ARQUITECTURA.md) | [03_PARALELISMO_SCHEDULING](./03_PARALELISMO_SCHEDULING.md) | [04_EXCEL_CORREO](./04_EXCEL_CORREO.md) | [05_IMPLEMENTATION_PLAN](./05_IMPLEMENTATION_PLAN.md) | [06_DESPLIEGUE_LOCAL](./06_DESPLIEGUE_LOCAL.md)
> **Versión:** 1.0.0
> **Fecha:** 2026-07-20

---

## 1. Problema de Negocio

El backend `task-reportes-back` (Node.js) genera y distribuye los reportes financieros
regulatorios y operativos de la organización (ej. *Cartera Heredada*, *Desembolso Canal*,
*Fondeo Estable*). Aunque funciona, presenta limitaciones que motivan la migración:

- **Stack aislado:** el resto del ecosistema backend (MIS Host y sistemas hijos) está
  estandarizado en **Spring Boot 3 + Java 21**; mantener Node.js duplica conocimiento,
  pipelines y librerías.
- **Tipado y mantenibilidad:** las queries SQL complejas y las transformaciones a Excel
  carecen de tipado fuerte; los errores se descubren en runtime.
- **Observabilidad limitada:** no hay métricas ni health-checks estándar (Actuator) para
  monitorear las ejecuciones de cron.
- **Gestión de concurrencia implícita:** el paralelismo depende del event loop y de
  `Promise.all` sin control de tamaño de pool, timeouts ni backpressure.

---

## 2. Objetivo y Valor de Negocio

> **"Migrar el orquestador de reportes a Spring Boot conservando (o mejorando) los tiempos
> de generación gracias a un paralelismo controlado y configurable."**

| Pilar | Descripción |
|---|---|
| **Paridad funcional** | Cada cron job, query, Excel y correo de Node.js tiene su equivalente exacto en Spring Boot. |
| **Paralelismo real** | Las consultas y la generación de reportes se ejecutan en paralelo (`@Async` + `CompletableFuture.allOf()`), nunca de forma secuencial. |
| **Operabilidad** | Logs estructurados por ejecución, métricas del pool de hilos y health-checks vía Actuator. |
| **Estandarización** | Mismo stack y convenciones que el resto del ecosistema MIS; despliegue como jar ejecutable de Spring Boot. |

---

## 3. Alcance del MVP

### ✅ Dentro del Alcance

- **Scheduler central** con `@Scheduled` que replica 1:1 las expresiones cron de Node.js.
- **Ejecución paralela** de todas las consultas SQL de cada reporte mediante un
  `ThreadPoolTaskExecutor` dedicado (`reportTaskExecutor`).
- **Generación de Excel (`.xlsx`)** con Apache POI en modo streaming (SXSSF) para
  volúmenes grandes sin agotar memoria.
- **Envío de correos** con adjuntos Excel a listas de destinatarios configurables por
  reporte (`application.yml`).
- **Migración de los reportes existentes**: Cartera Heredada, Desembolso Canal,
  Fondeo Estable y los demás relevados en FASE 0.
- **Manejo de fallos parciales:** si una query falla, el reporte se marca como fallido y
  se notifica, sin tumbar el resto de reportes del ciclo.
- **Endpoint manual de disparo** (`POST /api/v1/reportes/{codigo}/ejecutar`) para
  re-ejecuciones bajo demanda (soporte/operaciones).

### ❌ Fuera del Alcance (MVP)

- Interfaz gráfica de administración de reportes (se opera por configuración y API).
- Reescritura u optimización de las queries SQL (se migran tal cual; tuning en fase 2).
- Persistencia histórica de los archivos generados (solo retención temporal local).
- Orquestadores externos (Quartz clusterizado, Airflow); `@Scheduled` es suficiente para
  una única instancia.

---

## 4. Usuarios / Consumidores

| Perfil | Descripción | Interacción |
|---|---|---|
| **Áreas de negocio (Finanzas, Riesgos, Canales)** | Destinatarios de los correos con los Excel. | Reciben los reportes en su bandeja según el cron de cada uno. |
| **Operaciones / Soporte TI** | Monitorean las ejecuciones y re-disparan reportes fallidos. | Logs, Actuator y endpoint manual de ejecución. |
| **Equipo de Desarrollo** | Mantiene y agrega nuevos reportes. | Agrega un nuevo `ReportService` + configuración, sin tocar el orquestador. |

---

## 5. Reglas de Negocio Críticas

> Estas reglas son **no negociables** y deben implementarse en la primera versión.

| # | Regla |
|---|---|
| RN-01 | El procesamiento de las consultas de un reporte es **estrictamente paralelo**: prohibido encadenar queries secuencialmente cuando no existe dependencia de datos entre ellas. |
| RN-02 | Los horarios de generación (cron) deben ser **idénticos** a los de Node.js durante la convivencia; cualquier cambio de horario es una decisión de negocio, no técnica. |
| RN-03 | Un fallo en un reporte **no interrumpe** los demás reportes del mismo ciclo ni el scheduler. |
| RN-04 | Todo correo enviado incluye el **período/fecha de corte** del reporte en asunto y cuerpo; nunca se envía un Excel vacío sin advertirlo explícitamente. |
| RN-05 | Los destinatarios se definen **por configuración** (`application.yml` por perfil), jamás hardcodeados en el código. |
| RN-06 | Las tareas `@Async` corren **únicamente** en el executor dedicado `reportTaskExecutor`; prohibido usar el `SimpleAsyncTaskExecutor` por defecto de Spring. |
| RN-07 | Cada ejecución de reporte genera un **id de correlación** presente en todos sus logs (MDC), para trazar una corrida de punta a punta. |
| RN-08 | Los archivos generados se escriben en un directorio temporal y se **eliminan tras el envío** exitoso del correo. |

---

## 6. Criterios de Aceptación

| ID | Criterio | Prioridad |
|---|---|---|
| CA-01 | Cada reporte migrado produce un `.xlsx` **equivalente en datos** al generado por Node.js para la misma fecha de corte. | 🔴 Crítico |
| CA-02 | Las consultas de un reporte se ejecutan **en paralelo** (verificable en logs: hilos `report-exec-*` concurrentes). | 🔴 Crítico |
| CA-03 | El tiempo total de un reporte ≈ el de su **query más lenta** (+ generación Excel), no la suma de todas. | 🔴 Crítico |
| CA-04 | Los cron jobs disparan en los mismos horarios que en Node.js (validado en convivencia). | 🔴 Crítico |
| CA-05 | Si una query falla, el reporte se marca fallido, se loguea con su id de correlación y se notifica; los demás reportes concluyen normalmente. | 🟠 Alto |
| CA-06 | Los correos llegan a los destinatarios configurados con el adjunto correcto y el asunto normado. | 🟠 Alto |
| CA-07 | El endpoint manual re-ejecuta un reporte específico y responde `202 Accepted` de inmediato (ejecución asíncrona). | 🟡 Medio |
| CA-08 | Actuator expone health y métricas del pool (`executor.active`, `executor.queued`). | 🟡 Medio |

---

## 7. Métricas de Éxito

| Métrica | Meta |
|---|---|
| Paridad de datos vs. Node.js (filas/columnas/valores por reporte) | 100% |
| Tiempo de generación por reporte vs. Node.js | ≤ 100% (igual o mejor) |
| Reportes entregados a tiempo en el primer mes de convivencia | ≥ 99% |
| Incidentes por correos no enviados | 0 |

---

## 8. Dependencias y Restricciones

- **Java 21 LTS + Spring Boot 3.3+** (mismo estándar que el backend del MIS Host).
- **Maven** como gestor de dependencias (`pom.xml`).
- Acceso de solo lectura a las **mismas bases de datos** que consulta hoy Node.js.
- **Servidor SMTP corporativo** (mismas credenciales/relay que usa nodemailer hoy).
- Despliegue como **jar ejecutable de Spring Boot** (`./mvnw clean package` → `java -jar`), corrido como servicio del sistema en una sola instancia.
- Período de **convivencia** (ambos sistemas generando en paralelo) para validar paridad
  antes del apagado de Node.js — ver FASE 5 del [plan](./05_IMPLEMENTATION_PLAN.md).