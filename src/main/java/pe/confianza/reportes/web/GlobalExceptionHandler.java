package pe.confianza.reportes.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pe.confianza.reportes.shared.ReporteNoEncontradoException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ReporteNoEncontradoException.class)
    public ResponseEntity<ApiError> reporteNoEncontrado(ReporteNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.de(HttpStatus.NOT_FOUND.value(), e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> parametroInvalido(Exception e) {
        return ResponseEntity.badRequest()
                .body(ApiError.de(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> errorInterno(Exception e) {
        log.error("Error no controlado en la API", e);
        return ResponseEntity.internalServerError()
                .body(ApiError.de(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error interno del servidor"));
    }
}
