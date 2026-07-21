/**
 * Consultas SQL para el módulo de control de cargas y validación de base de datos
 */

module.exports = {
  /**
   * Obtiene el estado detallado de todos los procesos de carga
   */
  obtenerEstadoProcesos: () => `
    EXEC mod_rep.com.RSRPD001 @OPT = '2'
  `
};