/**
 * middleware/security.js
 *
 * Protección básica de la API:
 * - Token de acceso compartido (API_ACCESS_TOKEN) enviado por la app en
 *   `Authorization: Bearer <token>` o `X-API-Key: <token>`.
 * - Límite de peticiones por IP para no agotar la cuota de Gemini.
 * - CORS deshabilitado salvo orígenes explícitos (la app Android no lo necesita).
 *
 * ⚠️ El token viaja dentro del APK, por lo que solo frena el uso casual o automatizado
 *    de la API; no sustituye una autenticación real por usuario.
 */

const crypto = require("crypto");
const rateLimit = require("express-rate-limit");

const API_ACCESS_TOKEN = (process.env.API_ACCESS_TOKEN || "").trim();
const IS_PRODUCTION = process.env.NODE_ENV === "production";

if (!API_ACCESS_TOKEN) {
  if (IS_PRODUCTION) {
    throw new Error("API_ACCESS_TOKEN es obligatorio cuando NODE_ENV=production.");
  }
  console.warn("⚠️  [Security] API_ACCESS_TOKEN no configurado: la API acepta peticiones sin token (solo desarrollo).");
}

if (IS_PRODUCTION && !process.env.TRUST_PROXY) {
  console.warn(
    "⚠️  [Security] TRUST_PROXY no configurado: detrás de un proxy todos los clientes comparten la IP del proxy " +
      "y el límite por minuto se vuelve global."
  );
}

function extractToken(req) {
  const auth = req.get("authorization") || "";
  if (auth.toLowerCase().startsWith("bearer ")) {
    return auth.slice(7).trim();
  }
  return (req.get("x-api-key") || "").trim();
}

/** Comparación en tiempo constante para no filtrar el token por diferencias de latencia. */
function tokensMatch(provided, expected) {
  const a = Buffer.from(provided);
  const b = Buffer.from(expected);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function requireApiToken(req, res, next) {
  if (!API_ACCESS_TOKEN) return next();
  const provided = extractToken(req);
  if (provided && tokensMatch(provided, API_ACCESS_TOKEN)) return next();
  return res.status(401).json({
    error: "Unauthorized",
    message: "Token de acceso ausente o inválido."
  });
}

function positiveIntFromEnv(name, fallback) {
  const value = parseInt(process.env[name] || "", 10);
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

const chatRateLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: positiveIntFromEnv("CHAT_RATE_LIMIT_PER_MINUTE", 20),
  standardHeaders: "draft-7",
  legacyHeaders: false,
  message: {
    error: "Too Many Requests",
    message: "Se superó el límite de mensajes por minuto. Intenta de nuevo en unos segundos."
  }
});

const sessionRateLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: positiveIntFromEnv("SESSION_RATE_LIMIT_PER_MINUTE", 30),
  standardHeaders: "draft-7",
  legacyHeaders: false
});

/** Lista de orígenes permitidos para navegadores (vacía = sin cabeceras CORS). */
function buildCorsOptions() {
  const origins = (process.env.CORS_ORIGINS || "")
    .split(",")
    .map((origin) => origin.trim())
    .filter(Boolean);
  return {
    origin: origins.length > 0 ? origins : false,
    methods: ["GET", "POST", "DELETE", "OPTIONS"],
    allowedHeaders: ["Content-Type", "Authorization", "X-API-Key"]
  };
}

module.exports = {
  requireApiToken,
  chatRateLimiter,
  sessionRateLimiter,
  buildCorsOptions,
  isTokenRequired: () => Boolean(API_ACCESS_TOKEN)
};
