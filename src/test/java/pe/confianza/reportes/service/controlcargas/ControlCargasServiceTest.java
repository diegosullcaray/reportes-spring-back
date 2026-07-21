package pe.confianza.reportes.service.controlcargas;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ControlCargasServiceTest {

    private static ProcesoCarga proceso(String nombre, String estado) {
        return new ProcesoCarga(nombre, LocalDate.of(2026, 7, 21), LocalDate.of(2026, 7, 20), estado);
    }

    @Test
    void sinRegistrosDevuelveFalloConMensajeClaro() {
        var resultado = ControlCargasService.evaluar(List.of());

        assertThat(resultado.success()).isFalse();
        assertThat(resultado.resumenCritico().mensaje()).contains("mod_rep.com.RSRPD001");
    }

    @Test
    void carterasCriticasFinalizadasReportanExito() {
        var resultado = ControlCargasService.evaluar(List.of(
                proceso("CARGA CARTERA ACTIVA", "FINALIZADO"),
                proceso("CARGA CARTERA PASIVA", "OK"),
                proceso("CARGA SEGUROS", "EN PROCESO")));

        assertThat(resultado.success()).isTrue();
        assertThat(resultado.resumenCritico().carterasActivasYPasivasListas()).isTrue();
        assertThat(resultado.resumenCritico().mensaje()).contains("✅");
        assertThat(resultado.totales()).isEqualTo(new ControlCargasResultado.Totales(3, 1, 2));
        assertThat(resultado.procesosPendientes()).extracting(ProcesoCarga::proceso)
                .containsExactly("CARGA SEGUROS");
    }

    @Test
    void carteraCriticaPendienteGeneraAlerta() {
        var resultado = ControlCargasService.evaluar(List.of(
                proceso("CARGA CARTERA ACTIVA", "FINALIZADO"),
                proceso("CARGA CARTERA PASIVA", "EN PROCESO")));

        assertThat(resultado.resumenCritico().carterasActivasYPasivasListas()).isFalse();
        assertThat(resultado.resumenCritico().mensaje()).contains("⚠️");
    }

    @Test
    void sinCoincidenciaExactaUsaBusquedaAlternaPorActivaPasiva() {
        // No dice "cartera", pero sí "activa": aplica el criterio alterno del Node.js
        var resultado = ControlCargasService.evaluar(List.of(
                proceso("CARGA ACTIVA DIARIA", "TERMINADO"),
                proceso("CARGA CONTABLE", "COMPLETADO")));

        assertThat(resultado.resumenCritico().procesosCriticosEvaluados())
                .extracting(ProcesoCarga::proceso).containsExactly("CARGA ACTIVA DIARIA");
        assertThat(resultado.resumenCritico().carterasActivasYPasivasListas()).isTrue();
    }

    @Test
    void filasSinDescripcionSeIgnoran() {
        var resultado = ControlCargasService.evaluar(List.of(
                new ProcesoCarga(null, null, null, "FINALIZADO"),
                proceso("CARGA CARTERA ACTIVA", "FINALIZADO")));

        assertThat(resultado.totales().totalProcesos()).isEqualTo(1);
    }
}
