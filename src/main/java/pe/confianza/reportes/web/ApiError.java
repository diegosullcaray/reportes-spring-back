package pe.confianza.reportes.web;

import java.time.OffsetDateTime;

public record ApiError(int status, String message, OffsetDateTime timestamp) {

    public static ApiError de(int status, String message) {
        return new ApiError(status, message, OffsetDateTime.now());
    }
}
