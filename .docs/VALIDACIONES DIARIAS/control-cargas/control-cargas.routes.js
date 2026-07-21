const { Router } = require('express');
const asyncHandler = require('../../../shared/http/asyncHandler');
const { reportesLimiter } = require('../../../infrastructure/rate-limit/RateLimiter');
const controlCargasController = require('./control-cargas.controller');

const router = Router();

/**
 * GET /api/validaciones/control-cargas
 */
router.get(
  '/control-cargas',
  reportesLimiter,
  asyncHandler((req, res) => controlCargasController.obtenerEstado(req, res))
);

module.exports = router;
