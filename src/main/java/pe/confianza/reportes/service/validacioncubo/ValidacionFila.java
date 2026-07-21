package pe.confianza.reportes.service.validacioncubo;

/** Resultado de una regla de validación sobre el cubo. */
public record ValidacionFila(
        String indicador,
        String regla,
        String resultado,
        String detalle
) {

    public static final String OK = "OK";
    public static final String ALERTA = "ALERTA";
    public static final String ADVERTENCIA = "ADVERTENCIA";
}
