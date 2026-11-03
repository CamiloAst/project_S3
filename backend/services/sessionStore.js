/**
 * services/sessionStore.js
 *
 * Persistencia de sesiones conversacionales con backends intercambiables
 * (variable de entorno SESSION_STORE):
 *
 * - "file"   (por defecto): caché en memoria + snapshot JSON en disco con escritura
 *            atómica. Las sesiones sobreviven a reinicios del servidor.
 *            Adecuado para UNA sola instancia del backend.
 * - "redis": recomendado para producción o múltiples instancias. Requiere
 *            `npm install redis` y la variable REDIS_URL. El TTL lo gestiona Redis.
 * - "memory": solo memoria (se pierde al reiniciar). Útil para pruebas.
 *
 * Todas las implementaciones exponen la misma interfaz asíncrona:
 *   get(sessionId) -> objeto de sesión | null
 *   save(sessionId, session)
 *   delete(sessionId) -> boolean
 *   close()
 *
 * ⚠️ Privacidad: las sesiones contienen conversaciones de adultos mayores.
 *    El archivo de sesiones NO debe versionarse (ver .gitignore) y en producción
 *    debe residir en un volumen protegido o en un Redis con autenticación.
 */

const fs = require("fs");
const path = require("path");

const DEFAULT_TTL_MS = parseInt(process.env.SESSION_TTL_MINUTES || "60", 10) * 60 * 1000;
const DEFAULT_FILE_PATH = path.resolve(
  process.env.SESSION_FILE_PATH || path.join(__dirname, "..", "data", "sessions.json")
);
const CLEANUP_INTERVAL_MS = 15 * 60 * 1000;

// =============================================================================
// 1. MEMORIA (base para el almacenamiento en archivo)
// =============================================================================
class MemorySessionStore {
  constructor({ ttlMs = DEFAULT_TTL_MS } = {}) {
    this.kind = "memory";
    this.ttlMs = ttlMs;
    this.sessions = new Map();

    this.cleanupTimer = setInterval(() => this.cleanupExpired(), CLEANUP_INTERVAL_MS);
    // No mantener vivo el proceso solo por el temporizador de limpieza
    this.cleanupTimer.unref();
  }

  isExpired(session, now = Date.now()) {
    return !session || now - (session.lastUpdated || 0) > this.ttlMs;
  }

  cleanupExpired() {
    const now = Date.now();
    let removed = 0;
    for (const [sessionId, session] of this.sessions.entries()) {
      if (this.isExpired(session, now)) {
        this.sessions.delete(sessionId);
        removed++;
        console.log(`[Session Cleanup] Sesión inactiva eliminada: ${sessionId}`);
      }
    }
    if (removed > 0) this.onChange();
  }

  async get(sessionId) {
    const session = this.sessions.get(sessionId);
    if (!session) return null;
    if (this.isExpired(session)) {
      this.sessions.delete(sessionId);
      this.onChange();
      return null;
    }
    return session;
  }

  async save(sessionId, session) {
    session.lastUpdated = Date.now();
    this.sessions.set(sessionId, session);
    this.onChange();
  }

  async delete(sessionId) {
    const existed = this.sessions.delete(sessionId);
    if (existed) this.onChange();
    return existed;
  }

  /** Hook para subclases que necesitan persistir los cambios. */
  onChange() {}

  async close() {
    clearInterval(this.cleanupTimer);
  }
}

// =============================================================================
// 2. ARCHIVO JSON (persistente, una sola instancia)
// =============================================================================
class FileSessionStore extends MemorySessionStore {
  constructor({ filePath = DEFAULT_FILE_PATH, ttlMs = DEFAULT_TTL_MS, flushDelayMs = 1000 } = {}) {
    super({ ttlMs });
    this.kind = "file";
    this.filePath = filePath;
    this.flushDelayMs = flushDelayMs;
    this.flushTimer = null;
    this.flushing = null;
    this.dirty = false;

    this.loadFromDisk();
  }

  loadFromDisk() {
    if (!fs.existsSync(this.filePath)) {
      console.log(`💾 [SessionStore:file] Sin sesiones previas. Se guardarán en: ${this.filePath}`);
      return;
    }

    try {
      const raw = fs.readFileSync(this.filePath, "utf8");
      const parsed = JSON.parse(raw);
      const entries = Object.entries(parsed.sessions || {});
      const now = Date.now();
      let restored = 0;

      for (const [sessionId, session] of entries) {
        if (!this.isExpired(session, now)) {
          this.sessions.set(sessionId, session);
          restored++;
        }
      }
      console.log(`💾 [SessionStore:file] ${restored} sesión(es) restauradas desde ${this.filePath}`);
    } catch (err) {
      // Archivo corrupto: se aparta para análisis y se arranca con un almacén vacío
      const backupPath = `${this.filePath}.corrupt-${Date.now()}`;
      try {
        fs.renameSync(this.filePath, backupPath);
      } catch (_) {}
      console.error(
        `💥 [SessionStore:file] No se pudo leer ${this.filePath} (${err.message}). ` +
          `Se movió a ${backupPath} y se inicia sin sesiones.`
      );
    }
  }

  onChange() {
    this.dirty = true;
    if (this.flushTimer) return;
    this.flushTimer = setTimeout(() => {
      this.flushTimer = null;
      this.flush().catch((err) =>
        console.error(`💥 [SessionStore:file] Error guardando sesiones: ${err.message}`)
      );
    }, this.flushDelayMs);
  }

  /**
   * Escribe el snapshot de forma atómica (archivo temporal + rename) para que un
   * corte abrupto nunca deje un JSON a medio escribir.
   */
  async flush() {
    if (this.flushing) {
      await this.flushing;
    }
    if (!this.dirty) return;
    this.dirty = false;

    const snapshot = {
      version: 1,
      savedAt: new Date().toISOString(),
      sessions: Object.fromEntries(this.sessions)
    };
    const tmpPath = `${this.filePath}.tmp`;

    this.flushing = (async () => {
      await fs.promises.mkdir(path.dirname(this.filePath), { recursive: true });
      await fs.promises.writeFile(tmpPath, JSON.stringify(snapshot), "utf8");
      await fs.promises.rename(tmpPath, this.filePath);
    })();

    try {
      await this.flushing;
    } catch (err) {
      this.dirty = true; // reintentar en el próximo cambio o al cerrar
      throw err;
    } finally {
      this.flushing = null;
    }
  }

  async close() {
    await super.close();
    if (this.flushTimer) {
      clearTimeout(this.flushTimer);
      this.flushTimer = null;
    }
    await this.flush();
  }
}

// =============================================================================
// 3. REDIS (producción / múltiples instancias)
// =============================================================================
class RedisSessionStore {
  constructor({ url, ttlMs = DEFAULT_TTL_MS, keyPrefix = "care:session:" } = {}) {
    if (!url) {
      throw new Error("SESSION_STORE=redis requiere definir REDIS_URL (ej. redis://localhost:6379).");
    }

    let redis;
    try {
      redis = require("redis");
    } catch (_) {
      throw new Error("SESSION_STORE=redis requiere instalar el cliente: npm install redis");
    }

    this.kind = "redis";
    this.ttlMs = ttlMs;
    this.keyPrefix = keyPrefix;
    this.client = redis.createClient({ url });
    this.client.on("error", (err) => console.error(`💥 [SessionStore:redis] ${err.message}`));
    this.ready = this.client.connect().then(() => {
      console.log("💾 [SessionStore:redis] Conectado.");
    });
  }

  key(sessionId) {
    return `${this.keyPrefix}${sessionId}`;
  }

  async get(sessionId) {
    await this.ready;
    const raw = await this.client.get(this.key(sessionId));
    return raw ? JSON.parse(raw) : null;
  }

  async save(sessionId, session) {
    await this.ready;
    session.lastUpdated = Date.now();
    // PX: Redis elimina la sesión automáticamente tras el TTL de inactividad
    await this.client.set(this.key(sessionId), JSON.stringify(session), { PX: this.ttlMs });
  }

  async delete(sessionId) {
    await this.ready;
    const removed = await this.client.del(this.key(sessionId));
    return removed > 0;
  }

  async close() {
    try {
      await this.ready;
      if (typeof this.client.close === "function") {
        await this.client.close();
      } else {
        await this.client.quit();
      }
    } catch (_) {}
  }
}

// =============================================================================
// 4. FÁBRICA + INSTANCIA COMPARTIDA
// =============================================================================
function createSessionStore() {
  const kind = (process.env.SESSION_STORE || "file").trim().toLowerCase();

  switch (kind) {
    case "memory":
      console.warn("⚠️  [SessionStore] Modo 'memory': las sesiones se perderán al reiniciar.");
      return new MemorySessionStore();
    case "redis":
      return new RedisSessionStore({ url: process.env.REDIS_URL });
    case "file":
      return new FileSessionStore();
    default:
      console.warn(`⚠️  [SessionStore] SESSION_STORE='${kind}' no reconocido. Usando 'file'.`);
      return new FileSessionStore();
  }
}

const sessionStore = createSessionStore();

module.exports = {
  sessionStore,
  createSessionStore,
  MemorySessionStore,
  FileSessionStore,
  RedisSessionStore
};
