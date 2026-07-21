# task-reportes-back

Orquestador de reportes financieros programados (migración Node.js → **Spring Boot 3 + Java 21**).
La especificación completa vive en [`.docs/`](.docs/README.md) (PRD, TRD, paralelismo, Excel/correo).

## Qué hace

1. Dispara reportes por cron (`reportes.definiciones.*.cron`, zona `America/Lima`).
2. Ejecuta las queries SQL de cada reporte **en paralelo** (`@Async("reportTaskExecutor")` + `CompletableFuture.allOf`, timeout 15 min).
3. Exporta los resultados a `.xlsx` (Apache POI SXSSF, streaming).
4. Envía el Excel por correo (reintentos 2s/4s/8s, zip si supera 20 MB) y elimina el temporal tras el envío exitoso.

## Reportes implementados

| Código | Frecuencia | Hojas | Fuente de referencia (.docs) |
|---|---|---|---|
| `validacion-cubo` | Diaria | Indicadores, Validaciones | VALIDACIONES DIARIAS/VALIDACION_CUBO_.xlsx |
| `cartera-heredada` | Mensual | PDM Heredado | MENSUALES/Cartera Heredada |
| `desembolso-canal` | Mensual | Desembolsos Canal | MENSUALES/Desembolso Canal |
| `fondeo-estable` | Mensual | Fondeo Estable | MENSUALES/Fondeo Estable |
| `saldo-medio-vigente` | Mensual | Saldo Medio Vigente, Saldo Vigente Diario | MENSUALES/Reporte saldo medio vigente - Diana |
| `saca-tu-garra` | Mensual | BASE | MENSUALES/Reporte Giancarlo |
| `datos-cierre` | Mensual | Ratio CE, Clientes Rurales Migrantes | MENSUALES/Ratio CE, Clientes Nuevos y Migrantes |
| `reporte-seguros` | Mensual | Penetracion Seguros (47 columnas) | MENSUALES/.../Reporte seguros |
| `saldo-puntual-medio` | Mensual | Saldo Puntual, Saldo Medio | MENSUALES/.../Reporte saldo puntual - saldo medio |
| `cartera-vigente-agro` | Mensual | Saldo Vigente, Mes Anterior, Clientes | MENSUALES/.../Reporte saldo vigente - producto agro |

## Validaciones implementadas

- **Validación diaria del cubo** (`validacion-cubo`): datos presentes para el corte, indicadores no nulos/no negativos y variación diaria dentro del umbral (30%); genera hoja de resultados OK/ALERTA/ADVERTENCIA.
- **Jerarquía comercial**: sectorista/grupo/corredor/territorio nulos se reportan como `SIN ASIGNAR` (regla del área en `reporte-seguros` y `desembolso-canal`).
- **Hojas vacías**: se genera la hoja con cabeceras + leyenda "Sin registros para el período" y el correo lo advierte (RN-04).
- **Fecha de corte** del endpoint manual: no puede ser futura (400).
- **Configuración tipada y validada** al arranque (`@ConfigurationProperties` + `@Validated`); cada reporte debe tener cron, asunto y destinatarios.

## Operación

Despliegue con mecanismos propios de Spring Boot (sin Docker); la guía completa de
variables, perfiles y comandos está en [`.docs/06_DESPLIEGUE_LOCAL.md`](.docs/06_DESPLIEGUE_LOCAL.md).

```bash
# Desarrollo: perfil dev + configuración personal en application-local.yml (gitignored)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,local

# O con variables de entorno (mismos nombres que el .env del proyecto Node.js:
# DB_SERVER/DB_DOMAIN/DB_INSTANCE estilo SSMS, EMAIL_* de Google Workspace, *_PARA/*_CC)
DB_SERVER=SERVIDOR-BD DB_DATABASE=storage DB_DOMAIN=DOMINIO DB_USERNAME=usuario DB_PASSWORD=... \
EMAIL_USER=usuario@confianza.pe EMAIL_PASSWORD=... SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# Empaquetado ejecutable (producción)
./mvnw clean package && java -jar target/task-reportes-back-1.0.0.jar

# Listar reportes
GET /api/v1/reportes

# Re-ejecución manual (202 Accepted, corre en el pool dedicado)
POST /api/v1/reportes/{codigo}/ejecutar?corte=2026-06-30

# Salud y métricas del pool
GET /actuator/health · GET /actuator/metrics · GET /actuator/scheduledtasks
```

## Agregar un reporte nuevo (AR-07)

1. Crear subpaquete en `service/<reporte>/` con `*Queries` (SQL en text blocks, parámetros nombrados), records `*Fila` y el `*Service` que implementa `ReporteService`.
2. Crear su repositorio `repository/*Repository` con métodos `@Async("reportTaskExecutor")` que retornan `CompletableFuture`.
3. Agregar el bloque en `reportes.definiciones.<codigo>` de `application.yml`.

Sin cambios en scheduler, Excel ni correo.

## Pendientes de convivencia

- Copiar los **crons exactos** del Node.js actual (los horarios en `application.yml` son provisionales — RN-02).
- Los destinatarios por defecto salen del `.env` de Node.js (variables `*_PARA`/`*_CC`); confirmar los de `datos-cierre`, `reporte-seguros`, `saldo-puntual-medio` y `cartera-vigente-agro`, que en el template original tenían placeholders.
- `validacion-cubo`: confirmar la vista/tabla real del cubo (el `.docs` solo trae el Excel de indicadores; ver TODO en `ValidacionCuboQueries`).
