/**
 * Versión web de prueba (backend/public/): la misma conversación que la app Android, en el navegador,
 * para quien no puede instalar la APK (p. ej. con un iPhone). Se sirve desde el mismo origen que la API,
 * así que no necesita CORS.
 *
 * La página guarda el token de acceso en localStorage: la política de contenido (CSP) solo admite
 * scripts, estilos e imágenes propios, para que ningún script ajeno pueda leerlo.
 */

const path = require("path");
const express = require("express");

const PUBLIC_DIR = path.join(__dirname, "..", "public");

const SECURITY_HEADERS = {
  "Content-Security-Policy": [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self'",
    "img-src 'self' data:",
    // Audio neural como blob: y un silencio en data: para desbloquear el audio en iPhone
    "media-src 'self' blob: data:",
    "connect-src 'self'",
    "base-uri 'none'",
    "form-action 'none'",
    "frame-ancestors 'none'"
  ].join("; "),
  "Referrer-Policy": "no-referrer",
  "X-Content-Type-Options": "nosniff",
  "Permissions-Policy": "microphone=(self), camera=(), geolocation=()"
};

function serveWebClient() {
  return express.static(PUBLIC_DIR, {
    index: "index.html",
    setHeaders(res, filePath) {
      for (const [name, value] of Object.entries(SECURITY_HEADERS)) {
        res.setHeader(name, value);
      }
      // HTML, CSS y JS se revalidan siempre: cada despliegue llega al momento a quien prueba
      res.setHeader("Cache-Control", filePath.endsWith(".webp") ? "public, max-age=86400" : "no-cache");
    }
  });
}

module.exports = { serveWebClient, PUBLIC_DIR };
