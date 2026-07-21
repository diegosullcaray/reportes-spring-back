package pe.confianza.reportes.service.validacioncubo;

import org.junit.jupiter.api.Test;

import pe.confianza.reportes_programados.task_reportes_back.service.validacioncubo.CuboSnapshot;
import pe.confianza.reportes_programados.task_reportes_back.service.validacioncubo.ValidacionCuboService;
import pe.confianza.reportes_programados.task_reportes_back.service.validacioncubo.ValidacionFila;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ValidacionCuboServiceTest {

    private static final LocalDate CORTE = LocalDate.of(2026, 7, 13);

    private static CuboSnapshot snapshot(String desembolso, String tasa) {
        return new CuboSnapshot(CORTE,
                desembolso == null ? null : new BigDecimal(desembolso),
                tasa == null ? null : new BigDecimal(tasa),
                new BigDecimal("0.36"),
                new BigDecimal("8199.07"),
                new BigDecimal("98987564.21"),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    @Test
    void alertaSiElCuboNoTieneDatosDelDia() {
        var resultados = ValidacionCuboService.validar(CORTE, null, null);

        assertThat(resultados).hasSize(1);
        assertThat(resultados.getFirst().resultado()).isEqualTo(ValidacionFila.ALERTA);
        assertThat(resultados.getFirst().detalle()).contains("2026-07-13");
    }

    @Test
    void alertaPorIndicadorNuloYNegativo() {
        var actual = new CuboSnapshot(CORTE, null, new BigDecimal("-0.5"),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);

        var resultados = ValidacionCuboService.validar(CORTE, actual, null);

        assertThat(resultados)
                .filteredOn(v -> v.indicador().equals("SumMonDesembolsoMN"))
                .singleElement()
                .satisfies(v -> assertThat(v.resultado()).isEqualTo(ValidacionFila.ALERTA));
        assertThat(resultados)
                .filteredOn(v -> v.indicador().equals("TasaMesCre"))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.resultado()).isEqualTo(ValidacionFila.ALERTA);
                    assertThat(v.regla()).isEqualTo("Indicador no negativo");
                });
    }

    @Test
    void advertenciaSiLaVariacionSuperaElUmbral() {
        var actual = snapshot("200", "0.36");
        var anterior = snapshot("100", "0.36");

        var resultados = ValidacionCuboService.validar(CORTE, actual, anterior);

        assertThat(resultados)
                .filteredOn(v -> v.indicador().equals("SumMonDesembolsoMN"))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.resultado()).isEqualTo(ValidacionFila.ADVERTENCIA);
                    assertThat(v.detalle()).contains("100.00%");
                });
        // La tasa no varió: debe quedar OK
        assertThat(resultados)
                .filteredOn(v -> v.indicador().equals("TasaMesCre"))
                .singleElement()
                .satisfies(v -> assertThat(v.resultado()).isEqualTo(ValidacionFila.OK));
    }

    @Test
    void comparativoIncluyeVariacionContraElDiaAnterior() {
        var filas = ValidacionCuboService.compararIndicadores(CORTE, snapshot("110", "0.36"), snapshot("100", "0.36"));

        assertThat(filas).hasSize(7);
        assertThat(filas.getFirst().indicador()).isEqualTo("SumMonDesembolsoMN");
        assertThat(filas.getFirst().variacionPct()).isEqualByComparingTo("0.1");
    }
}
