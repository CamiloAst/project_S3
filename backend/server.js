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
const { handleChat, clearSession, getSessionInfo } = require("./controllers/chatController");

const app = express();
const PORT = process.env.PORT || 3000;

// =============================================================================
// 1. CONFIGURACIÓN DE MIDDLEWARES
// =============================================================================

// Logger HTTP para monitorear peticiones en consola
app.use(morgan(process.env.NODE_ENV === "production" ? "combined" : "dev"));

// Configuración de CORS permisivo para desarrollo y apps móviles Android
const corsOptions = {
  origin: "*", // En producción se recomienda limitar a dominios específicos
  methods: ["GET", "POST", "DELETE", "OPTIONS"],
  allowedHeaders: ["Content-Type", "Authorization"]
};
app.use(cors(corsOptions));

// Parseo de cuerpo JSON y URL-Encoded
app.use(express.json({ limit: "2mb" }));
app.use(express.urlencoded({ extended: true, limit: "2mb" }));

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
    version: "1.0.0",
    uptimeSeconds: Math.floor(process.uptime()),
    timestamp: new Date().toISOString(),
    aiProvider: {
      model: process.env.GEMINI_MODEL || "gemini-2.0-flash",
      configured: Boolean(process.env.GEMINI_API_KEY && process.env.GEMINI_API_KEY !== "tu_gemini_api_key_aqui")
    }
  });
};

app.get("/", healthHandler);
app.get("/api/health", healthHandler);

/**
 * Endpoint principal de interacción con el asistente de voz.
 * Recibe el texto transcrito (STT) desde Android y retorna el esquema multimodal CARE.
 * POST /api/chat
 */
app.post("/api/chat", handleChat);

/**
 * Endpoints auxiliares para gestión de sesiones en memoria
 */
app.get("/api/chat/session/:sessionId", getSessionInfo);
app.delete("/api/chat/session/:sessionId", clearSession);

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

  const statusCode = err.statusCode || 500;
  const errorResponse = {
    error: err.name || "InternalServerError",
    message: err.message || "Ocurrió un error inesperado al procesar la solicitud.",
    timestamp: new Date().toISOString()
  };

  // En desarrollo se expone el stack trace para facilitar la depuración
  if (process.env.NODE_ENV !== "production") {
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

// Graceful shutdown
process.on("SIGTERM", () => {
  console.log("Cerrando servidor HTTP con elegancia (SIGTERM)...");
  server.close(() => {
    console.log("Servidor cerrado.");
    process.exit(0);
  });
});

process.on("SIGINT", () => {
  console.log("Cerrando servidor HTTP con elegancia (SIGINT)...");
  server.close(() => {
    console.log("Servidor cerrado.");
    process.exit(0);
  });
});

module.exports = app;
