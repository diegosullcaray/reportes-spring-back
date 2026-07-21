package pe.confianza.reportes.service.controlcargas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.confianza.reportes.mail.GoogleChatNotifier;
import pe.confianza.reportes.repository.ControlCargasRepository;

import java.util.List;

/**
 * Validación de control de cargas (réplica del módulo "control-cargas" de
 * Node.js — ver .docs/VALIDACIONES DIARIAS/control-cargas/): consulta el estado
 * de los procesos de carga del warehouse, evalúa que las carteras activas y
 * pasivas hayan finalizado y notifica el resultado de CADA corrida al espacio
 * de Google Chat configurado en GOOGLE_CHAT_WEBHOOK_URL.
 */
@Service
public class ControlCargasService {

    private static final Logger log = LoggerFactory.getLogger(ControlCargasService.class);

    private final ControlCargasRepository repository;
    private final GoogleChatNotifier chatNotifier;

    public ControlCargasService(ControlCargasRepository repository, GoogleChatNotifier chatNotifier) {
        this.repository = repository;
        this.chatNotifier = chatNotifier;
    }

    public ControlCargasResultado validarCargas() {
        log.info("Consultando estado de cargas en BD (mod_rep.com.RSRPD001)");
        List<ProcesoCarga> filas = repository.consultarEstadoProcesos();

        var resultado = evaluar(filas);
        if (!resultado.success()) {
            log.warn("Control de cargas sin registros de procesos");
            chatNotifier.notificar("⚠️ *Control de Cargas*\n" + resultado.resumenCritico().mensaje());
            return resultado;
        }

        log.info("Control de cargas: {} procesos, {} pendientes, {} finalizados — carteras críticas listas: {}",
                resultado.totales().totalProcesos(), resultado.totales().pendientes(),
                resultado.totales().finalizados(), resultado.resumenCritico().carterasActivasYPasivasListas());
        chatNotifier.notificar(resultado.resumenCritico().mensaje() + "\n"
                + "*Control de Cargas* — " + resultado.totales().totalProcesos() + " procesos totales, "
                + resultado.totales().pendientes() + " pendientes, "
                + resultado.totales().finalizados() + " finalizados.");
        return resultado;
    }

    /** Lógica pura de evaluación (misma semántica que el service de Node.js). */
    static ControlCargasResultado evaluar(List<ProcesoCarga> filas) {
        List<ProcesoCarga> procesos = filas == null ? List.of()
                : filas.stream().filter(p -> p.proceso() != null && !p.proceso().isBlank()).toList();
        if (procesos.isEmpty()) {
            return ControlCargasResultado.sinRegistros();
        }

        List<ProcesoCarga> criticos = procesos.stream().filter(ProcesoCarga::esCritico).toList();
        if (criticos.isEmpty()) {
            criticos = procesos.stream().filter(ProcesoCarga::esCriticoAlterno).toList();
        }
        boolean criticosListos = !criticos.isEmpty() && criticos.stream().allMatch(ProcesoCarga::finalizoConExito);

        List<ProcesoCarga> pendientes = procesos.stream().filter(p -> !p.finalizoConExito()).toList();
        List<ProcesoCarga> finalizados = procesos.stream().filter(ProcesoCarga::finalizoConExito).toList();

        String mensaje = criticosListos
                ? "✅ Excelente. Las carteras activas y pasivas han finalizado sus cargas correctamente."
                : "⚠️ Alerta: Hay carteras de activos/pasivos pendientes o con error.";

        return new ControlCargasResultado(true,
                new ControlCargasResultado.ResumenCritico(criticosListos, mensaje, criticos),
                new ControlCargasResultado.Totales(procesos.size(), pendientes.size(), finalizados.size()),
                pendientes, finalizados);
    }
}
