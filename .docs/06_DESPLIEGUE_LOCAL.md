# 06 — Plan de Despliegue Local
> **Proyecto:** Task Reportes — Orquestador de Reportes Financieros
> **Documentación Activa:** [README](./README.md) | [01_PRD_MIGRACION](./01_PRD_MIGRACION.md) | [02_TRD_ARQUITECTURA](./02_TRD_ARQUITECTURA.md) | [03_PARALELISMO_SCHEDULING](./03_PARALELISMO_SCHEDULING.md) | [04_EXCEL_CORREO](./04_EXCEL_CORREO.md) | [05_IMPLEMENTATION_PLAN](./05_IMPLEMENTATION_PLAN.md) | [06_DESPLIEGUE_LOCAL](./06_DESPLIEGUE_LOCAL.md)
> **Versión:** 1.0.0
> **Fecha:** 2026-07-21

---

## 1. Prerrequisitos

| Herramienta | Versión | Verificación |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | no se instala — usar el wrapper `./mvnw` | `./mvnw -version` |
| Docker + Docker Compose | reciente (solo para Mailpit / despliegue en contenedor) | `docker compose version` |
| Acceso de red a SQL Server | bases `storage`, `dma`, `dwh` (solo lectura) | `telnet <host> 1433` |

> **Sin base de datos accesible** la aplicación **igual arranca** (Hikari abre
> conexiones de forma perezosa): se pueden probar el listado de reportes, el
> Actuator y la validación de la configuración. Las corridas de reportes fallarán
> al ejecutar la query y notificarán a `MAIL_SOPORTE` — comportamiento esperado.

---

## 2. Variables de Entorno — dónde se cambian

Toda la configuración sensible entra por **variables de entorno**; los defaults
viven en `src/main/resources/application.yml` (RN-05: nada hardcodeado en código).

**Orden de precedencia** (gana el de más arriba):

1. Variable de entorno exportada en la shell / `.env` de Docker Compose.
2. Default del placeholder en `application.yml` → `${VAR:default}`.
3. Overrides del perfil activo (`application-dev.yml` pisa los valores base cuando `SPRING_PROFILES_ACTIVE=dev`).

### 2.1 Catálogo de variables

| Variable | Propiedad que alimenta | Default (sin variable) | Descripción |
|---|---|---|---|
| `DB_URL` | `spring.datasource.url` | `jdbc:sqlserver://localhost:1433;databaseName=storage;encrypt=true;trustServerCertificate=true` | Cadena JDBC de SQL Server |
| `DB_USER` | `spring.datasource.username` | `sa` | Usuario de solo lectura |
| `DB_PASSWORD` | `spring.datasource.password` | `changeit` | Contraseña |
| `SMTP_HOST` | `spring.mail.host` | `localhost` | Relay SMTP (local: Mailpit) |
| `SMTP_PORT` | `spring.mail.port` | `1025` | Puerto SMTP (Mailpit: 1025; relay corporativo: 587) |
| `SMTP_USER` / `SMTP_PASSWORD` | `spring.mail.username/password` | vacíos | Credenciales del relay (Mailpit no las necesita) |
| `TMP_REPORTES` | `reportes.directorio-temporal` | `/tmp/reportes` | Carpeta de los `.xlsx` temporales (RN-08) |
| `MAIL_FROM` | `reportes.correo-remitente` | `reportes@confianza.pe` | Remitente de todos los correos |
| `MAIL_SOPORTE` | `reportes.correo-soporte` | `soporte-ti@confianza.pe` | Recibe las notificaciones de fallo (RN-03) |
| `MAIL_VALIDACIONES` | destinatarios `validacion-cubo` | `mis-datos@confianza.pe` | — |
| `MAIL_RIESGOS` | destinatarios `cartera-heredada` | `riesgos@confianza.pe` | — |
| `MAIL_CANALES` | destinatarios `desembolso-canal` | `canales@confianza.pe` | — |
| `MAIL_TESORERIA` | destinatarios `fondeo-estable` | `tesoreria@confianza.pe` | — |
| `MAIL_FINANZAS` | destinatarios `saldo-medio-vigente` | `finanzas@confianza.pe` | — |
| `MAIL_COMERCIAL` | destinatarios `saca-tu-garra`, `datos-cierre`, `cartera-vigente-agro` | `comercial@confianza.pe` | — |
| `MAIL_SEGUROS` | destinatarios `reporte-seguros` | `seguros@confianza.pe` | — |
| `MAIL_PASIVOS` | destinatarios `saldo-puntual-medio` | `pasivos@confianza.pe` | — |
| `SPRING_PROFILES_ACTIVE` | perfil activo | *(ninguno)* | `dev` activa `application-dev.yml` (todos los correos a `dev@localhost`) |
| `TZ` | zona del contenedor | `America/Lima` (en Docker) | Los crons además llevan zona explícita (SC-03) |

### 2.2 Dónde editar cada cosa

| Quiero cambiar… | Edito |
|---|---|
| Credenciales/host de BD o SMTP **solo en mi máquina** | `.env` (copiado de `.env.example`, ignorado por git) o `export` en la shell |
| Defaults del proyecto | `src/main/resources/application.yml` |
| Comportamiento en desarrollo (correos a Mailpit) | `src/main/resources/application-dev.yml` |
| Cron / asunto / destinatarios / estrategia de corte de un reporte | Bloque `reportes.definiciones.<codigo>` en `application.yml` |
| Tamaño del pool de hilos o de la cola | `reportes.executor.*` en `application.yml` (mantener `hikari.maximum-pool-size ≥ max-size`) |

```bash
# Preparar el archivo de variables local (una sola vez)
cp .env.example .env
# editar .env con credenciales reales de BD (nunca commitearlo)
```

---

## 3. Correo local — Mailpit

Para no tocar el relay corporativo, en local se usa **Mailpit** (SMTP falso con
interfaz web). El `docker-compose.yml` del repo ya lo incluye:

```bash
docker compose up -d mailpit
# SMTP  → localhost:1025   (default de application.yml)
# Web   → http://localhost:8025   (bandeja para inspeccionar correos y adjuntos)
```

---

## 4. Comandos de Arranque

### Opción A — Maven (desarrollo, con hot-restart de código)

```bash
# 1) Levantar Mailpit
docker compose up -d mailpit

# 2) Exportar credenciales de BD (o tenerlas en la shell desde .env)
export DB_URL='jdbc:sqlserver://<host>:1433;databaseName=storage;encrypt=true;trustServerCertificate=true'
export DB_USER='usuario_lectura'
export DB_PASSWORD='********'

# 3) Arrancar con perfil dev (correos a dev@localhost vía Mailpit)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Variante en una sola línea sin exports:

```bash
SPRING_PROFILES_ACTIVE=dev DB_URL='jdbc:sqlserver://<host>:1433;databaseName=storage;encrypt=true;trustServerCertificate=true' DB_USER=usuario DB_PASSWORD=secreto ./mvnw spring-boot:run
```

### Opción B — JAR empaquetado (lo más parecido a producción)

```bash
./mvnw clean package                 # compila + corre los 17 tests
SPRING_PROFILES_ACTIVE=dev java -jar target/task-reportes-back-1.0.0.jar
```

### Opción C — Docker Compose (app + Mailpit, imagen multi-stage)

```bash
cp .env.example .env                 # y editar credenciales
docker compose up --build           # construye la imagen y levanta app + mailpit
# la app queda en http://localhost:8080 con TZ=America/Lima
```

### Solo tests

```bash
./mvnw test
```

---

## 5. Verificación Post-Arranque

```bash
# 1) Salud
curl -s http://localhost:8080/actuator/health
# → {"status":"UP"}

# 2) Reportes registrados (código + cron + estrategia de corte)
curl -s http://localhost:8080/api/v1/reportes | jq

# 3) Crons programados por el scheduler dinámico
curl -s http://localhost:8080/actuator/scheduledtasks | jq

# 4) Disparo manual de un reporte (202 Accepted, corre en el pool report-exec-*)
curl -s -X POST 'http://localhost:8080/api/v1/reportes/fondeo-estable/ejecutar?corte=2026-06-30' | jq
# → {"codigo":"fondeo-estable","corte":"2026-06-30","estado":"EN_PROCESO"}

# 5) Ver el correo con el Excel adjunto
open http://localhost:8025          # bandeja de Mailpit
```

**Qué mirar en los logs:** cada corrida lleva su `ejecucionId` (`[fondeo-estable-a1b2c3d4]`)
y las queries del reporte deben aparecer **casi simultáneas en hilos `report-exec-*`
distintos** (CA-02); la duración total ≈ la query más lenta (CA-03).

### Errores comunes

| Síntoma | Causa | Solución |
|---|---|---|
| Arranque falla con `No existe configuración 'reportes.definiciones.<codigo>'` | Se agregó un `ReporteService` sin su bloque YAML | Agregar el bloque en `application.yml` (AR-07) |
| Arranque falla con `expected ',' or ']'` en YAML | Placeholder `${...}` sin comillas dentro de lista `[ ]` | Escribir `[ "${VAR:default}" ]` |
| `Login failed for user` al ejecutar un reporte | Credenciales `DB_*` incorrectas | Revisar `.env` / exports; el resto de reportes no se ve afectado (RN-03) |
| Correo no llega en local | Mailpit apagado | `docker compose up -d mailpit` y revisar `http://localhost:8025` |
| Reporte queda "EN_PROCESO" mucho tiempo | Query lenta o BD inaccesible | Timeout de 15 min por reporte; ver logs por `ejecucionId` |
| El `.xlsx` no está en `/tmp/reportes` tras un envío exitoso | Comportamiento esperado | RN-08: se elimina tras el envío; solo se conserva si el correo falló |

---

## 6. Recordatorios para pasar a producción

- Configurar `DB_*`, `SMTP_*` y todos los `MAIL_*` con valores reales (sin defaults).
- **No** activar el perfil `dev`.
- Copiar los crons exactos del Node.js en convivencia (RN-02).
- Una sola réplica del servicio (SC-05).
