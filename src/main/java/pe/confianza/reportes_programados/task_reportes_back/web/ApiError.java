package pe.confianza.reportes_programados.task_reportes_back.web;

import java.time.OffsetDateTime;

public record ApiError(int status, String message, OffsetDateTime timestamp) {

    public static ApiError de(int status, String message) {
        return new ApiError(status, message, OffsetDateTime.now());
    }
}
