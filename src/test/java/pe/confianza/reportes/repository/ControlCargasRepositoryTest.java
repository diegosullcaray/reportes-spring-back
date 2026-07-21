package pe.confianza.reportes.repository;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ControlCargasRepositoryTest {

    @Test
    void resolverIndiceEncuentraElPrimerCandidatoPresente() {
        var columnas = Map.of("descripcion", 1, "fec_act", 2, "estado", 4);

        assertThat(ControlCargasRepository.resolverIndice(columnas, "des_pro", "descripcion")).isEqualTo(1);
        assertThat(ControlCargasRepository.resolverIndice(columnas, "est_pro", "estado")).isEqualTo(4);
    }

    @Test
    void resolverIndiceDevuelveMenosUnoSiNingunCandidatoExiste() {
        var columnas = Map.of("descripcion", 1);

        assertThat(ControlCargasRepository.resolverIndice(columnas, "fec_rep", "fecha_reporte")).isEqualTo(-1);
    }
}
