package pe.confianza.reportes_programados.task_reportes_back.service;

import java.time.LocalDate;

import pe.confianza.reportes_programados.task_reportes_back.shared.ReporteResultado;

/** Contrato común de todos los reportes programados. */
public interface ReporteService {

    /** Código kebab-case del reporte; clave en {@code reportes.definiciones.*} y en la API manual. */
    String codigo();

    ReporteResultado generar(LocalDate corte);
}
