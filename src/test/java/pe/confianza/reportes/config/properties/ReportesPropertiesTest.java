package pe.confianza.reportes.config.properties;

import org.junit.jupiter.api.Test;
import pe.confianza.reportes.shared.ReporteException;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportesPropertiesTest {

    @Test
    void estrategiaDiaAnteriorRestaUnDia() {
        assertThat(ReportesProperties.EstrategiaCorte.DIA_ANTERIOR.resolver(LocalDate.of(2026, 7, 21)))
                .isEqualTo(LocalDate.of(2026, 7, 20));
    }

    @Test
    void estrategiaFinMesAnteriorDevuelveUltimoDiaDelMesPrevio() {
        assertThat(ReportesProperties.EstrategiaCorte.FIN_MES_ANTERIOR.resolver(LocalDate.of(2026, 7, 1)))
                .isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(ReportesProperties.EstrategiaCorte.FIN_MES_ANTERIOR.resolver(LocalDate.of(2026, 3, 15)))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void definicionInexistenteLanzaErrorClaro() {
        var props = new ReportesProperties("America/Lima", Path.of("/tmp"), "a@b.pe",
                List.of("s@b.pe"), 20, Map.of());

        assertThatThrownBy(() -> props.definicion("no-existe"))
                .isInstanceOf(ReporteException.class)
                .hasMessageContaining("reportes.definiciones.no-existe");
    }
}
