package pe.confianza.reportes.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.confianza.reportes.service.controlcargas.ControlCargasResultado;
import pe.confianza.reportes.service.controlcargas.ControlCargasService;
import pe.confianza.reportes.shared.CorrelacionUtils;

/** Consulta en vivo de las validaciones de carga (equivalente a /api/validaciones de Node.js). */
@RestController
@RequestMapping("/api/v1/validaciones")
@Tag(name = "Validaciones", description = "Validaciones de carga del warehouse")
public class ValidacionesController {

    private final ControlCargasService controlCargasService;

    public ValidacionesController(ControlCargasService controlCargasService) {
        this.controlCargasService = controlCargasService;
    }

    @Operation(summary = "Estado de las cargas del warehouse",
            description = "Ejecuta la validación en vivo (mod_rep.com.RSRPD001): estado de las carteras "
                    + "críticas (activas/pasivas), totales y detalle de procesos pendientes/finalizados. "
                    + "Cada consulta también notifica el resultado al espacio de Google Chat configurado.")
    @GetMapping("/control-cargas")
    public ResponseEntity<ControlCargasResultado> estadoCargas() {
        CorrelacionUtils.iniciarEjecucion("control-cargas");
        try {
            var resultado = controlCargasService.validarCargas();
            return resultado.success() ? ResponseEntity.ok(resultado)
                    : ResponseEntity.badRequest().body(resultado);
        } finally {
            CorrelacionUtils.finalizarEjecucion();
        }
    }
}
