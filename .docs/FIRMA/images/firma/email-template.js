/**
 * Plantilla HTML corporativa para los correos de reportes.
 * Firma configurable vía EMAIL_FIRMA_NOMBRE / EMAIL_FIRMA_CARGO.
 * Incluye logo Confianza como imagen embebida (CID) al lado izquierdo de la firma.
 */

const path = require('path');

const FIRMA_NOMBRE = process.env.EMAIL_FIRMA_NOMBRE || 'Diego Denilson Sullcaray Ramos';
const FIRMA_CARGO = process.env.EMAIL_FIRMA_CARGO || 'Analista de Sistema de la Información';
const FIRMA_DIRECCION = 'Las Begonias 441 oficina 338C, San Isidro, Lima';
const FIRMA_WEB = 'www.confianza.pe';

// Ruta absoluta a la imagen del logo para adjuntar como CID
const LOGO_PATH = path.join(__dirname, '..', '..', '..', 'public', 'images', 'image.png');
const LOGO_CID = 'logo_confianza';

// Bloque HTML de la firma (logo + datos), reutilizado por todas las plantillas
const firmaHtml = () => `
      <p>--<br>Saludos,</p>

      <table cellpadding="0" cellspacing="0" border="0" style="font-family: Arial, sans-serif; font-size: 13px; color: #333;">
        <tr>
          <td style="padding-right: 15px; vertical-align: middle;">
            <img src="cid:${LOGO_CID}" alt="Financiera Confianza" width="150" style="display: block;" />
          </td>
          <td style="border-left: 3px solid #0072CE; padding-left: 15px; vertical-align: middle;">
            <strong style="color: #0072CE; font-size: 14px;">${FIRMA_NOMBRE}</strong><br>
            <strong>${FIRMA_CARGO}</strong><br>
            <span style="color: #555;">${FIRMA_DIRECCION}</span><br>
            <a href="https://${FIRMA_WEB}" style="color: #0072CE; font-weight: bold; text-decoration: none;">${FIRMA_WEB}</a>
          </td>
        </tr>
      </table>`;

const firmaAttachments = () => [
  {
    filename: 'logo_confianza.png',
    path: LOGO_PATH,
    cid: LOGO_CID
  }
];

/**
 * Genera el cuerpo HTML estándar de un correo de reporte (con adjunto Excel).
 * @param {string} saludo - Ej. "Estimado Alvaro," / "Estimados,"
 * @param {string} [mensaje] - Cuerpo del mensaje
 * @returns {{ html: string, firmaAttachments: Object[] }} HTML del email y adjuntos inline de la firma
 */
function plantillaCorreoReporte(saludo, mensaje = 'se le adjunta lo solicitado') {
  const html = `
    <div style="font-family: Arial, sans-serif; font-size: 14px; color: #222;">
      <p>${saludo}<br>${mensaje}</p>
${firmaHtml()}
    </div>
  `;

  return { html, firmaAttachments: firmaAttachments() };
}

/**
 * Genera el cuerpo HTML de un correo cuyo contenido son tablas embebidas
 * (no hay Excel adjunto: el resultado va directo en el cuerpo).
 * @param {Object} opciones
 * @param {string} opciones.saludo - Ej. "Estimados,"
 * @param {string} [opciones.mensaje] - Texto introductorio antes de las tablas
 * @param {string} opciones.cuerpoHtml - HTML de las tablas a insertar en el cuerpo
 * @returns {{ html: string, firmaAttachments: Object[] }}
 */
function plantillaCorreoReporteHtml({ saludo, mensaje = 'se comparten los datos del cierre:', cuerpoHtml }) {
  const html = `
    <div style="font-family: Arial, sans-serif; font-size: 14px; color: #222;">
      <p>${saludo}<br>${mensaje}</p>

      ${cuerpoHtml}
${firmaHtml()}
    </div>
  `;

  return { html, firmaAttachments: firmaAttachments() };
}

module.exports = { plantillaCorreoReporte, plantillaCorreoReporteHtml };
