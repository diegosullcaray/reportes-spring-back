package pe.confianza.reportes.service;

import pe.confianza.reportes.shared.ReporteResultado;

import java.time.LocalDate;

/** Contrato común de todos los reportes programados. */
public interface ReporteService {

    /** Código kebab-case del reporte; clave en {@code reportes.definiciones.*} y en la API manual. */
    String codigo();

    ReporteResultado generar(LocalDate corte);
}
