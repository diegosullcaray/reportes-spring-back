# 06 — Plan de Despliegue Local (Spring Boot puro)
> **Proyecto:** Task Reportes — Orquestador de Reportes Financieros
> **Documentación Activa:** [README](./README.md) | [01_PRD_MIGRACION](./01_PRD_MIGRACION.md) | [02_TRD_ARQUITECTURA](./02_TRD_ARQUITECTURA.md) | [03_PARALELISMO_SCHEDULING](./03_PARALELISMO_SCHEDULING.md) | [04_EXCEL_CORREO](./04_EXCEL_CORREO.md) | [05_IMPLEMENTATION_PLAN](./05_IMPLEMENTATION_PLAN.md) | [06_DESPLIEGUE_LOCAL](./06_DESPLIEGUE_LOCAL.md)
> **Versión:** 3.0.0
> **Fecha:** 2026-07-21

El despliegue local usa **únicamente mecanismos propios de Spring Boot**: el
Maven wrapper (`./mvnw`), perfiles (`application-dev.yml` /
`application-local.yml`) y placeholders `${VAR:default}`. No se requiere Docker.

Las variables usan **los mismos nombres que el `.env` del proyecto Node.js**,
así la configuración existente se traslada sin renombrar nada.

---

## 1. Prerrequisitos

| Herramienta | Versión | Verificación |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | no se instala — el repo trae el wrapper | `./mvnw -version` |
| Acceso de red a SQL Server | mismos datos que la ventana "Connect to Server" de SSMS | conectividad al 1433 (o SQL Browser si es instancia nombrada) |
| Cuenta de Google Workspace | contraseña de aplicación generada | — |

> **Sin base de datos accesible** la aplicación **igual arranca** (Hikari abre
> conexiones de forma perezosa): se pueden probar el listado de reportes, el
> Actuator y la validación de la configuración. Las corridas de reportes fallarán
> al ejecutar la query y notificarán a soporte — comportamiento esperado (RN-03).

---

## 2. Variables — dónde se cambian

**Orden de precedencia** (gana el de más arriba — mecanismo estándar de Spring Boot):

1. Argumentos de línea de comandos (`--db.server=...`).
2. Variables de entorno exportadas en la shell.
3. Perfil activo: `application-<perfil>.yml` pisa los valores base.
4. Default del placeholder en `application.yml` → `${VAR:default}`.

### 2.1 SQL Server (igual que "Connect to Server" de SSMS)

| Variable | Default | Descripción |
|---|---|---|
| `DB_SERVER` | `localhost` | Nombre o IP del servidor |
| `DB_DATABASE` | `storage` | Base de datos inicial |
| `DB_DOMAIN` | *(vacío)* | **Windows Authentication (NTLM):** el dominio de `DOMINIO\usuario`. Vacío/sin definir → SQL Server Authentication |
| `DB_USERNAME` | `sa` | Usuario **sin** el prefijo de dominio |
| `DB_PASSWORD` | *(vacío)* | Contraseña |
| `DB_INSTANCE` | *(vacío)* | Instancia nombrada (ej. `SQLEXPRESS` de `SERVIDOR\SQLEXPRESS`); si se define se ignora el puerto |
| `DB_PORT` | `1433` | Puerto cuando no hay instancia nombrada |
| `DB_ENCRYPTION` | `false` | `encrypt` de la cadena JDBC |
| `DB_TRUST_CERTIFICATE` | `true` | `trustServerCertificate` |

La cadena JDBC se **arma sola** desde estas partes (`DbProperties.jdbcUrl()`):
con `DB_DOMAIN` agrega `authenticationScheme=NTLM;domain=...`; con `DB_INSTANCE`
usa `instanceName=...` (requiere el servicio SQL Browser activo en el servidor).

### 2.2 Correo (Google Workspace)

| Variable | Default | Descripción |
|---|---|---|
| `EMAIL_USER` | `usuario@confianza.pe` | Cuenta que envía; también es el remitente |
| `EMAIL_PASSWORD` | *(vacío)* | **Contraseña de aplicación** de Google: pegar los 16 caracteres **JUNTOS**, sin espacios ni guiones (Google la muestra como `abcd efgh ijkl mnop`) |
| `EMAIL_HOST` | `smtp.gmail.com` | Servidor SMTP |
| `EMAIL_PORT` | `465` | Si el firewall bloquea el 465, usar `587` (ver abajo) |
| `EMAIL_SSL` | `true` | SSL implícito (puerto 465) |
| `EMAIL_STARTTLS` | `false` | Para puerto 587: `EMAIL_SSL=false` y `EMAIL_STARTTLS=true` |
| `EMAIL_FIRMA_NOMBRE` | `Equipo de Reportes` | Firma de los correos |
| `EMAIL_FIRMA_CARGO` | *(vacío)* | Cargo en la firma |
| `MAIL_SOPORTE` | `diego.sullcaray@confianza.pe` | Recibe las notificaciones de fallo (RN-03) |

### 2.3 Destinatarios por reporte (listas separadas por comas)

Mismos nombres `*_PARA` / `*_CC` que el `.env` de Node.js; si no se definen,
aplican los destinatarios por defecto de `application.yml`:

| Reporte (código Spring) | Variables |
|---|---|
| `cartera-heredada` | `CARTERA_HEREDADA_PARA` / `CARTERA_HEREDADA_CC` |
| `desembolso-canal` | `DESEMBOLSO_CANAL_PARA` / `DESEMBOLSO_CANAL_CC` |
| `fondeo-estable` | `FONDEO_ESTABLE_PARA` / `FONDEO_ESTABLE_CC` |
| `datos-cierre` | `RATIO_CE_PARA` / `RATIO_CE_CC` |
| `saca-tu-garra` | `REPORTE_GIANCARLO_PARA` / `REPORTE_GIANCARLO_CC` |
| `saldo-medio-vigente` | `SALDO_MEDIO_VIGENTE_PARA` / `SALDO_MEDIO_VIGENTE_CC` |
| `saldo-puntual-medio` | `SALDO_PUNTUAL_MEDIO_PARA` / `SALDO_PUNTUAL_MEDIO_CC` |
| `cartera-vigente-agro` | `SALDO_VIGENTE_AGRO_PARA` / `SALDO_VIGENTE_AGRO_CC` |
| `reporte-seguros` | `REPORTE_SEGUROS_PARA` / `REPORTE_SEGUROS_CC` |
| `validacion-cubo` | `VALIDACION_CUBO_PARA` / `VALIDACION_CUBO_CC` |

### 2.4 Aplicación, rutas y notificaciones

| Variable | Default | Descripción |
|---|---|---|
| `TZ_SCHEDULES` | `America/Lima` | Zona horaria de los cron |
| `EXCEL_OUTPUT_PATH` | `./xlsx_output` | Carpeta de los `.xlsx` (se eliminan tras el envío exitoso, RN-08) |
| `GOOGLE_CHAT_WEBHOOK_URL` | *(vacío)* | Webhook entrante de un espacio de Google Chat: espacio → "Apps e integraciones" → "Webhooks" → crear → copiar URL. La validación diaria del cubo notifica ahí su resultado; si se omite, solo loguea |
| `SPRING_PROFILES_ACTIVE` | *(ninguno)* | `dev` → todos los correos a `dev@localhost`; `local` → tu configuración personal (§2.6) |

### 2.5 Dónde editar cada cosa

| Quiero cambiar… | Edito |
|---|---|
| Credenciales de BD/Gmail **solo en mi máquina** | `application-local.yml` (§2.6, ignorado por git) o `export` en la shell |
| Defaults del proyecto (incl. destinatarios por defecto) | `src/main/resources/application.yml` |
| Comportamiento en desarrollo (correos a `dev@localhost`) | `src/main/resources/application-dev.yml` |
| Cron / asunto / estrategia de corte de un reporte | Bloque `reportes.definiciones.<codigo>` en `application.yml` |
| Pool de hilos / cola | `reportes.executor.*` (mantener `db.pool-size ≥ max-size`) |

### 2.6 Perfil `local` — credenciales personales sin exportar nada

Crear `src/main/resources/application-local.yml` (ya está en `.gitignore`,
**nunca se commitea**):

```yaml
# application-local.yml — configuración personal de desarrollo
db:
  server: SERVIDOR-BD          # como en SSMS
  database: storage
  domain: DOMINIO              # vacío o borrar la línea para SQL Server Authentication
  username: usuario            # sin DOMINIO\
  password: mi_password
  # instance: SQLEXPRESS       # solo si la instancia tiene nombre
  # port: 1433

spring:
  mail:
    username: mi.usuario@confianza.pe
    password: abcdefghijklmnop   # contraseña de aplicación, 16 caracteres juntos

reportes:
  correo-remitente: mi.usuario@confianza.pe
  firma-nombre: Nombre Apellido
  firma-cargo: Cargo
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
# con perfil dev + configuración personal (§2.6) — recomendado
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,local

# o con variables exportadas (mismos nombres que el .env de Node)
export DB_SERVER=SERVIDOR-BD DB_DATABASE=storage
export DB_DOMAIN=DOMINIO DB_USERNAME=usuario DB_PASSWORD='********'
export EMAIL_USER=mi.usuario@confianza.pe EMAIL_PASSWORD=abcdefghijklmnop
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# o pisando propiedades puntuales por argumentos (máxima precedencia)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--db.server=SERVIDOR-BD --db.username=usuario --db.password=secreto"
```

### Opción B — JAR ejecutable (lo más parecido a producción)

```bash
./mvnw clean package                 # compila + corre los 20 tests
java -jar target/task-reportes-back-1.0.0.jar --spring.profiles.active=dev,local
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

# 5) Revisar la bandeja del destinatario (perfil dev: dev@localhost en el SMTP local)
#    y, si GOOGLE_CHAT_WEBHOOK_URL está configurado, el espacio de Chat para
#    la validación del cubo.
```

En el arranque, el log muestra la cadena JDBC armada y el modo de autenticación:
`DataSource SQL Server: jdbc:sqlserver://... (auth=Windows/NTLM dominio DOMINIO)`.

**Qué mirar en los logs:** cada corrida lleva su `ejecucionId` y las queries del
reporte deben verse **casi simultáneas en hilos `report-exec-*` distintos**
(CA-02); la duración total ≈ la query más lenta (CA-03).

### Errores comunes

| Síntoma | Causa | Solución |
|---|---|---|
| `Login failed for user 'usuario'` | `DB_DOMAIN` definido pero credenciales de SQL auth (o viceversa) | Con Windows Authentication definir `DB_DOMAIN` y el usuario **sin** `DOMINIO\`; para SQL auth dejar `DB_DOMAIN` vacío |
| `The connection to the named instance ... failed` | `DB_INSTANCE` definido pero SQL Browser apagado o UDP 1434 bloqueado | Conectar por puerto: quitar `DB_INSTANCE` y definir `DB_PORT` |
| `Username and Password not accepted` (Gmail 535) | `EMAIL_PASSWORD` no es contraseña de aplicación o tiene espacios | Generar contraseña de aplicación y pegar los 16 caracteres juntos |
| Correo no sale y el log muestra timeout al 465 | Firewall bloquea el 465 | `EMAIL_PORT=587`, `EMAIL_SSL=false`, `EMAIL_STARTTLS=true` |
| Arranque falla con `No existe configuración 'reportes.definiciones.<codigo>'` | Se agregó un `ReporteService` sin su bloque YAML | Agregar el bloque en `application.yml` (AR-07) |
| Reporte queda "EN_PROCESO" mucho tiempo | Query lenta o BD inaccesible | Timeout de 15 min por reporte; ver logs por `ejecucionId` |
| El `.xlsx` no está en `./xlsx_output` tras un envío exitoso | Comportamiento esperado | RN-08: se elimina tras el envío; solo se conserva si el correo falló |
| No llega la notificación a Google Chat | `GOOGLE_CHAT_WEBHOOK_URL` no configurado | Crear el webhook en el espacio y exportar la variable (opcional; sin él solo se loguea) |

---

## 5. Recordatorios para pasar a producción

- Configurar `DB_*`, `EMAIL_*` y los `*_PARA`/`*_CC` reales (sin defaults).
- **No** activar los perfiles `dev` ni `local`.
- Ejecutar como servicio del sistema el jar de `./mvnw clean package`
  (`java -jar task-reportes-back-1.0.0.jar`), zona horaria del host en
  `America/Lima` (los crons además llevan zona explícita, SC-03).
- Copiar los crons exactos del Node.js en convivencia (RN-02).
- Una sola instancia del servicio (SC-05: los crons no coordinan entre réplicas).
