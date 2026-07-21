# 04 — Generación de Excel (Apache POI) y Servicio de Correo
> **Proyecto:** Task Reportes — Orquestador de Reportes Financieros
> **Documentación Activa:** [README](./README.md) | [01_PRD_MIGRACION](./01_PRD_MIGRACION.md) | [02_TRD_ARQUITECTURA](./02_TRD_ARQUITECTURA.md) | [03_PARALELISMO_SCHEDULING](./03_PARALELISMO_SCHEDULING.md) | [04_EXCEL_CORREO](./04_EXCEL_CORREO.md) | [05_IMPLEMENTATION_PLAN](./05_IMPLEMENTATION_PLAN.md) | [06_DESPLIEGUE_LOCAL](./06_DESPLIEGUE_LOCAL.md)
> **Versión:** 1.0.0
> **Fecha:** 2026-07-20
> **Estado:** 🟢 Especificación lista para construir

---

## 1. Generación de Excel — Decisiones

| Aspecto | Decisión | Justificación |
|---|---|---|
| Librería | **Apache POI 5.2+** (`poi-ooxml`) | Estándar de facto en Java; reemplaza `exceljs`/`xlsx` |
| Modo | **`SXSSFWorkbook`** (streaming) | Los reportes financieros pueden superar cientos de miles de filas; SXSSF mantiene una ventana de ~100 filas en memoria y vuelca el resto a disco |
| Formato | `.xlsx` (OOXML) | Mismo formato que produce Node.js hoy |
| API interna | `ExcelGenerator` genérico + `ExcelSheetSpec` declarativo | Los services de reporte no tocan POI directamente (AR-04) |

### Reglas de generación

| # | Regla |
|---|---|
| XL-01 | Un dataset (resultado de una query) = **una hoja**; el nombre de la hoja se define en el `ExcelSheetSpec`. |
| XL-02 | Fila 1 = cabeceras con estilo (negrita, fondo, filtro automático activado); datos desde la fila 2. |
| XL-03 | Montos como celdas **numéricas** con formato contable (`#,##0.00`); fechas como celdas de fecha (`dd/mm/yyyy`) — nunca texto. |
| XL-04 | Los estilos (`CellStyle`) se crean **una sola vez por workbook** y se reutilizan (crear uno por celda revienta el límite de estilos de Excel). |
| XL-05 | Con SXSSF, `autoSizeColumn` requiere `trackAllColumnsForAutoSizing()`; para hojas muy grandes preferir **anchos fijos** por columna en el spec. |
| XL-06 | El archivo se escribe en `reportes.directorio-temporal` con nombre `<codigo>_<corte>.xlsx` y se **elimina tras el envío** exitoso (RN-08). Llamar siempre `workbook.dispose()` para limpiar los temporales de SXSSF. |
| XL-07 | Si un dataset viene vacío, la hoja se genera con cabeceras + leyenda "Sin registros para el período"; el correo lo advierte (RN-04). |

### 1.1 `ExcelSheetSpec` — especificación declarativa

```java
/** Describe una hoja: nombre, columnas (título + extractor + formato) y filas. */
public record ExcelSheetSpec<T>(
    String nombreHoja,
    List<ColumnaSpec<T>> columnas,
    List<T> filas
) {
    public record ColumnaSpec<T>(
        String titulo,
        Function<T, Object> extractor,   // valor de la celda desde el record de fila
        FormatoCelda formato,            // TEXTO | ENTERO | MONTO | FECHA | PORCENTAJE
        int anchoCaracteres
    ) {}
}
```

### 1.2 `ExcelGenerator` (esqueleto)

```java
@Component
public class ExcelGenerator {

    private final ReportesProperties properties;

    public Path generar(String nombreArchivo, List<ExcelSheetSpec<?>> hojas) {
        Path destino = properties.directorioTemporal().resolve(nombreArchivo + ".xlsx");
        // Ventana de 100 filas en memoria; el resto se vuelca a disco comprimido
        try (var workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            var estilos = EstilosReporte.crear(workbook);   // XL-04: estilos una sola vez

            for (var spec : hojas) {
                escribirHoja(workbook, spec, estilos);
            }

            Files.createDirectories(destino.getParent());
            try (var out = Files.newOutputStream(destino)) {
                workbook.write(out);
            }
            workbook.dispose();                             // XL-06: limpia temporales SXSSF
            return destino;
        } catch (IOException e) {
            throw new ReporteException("No se pudo generar el Excel " + nombreArchivo, e);
        }
    }

    private <T> void escribirHoja(SXSSFWorkbook wb, ExcelSheetSpec<T> spec, EstilosReporte estilos) {
        var hoja = wb.createSheet(spec.nombreHoja());

        // Cabeceras (XL-02)
        var filaCabecera = hoja.createRow(0);
        for (int c = 0; c < spec.columnas().size(); c++) {
            var col = spec.columnas().get(c);
            var celda = filaCabecera.createCell(c);
            celda.setCellValue(col.titulo());
            celda.setCellStyle(estilos.cabecera());
            hoja.setColumnWidth(c, col.anchoCaracteres() * 256);   // XL-05: anchos fijos
        }
        hoja.setAutoFilter(new CellRangeAddress(0, 0, 0, spec.columnas().size() - 1));

        // Datos (XL-03: tipos nativos por formato)
        int r = 1;
        for (T fila : spec.filas()) {
            var row = hoja.createRow(r++);
            for (int c = 0; c < spec.columnas().size(); c++) {
                var col = spec.columnas().get(c);
                estilos.escribir(row.createCell(c), col.extractor().apply(fila), col.formato());
            }
        }

        if (spec.filas().isEmpty()) {                              // XL-07
            hoja.createRow(1).createCell(0).setCellValue("Sin registros para el período");
        }
    }
}
```

### 1.3 Uso desde un service de reporte

```java
ExcelSheetSpec.de("Cartera por Agencia", cartera)   // fábrica con las columnas del record
```

Cada reporte define sus columnas junto a sus queries (mismo subpaquete), por ejemplo
`CarteraHeredadaColumnas.java`, manteniendo `ExcelGenerator` 100% genérico (AR-04).

---

## 2. Servicio de Correo — `JavaMailSender`

Reemplazo directo de `nodemailer`. La configuración SMTP vive en `application.yml`
(§4 del [doc 02](./02_TRD_ARQUITECTURA.md)) y varía por perfil (`dev` apunta a un SMTP local de pruebas).

### Reglas de correo

| # | Regla |
|---|---|
| MA-01 | Destinatarios, asunto y (opcional) copia por reporte salen de `reportes.definiciones.*` — nunca del código (RN-05). |
| MA-02 | Asunto normado: `"<Nombre del Reporte> - <fecha de corte>"` (RN-04). |
| MA-03 | Cuerpo HTML simple: nombre del reporte, período, totales por hoja y advertencia si alguna hoja está vacía. |
| MA-04 | Envío con **reintentos**: 3 intentos con backoff (2s, 4s, 8s) ante fallos SMTP transitorios; al agotarse, el reporte se marca fallido y se conserva el archivo para reenvío manual. |
| MA-05 | El envío es parte de la corrida del reporte (mismo `ejecucionId` en logs), pero el `EmailService` es genérico y no conoce el negocio (AR-04). |
| MA-06 | Tamaño del adjunto vigilado: si supera el límite del relay (configurable, default 20 MB), se comprime a `.zip` antes de adjuntar. |
| MA-07 | Firma al pie de **todo** correo (adjunto o de fallo): nombre y cargo configurables (`reportes.firma-nombre` / `firma-cargo`); si hay logo configurado (`reportes.firma-logo-path`), se embebe como imagen **inline** (`cid:`), nunca como URL externa — así se ve en Outlook/Gmail aunque el cliente bloquee imágenes remotas. |

### 2.0 Firma corporativa e imagen del correo

Réplica exacta de la firma del proyecto Node.js original (plantilla
`email-template.js`; el logo real quedó empaquetado en
`src/main/resources/static/logo-confianza.png`). **Todo** correo saliente la
lleva al pie: el del reporte
(`ReporteSupport`) y el de aviso de fallo a soporte (`ReporteScheduler`),
armada una sola vez por `FirmaHtmlBuilder` (`mail/FirmaHtmlBuilder.java`) para
no duplicar el HTML en dos lugares.

```
┌─────────┬──┬─────────────────────────────┐
│         │▎ │ Nombre Apellido    (azul)   │
│  [LOGO] │▎ │ Cargo              (negrita)│
│  150px  │▎ │ Dirección          (gris)   │
│         │▎ │ www.confianza.pe   (azul)   │
└─────────┴──┴─────────────────────────────┘
                ↑ borde izquierdo #0072CE
```

| Propiedad | Variable de entorno | Default | Descripción |
|---|---|---|---|
| `reportes.firma-nombre` | `EMAIL_FIRMA_NOMBRE` | `Equipo de Reportes` | Nombre, en azul corporativo (`#0072CE`) |
| `reportes.firma-cargo` | `EMAIL_FIRMA_CARGO` | *(vacío)* | Cargo, en negrita debajo del nombre |
| `reportes.firma-direccion` | `EMAIL_FIRMA_DIRECCION` | `Las Begonias 441 oficina 338C, San Isidro, Lima` | Dirección, en gris |
| `reportes.firma-web` | `EMAIL_FIRMA_WEB` | `www.confianza.pe` | Enlace al pie, en azul negrita (se antepone `https://`) |
| `reportes.firma-logo-path` | `EMAIL_FIRMA_LOGO_PATH` | `classpath:static/logo-confianza.png` | Logo, embebido inline (ver abajo) |

**El logo ya viene incluido**: el archivo real de Financiera Confianza está en
`src/main/resources/static/logo-confianza.png` (copiado del `.docs/FIRMA/`
original) y queda empaquetado dentro del jar — funciona out-of-the-box sin
configurar nada. `firma-logo-path` admite tres formas:

| Prefijo | Resuelve a | Cuándo usarlo |
|---|---|---|
| `classpath:ruta` | Recurso empaquetado en el jar | Default; el logo corporativo estándar |
| `file:ruta` | Archivo en disco, ruta explícita | Un logo distinto sin recompilar |
| *(sin prefijo)* | Archivo en disco, ruta tal cual | Igual que `file:`, por compatibilidad |

Si la ruta configurada no resuelve a un recurso existente, `EmailService`
**no falla el envío**: loguea un `WARN` (`reportes.firma-logo-path='...' no
existe; el correo se envía sin logo`) y manda el correo solo con el bloque de
texto. Esto es intencional para que un logo mal configurado nunca tumbe un
reporte (RN-03).

Mecanismo interno: `MimeMessageHelper.addInline(EmailService.LOGO_CONTENT_ID, ...)`
adjunta la imagen con `Content-ID: <firma-logo>`, y el HTML del correo la
referencia con `<img src="cid:firma-logo">` — el estándar para que los
clientes de correo la muestren sin depender de descargar nada externo (a
diferencia de una `<img src="https://...">`, que Outlook/Gmail suelen
bloquear por defecto).

### 2.1 `EmailService` (esqueleto)

```java
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final ReportesProperties properties;

    public void enviarConAdjunto(List<String> destinatarios, String asunto, Path adjunto) {
        enviarConReintentos(() -> {
            var mensaje = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.correoRemitente());
            helper.setTo(destinatarios.toArray(String[]::new));
            helper.setSubject(asunto);
            helper.setText(cuerpoHtml(asunto, adjunto), true);
            helper.addAttachment(adjunto.getFileName().toString(),
                                 new FileSystemResource(adjunto));
            mailSender.send(mensaje);
        });
        eliminarArchivo(adjunto);   // RN-08: solo tras envío exitoso
        log.info("Correo '{}' enviado a {} destinatario(s)", asunto, destinatarios.size());
    }

    /** MA-04: 3 intentos con backoff exponencial 2s/4s/8s. */
    private void enviarConReintentos(Envio envio) { /* ... */ }
}
```

> Alternativa a los reintentos manuales: `spring-retry` con `@Retryable(maxAttempts = 3,
> backoff = @Backoff(delay = 2000, multiplier = 2))` sobre el método de envío.

### 2.2 Perfil `dev` — SMTP local

```yaml
# application-dev.yml
spring:
  mail:
    host: localhost
    port: 1025          # SMTP local de pruebas
reportes:
  definiciones:
    cartera-heredada:
      destinatarios: [dev@localhost]
```

Con un SMTP local de pruebas se inspeccionan los correos y adjuntos sin
tocar el relay corporativo.

---

## 3. Flujo Completo de un Reporte

```
@Scheduled (cron, hilo report-sched-N)
   │
   ├─ MDC.put(ejecucionId)                       ← RN-07
   ▼
ReporteService.generar(corte)
   │
   ├─ repo.queryA(corte) ─┐  (hilos report-exec-*)
   ├─ repo.queryB(corte) ─┼─ EN PARALELO          ← RN-01 / CA-02
   ├─ repo.queryC(corte) ─┘
   │
   ├─ CompletableFuture.allOf(...).orTimeout(15 min).join()
   ▼
ExcelGenerator.generar(nombre, [hojas])           ← SXSSF, 1 hoja por dataset
   ▼
EmailService.enviarConAdjunto(destinatarios, asunto, xlsx)   ← reintentos MA-04
   ▼
eliminar archivo temporal                          ← RN-08
   ▼
log resultado (filas, duración) / notificar fallo  ← RN-03
```