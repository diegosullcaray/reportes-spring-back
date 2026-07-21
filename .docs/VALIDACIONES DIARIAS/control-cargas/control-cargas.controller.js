const controlCargasService = require('./control-cargas.service');

class ControlCargasController {
  /**
   * GET /api/validaciones/control-cargas
   * Obtiene la validación en vivo del estado de las cargas y carteras críticas
   */
  async obtenerEstado(req, res) {
    const resultado = await controlCargasService.validarCargas();
    res.status(resultado.success ? 200 : 400).json(resultado);
  }
}

module.exports = new ControlCargasController();
