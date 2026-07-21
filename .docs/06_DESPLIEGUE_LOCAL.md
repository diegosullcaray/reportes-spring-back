# 06 — Plan de Despliegue Local (Spring Boot puro)
> **Proyecto:** Task Reportes — Orquestador de Reportes Financieros
> **Documentación Activa:** [README](./README.md) | [01_PRD_MIGRACION](./01_PRD_MIGRACION.md) | [02_TRD_ARQUITECTURA](./02_TRD_ARQUITECTURA.md) | [03_PARALELISMO_SCHEDULING](./03_PARALELISMO_SCHEDULING.md) | [04_EXCEL_CORREO](./04_EXCEL_CORREO.md) | [05_IMPLEMENTATION_PLAN](./05_IMPLEMENTATION_PLAN.md) | [06_DESPLIEGUE_LOCAL](./06_DESPLIEGUE_LOCAL.md)
> **Versión:** 2.0.0
> **Fecha:** 2026-07-21

El despliegue local usa **únicamente mecanismos propios de Spring Boot**: el
Maven wrapper (`./mvnw`), perfiles (`application-dev.yml` /
`application-local.yml`) y placeholders `${VAR:default}` resueltos por variables
de entorno. No se requiere Docker ni ninguna herramienta externa.

---

## 1. Prerrequisitos

| Herramienta | Versión | Verificación |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | no se instala — el repo trae el wrapper | `./mvnw -version` |
| Acceso de red a SQL Server | bases `storage`, `dma`, `dwh` (solo lectura) | conectividad al puerto 1433 |
| SMTP alcanzable | relay de pruebas o corporativo | — |

> **Sin base de datos accesible** la aplicación **igual arranca** (Hikari abre
> conexiones de forma perezosa): se pueden probar el listado de reportes, el
> Actuator y la validación de la configuración. Las corridas de reportes fallarán
> al ejecutar la query y notificarán a `MAIL_SOPORTE` — comportamiento esperado (RN-03).

---

## 2. Variables de Entorno — dónde se cambian

Toda la configuración sensible entra por **variables de entorno**; los defaults
viven en `src/main/resources/application.yml` (RN-05: nada hardcodeado en código).

**Orden de precedencia** (gana el de más arriba — mecanismo estándar de Spring Boot):

1. Argumentos de línea de comandos (`--spring.datasource.url=...`).
2. Variables de entorno exportadas en la shell.
3. Perfil activo: `application-<perfil>.yml` pisa los valores base.
4. Default del placeholder en `application.yml` → `${VAR:default}`.

### 2.1 Catálogo de variables

| Variable | Propiedad que alimenta | Default (sin variable) | Descripción |
|---|---|---|---|
| `DB_URL` | `spring.datasource.url` | `jdbc:sqlserver://localhost:1433;databaseName=storage;encrypt=true;trustServerCertificate=true` | Cadena JDBC de SQL Server |
| `DB_USER` | `spring.datasource.username` | `sa` | Usuario de solo lectura |
| `DB_PASSWORD` | `spring.datasource.password` | `changeit` | Contraseña |
| `SMTP_HOST` | `spring.mail.host` | `localhost` | Relay SMTP |
| `SMTP_PORT` | `spring.mail.port` | `1025` | Puerto SMTP (relay corporativo: 587) |
| `SMTP_USER` / `SMTP_PASSWORD` | `spring.mail.username/password` | vacíos | Credenciales del relay |
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
| `SPRING_PROFILES_ACTIVE` | perfil activo | *(ninguno)* | `dev` → correos a `dev@localhost`; `local` → tu configuración personal (§2.3) |

### 2.2 Dónde editar cada cosa

| Quiero cambiar… | Edito |
|---|---|
| Credenciales de BD/SMTP **solo en mi máquina** | `application-local.yml` (§2.3, ignorado por git) o `export` en la shell |
| Defaults del proyecto | `src/main/resources/application.yml` |
| Comportamiento en desarrollo (correos a `dev@localhost`) | `src/main/resources/application-dev.yml` |
| Cron / asunto / destinatarios / estrategia de corte de un reporte | Bloque `reportes.definiciones.<codigo>` en `application.yml` |
| Tamaño del pool de hilos o de la cola | `reportes.executor.*` en `application.yml` (mantener `hikari.maximum-pool-size ≥ max-size`) |

### 2.3 Perfil `local` — credenciales personales sin exportar nada

Mecanismo propio de Spring: crear `src/main/resources/application-local.yml`
(ya está en `.gitignore`, **nunca se commitea**) con tus valores:

```yaml
# application-local.yml — configuración personal de desarrollo
spring:
  datasource:
    url: jdbc:sqlserver://<host>:1433;databaseName=storage;encrypt=true;trustServerCertificate=true
    username: mi_usuario_lectura
    password: mi_password
  mail:
    host: smtp.pruebas.confianza.pe
    port: 587
    username: usuario_smtp
    password: password_smtp

reportes:
  correo-remitente: mi.correo@confianza.pe
  correo-soporte: [ mi.correo@confianza.pe ]
```

y arrancar combinándolo con `dev` (los destinatarios quedan en `dev@localhost`
y las credenciales salen de tu archivo):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,local
```

---

## 3. Comandos de Arranque

### Opción A — `mvnw spring-boot:run` (desarrollo)

```bash
# con perfil dev + configuración personal (§2.3)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,local

# o con variables exportadas en la shell
export DB_URL='jdbc:sqlserver://<host>:1433;databaseName=storage;encrypt=true;trustServerCertificate=true'
export DB_USER='usuario_lectura'
export DB_PASSWORD='********'
export SMTP_HOST='smtp.pruebas.confianza.pe' SMTP_PORT=587
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# o pisando propiedades puntuales por argumentos (máxima precedencia)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--spring.datasource.username=usuario --spring.datasource.password=secreto"
```

### Opción B — JAR ejecutable (lo más parecido a producción)

```bash
./mvnw clean package                 # compila + corre los 17 tests
java -jar target/task-reportes-back-1.0.0.jar --spring.profiles.active=dev,local

# o con variables de entorno
SPRING_PROFILES_ACTIVE=dev DB_USER=usuario DB_PASSWORD=secreto \
  java -jar target/task-reportes-back-1.0.0.jar
```

### Solo tests

```bash
./mvnw test
```

---

## 4. Verificación Post-Arranque

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

# 5) Revisar la bandeja del destinatario configurado (perfil dev: dev@localhost
#    en el SMTP apuntado por SMTP_HOST/SMTP_PORT)
```

**Qué mirar en los logs:** cada corrida lleva su `ejecucionId` (`[fondeo-estable-a1b2c3d4]`)
y las queries del reporte deben aparecer **casi simultáneas en hilos `report-exec-*`
distintos** (CA-02); la duración total ≈ la query más lenta (CA-03).

### Errores comunes

| Síntoma | Causa | Solución |
|---|---|---|
| Arranque falla con `No existe configuración 'reportes.definiciones.<codigo>'` | Se agregó un `ReporteService` sin su bloque YAML | Agregar el bloque en `application.yml` (AR-07) |
| Arranque falla con `expected ',' or ']'` en YAML | Placeholder `${...}` sin comillas dentro de lista `[ ]` | Escribir `[ "${VAR:default}" ]` |
| `Login failed for user` al ejecutar un reporte | Credenciales `DB_*` incorrectas | Revisar exports / `application-local.yml`; el resto de reportes no se ve afectado (RN-03) |
| `Couldn't connect to host, port: localhost, 1025` al enviar correo | No hay SMTP en el default local | Configurar `SMTP_HOST`/`SMTP_PORT` reales (el envío reintenta 3 veces y conserva el `.xlsx` para reenvío manual, MA-04) |
| Reporte queda "EN_PROCESO" mucho tiempo | Query lenta o BD inaccesible | Timeout de 15 min por reporte; ver logs por `ejecucionId` |
| El `.xlsx` no está en `/tmp/reportes` tras un envío exitoso | Comportamiento esperado | RN-08: se elimina tras el envío; solo se conserva si el correo falló |

---

## 5. Recordatorios para pasar a producción

- Configurar `DB_*`, `SMTP_*` y todos los `MAIL_*` con valores reales (sin defaults).
- **No** activar los perfiles `dev` ni `local`.
- Ejecutar como servicio del sistema el jar de `./mvnw clean package`
  (`java -jar task-reportes-back-1.0.0.jar`), zona horaria del host en
  `America/Lima` (los crons además llevan zona explícita, SC-03).
- Copiar los crons exactos del Node.js en convivencia (RN-02).
- Una sola instancia del servicio (SC-05: los crons no coordinan entre réplicas).
