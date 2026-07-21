const cron = require('node-cron');
const controlCargasService = require('./control-cargas.service');
const logger = require('../../../infrastructure/logging/logger');

const timezone = process.env.TZ_SCHEDULES || undefined;
// Cada 5 minutos por defecto; configurable con CONTROL_CARGAS_CRON
const cronExpr = process.env.CONTROL_CARGAS_CRON || '*/5 * * * *';

// Evita que dos ejecuciones se solapen si una consulta a BD tarda más de la ventana
let enEjecucion = false;

/**
 * Tarea programada: Validación de Control de Cargas.
 *
 * Corre cada 5 minutos, consulta el estado de las cargas en SQL Server y
 * notifica el resultado al espacio de Google Chat (GOOGLE_CHAT_WEBHOOK_URL).
 * La notificación la hace el propio service en cada llamada a validarCargas().
 */
function inicializarSchedules() {
  cron.schedule(cronExpr, async () => {
    if (enEjecucion) {
      logger.warn('⏭️ [CONTROL CARGAS] Ejecución anterior aún en curso, se omite este ciclo');
      return;
    }

    enEjecucion = true;
    logger.info('⏰ [CONTROL CARGAS] Validación automática programada');
    try {
      await controlCargasService.validarCargas();
    } catch (error) {
      logger.error(`Error en schedule control de cargas: ${error.message}`);
    } finally {
      enEjecucion = false;
    }
  }, { timezone });

  logger.info(`✅ Schedule Control de Cargas: "${cronExpr}" (por defecto cada 5 minutos)`);
}

module.exports = { inicializarSchedules };
