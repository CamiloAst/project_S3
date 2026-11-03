/**
 * server.js
 * 
 * Servidor Backend Express.js para el Asistente Gerontológico Inteligente.
 * Expone endpoints REST y prepara la infraestructura para integración con la app Android.
 */

require("dotenv").config();
const express = require("express");
const cors = require("cors");
const morgan = require("morgan");
const {
  handleChat,
  clearSession,
  getSessionInfo,
  getAiStatus,
  sessionStore
} = require("./controllers/chatController");
const { getTtsStatus } = require("./services/ttsService");
const {
  requireApiToken,
  chatRateLimiter,
  sessionRateLimiter,
  buildCorsOptions,
  isTokenRequired
} = require("./middleware/security");

const { version: SERVICE_VERSION } = require("./package.json");

const app = express();
const PORT = process.env.PORT || 3000;

app.disable("x-powered-by");
// Detrás de un proxy (ngrok, Render, Nginx) el límite por IP necesita la IP real del cliente
if (process.env.TRUST_PROXY) {
  app.set("trust proxy", /^\d+$/.test(process.env.TRUST_PROXY) ? Number(process.env.TRUST_PROXY) : process.env.TRUST_PROXY);
}

// =============================================================================
// 1. CONFIGURACIÓN DE MIDDLEWARES
// =============================================================================

// Logger HTTP para monitorear peticiones en consola
app.use(morgan(process.env.NODE_ENV === "production" ? "combined" : "dev"));

// CORS solo para orígenes declarados en CORS_ORIGINS (la app Android no usa CORS)
app.use(cors(buildCorsOptions()));

// Parseo de cuerpo JSON (los mensajes están limitados a 2000 caracteres en el controlador)
app.use(express.json({ limit: "32kb" }));

// Middleware para registrar la latencia de cada solicitud
app.use((req, res, next) => {
  const start = Date.now();
  res.on("finish", () => {
    const duration = Date.now() - start;
    if (req.originalUrl.startsWith("/api/chat")) {
      console.log(`⏱️  [Chat Latency] ${req.method} ${req.originalUrl} - ${res.statusCode} (${duration}ms)`);
    }
  });
  next();
});

// =============================================================================
// 2. RUTAS Y ENDPOINTS DE LA API
// =============================================================================

/**
 * Healthcheck y verificación de estado del servicio.
 * GET / o GET /api/health
 */
const healthHandler = (req, res) => {
  res.status(200).json({
    status: "ok",
    service: "Geriatric Companion AI Backend",
    version: SERVICE_VERSION,
    uptimeSeconds: Math.floor(process.uptime()),
    timestamp: new Date().toISOString(),
    sessionStore: sessionStore.kind,
    authRequired: isTokenRequired(),
    aiProvider: getAiStatus(),
    // Voz neural: neural = audios entregados, retries/failures/skipped = intentos fallidos o sin plazo
    tts: getTtsStatus()
  });
};

app.get("/", healthHandler);
app.get("/api/health", healthHandler);

/**
 * Endpoint principal de interacción con el asistente de voz.
 * Recibe el texto transcrito (STT) desde Android y retorna el esquema multimodal CARE.
 * POST /api/chat
 */
app.post("/api/chat", chatRateLimiter, requireApiToken, handleChat);

/**
 * Endpoints auxiliares para gestión de sesiones (protegidos con el mismo token)
 */
app.get("/api/chat/session/:sessionId", sessionRateLimiter, requireApiToken, getSessionInfo);
app.delete("/api/chat/session/:sessionId", sessionRateLimiter, requireApiToken, clearSession);

// =============================================================================
// 3. MANEJO DE RUTAS NO ENCONTRADAS (404) Y ERRORES GLOBALES
// =============================================================================

// 404 Handler
app.use((req, res, next) => {
  res.status(404).json({
    error: "Not Found",
    message: `La ruta solicitada ${req.method} ${req.originalUrl} no existe en este servidor.`
  });
});

// Centralized Error Handling Middleware
app.use((err, req, res, next) => {
  console.error("💥 [Global Error Handler]:", err.stack || err);

  const statusCode = err.statusCode || err.status || 500;
  // Solo los errores del cliente marcados como públicos (p. ej. JSON mal formado de express.json)
  // muestran su mensaje; los 5xx nunca revelan detalles internos salvo con DEBUG_ERRORS=true.
  const isPublicError = statusCode < 500 && err.expose !== false;
  const debugErrors = process.env.DEBUG_ERRORS === "true";

  const errorResponse = {
    error: isPublicError ? err.name || "BadRequest" : "InternalServerError",
    message: isPublicError || debugErrors
      ? err.message || "Solicitud inválida."
      : "Ocurrió un error inesperado al procesar la solicitud.",
    timestamp: new Date().toISOString()
  };

  if (debugErrors) {
    errorResponse.stack = err.stack;
  }

  res.status(statusCode).json(errorResponse);
});

// =============================================================================
// 4. INICIALIZACIÓN DEL SERVIDOR
// =============================================================================
const server = app.listen(PORT, () => {
  console.log("\n=======================================================");
  console.log(`🤖 Cerebro IA Gerontológico Activo`);
  console.log(`📡 Servidor escuchando en: http://localhost:${PORT}`);
  console.log(`📱 Enlace Android Emulator:  http://10.0.2.2:${PORT}/api/chat`);
  console.log(`❤️  Health check disponible: http://localhost:${PORT}/api/health`);
  console.log("=======================================================\n");
});

// Graceful shutdown (persiste sesiones pendientes antes de salir)
function shutdown(signal) {
  console.log(`Cerrando servidor HTTP con elegancia (${signal})...`);
  server.close(async () => {
    try {
      await sessionStore.close();
    } catch (err) {
      console.error("Error cerrando sessionStore:", err.message);
    }
    console.log("Servidor cerrado.");
    process.exit(0);
  });
}

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));

module.exports = app;
