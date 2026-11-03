/**
 * services/ttsService.js
 *
 * Servicio de Síntesis de Voz Neural Gratuita (Microsoft Edge Neural TTS)
 * con Modulación Afectiva Gerontológica y Reloj Maestro de Sincronía.
 *
 * - Una voz por acompañante elegido en la app: compañera (es-CO-SalomeNeural, o es-MX-DaliaNeural
 *   si falla) y compañero (es-CO-GonzaloNeural, o es-MX-JorgeNeural).
 * - Modulación afectiva de prosodia (rate y pitch) según user_emotion.
 * - Edge gratis cierra el WebSocket tras 20-60 s sin uso y a veces tarda varios segundos en
 *   reconectar o corta el audio a mitad. Por eso: se reabre la conexión mientras Gemini piensa
 *   (prewarmTts), cada intento tiene plazos propios y, si falla, se reintenta una vez con una
 *   conexión nueva mientras quede plazo. Solo si todo falla se devuelve audio_base64: null y
 *   Android usa su TTS local (más robótico).
 */

const { MsEdgeTTS, OUTPUT_FORMAT } = require("msedge-tts");

// Voces por acompañante. EDGE_TTS_VOICE / EDGE_TTS_VOICE_MALE permiten cambiar la principal.
const VOICE_PROFILES = {
  female: {
    primary: process.env.EDGE_TTS_VOICE || "es-CO-SalomeNeural",
    fallback: "es-MX-DaliaNeural"
  },
  male: {
    primary: process.env.EDGE_TTS_VOICE_MALE || "es-CO-GonzaloNeural",
    fallback: "es-MX-JorgeNeural"
  }
};
// Sin acompañante (apps anteriores) o con un valor desconocido se usa la voz femenina
const DEFAULT_COMPANION = "female";
// EDGE_TTS_ENABLED=false desactiva la voz neural (pruebas automáticas o desarrollo sin red):
// las respuestas llegan con audio_base64=null y la app usa su TTS local.
const EDGE_TTS_ENABLED = process.env.EDGE_TTS_ENABLED !== "false";
// 96 kbps (el máximo que admite Edge gratis) suena más limpio que 48 kbps
const TTS_FORMAT = OUTPUT_FORMAT.AUDIO_24KHZ_96KBITRATE_MONO_MP3;
const TTS_BITRATE_KBPS = 96;
const TTS_CONNECTION_TIMEOUT_MS = 3500; // Conexión WebSocket (una reconexión normal tarda ~1 s)
const TTS_FIRST_BYTE_TIMEOUT_MS = 3000; // Sin audio (o sin actividad) tras enviar el texto
const TTS_MAX_STREAM_MS = 8000; // Límite de un intento completo
const TTS_MAX_ATTEMPTS = 2; // Un reintento con conexión nueva
const TTS_MIN_ATTEMPT_MS = 2500; // No se empieza un intento con menos plazo que esto

/**
 * Mapa de modulación prosódica afectiva según la emoción detectada del adulto mayor.
 * Ralentizaciones suaves: más de un 10 % hace que la voz neural suene estirada y robótica.
 */
const AFFECTIVE_PROSODY_MAP = {
  NEGATIVA_TRISTEZA_SOLEDAD: {
    rate: "-8%",
    pitch: "-1Hz"
  },
  NEGATIVA_ANSIEDAD_PREOCUPACION: {
    rate: "-10%",
    pitch: "-2Hz"
  },
  POSITIVA: {
    rate: "0%",
    pitch: "+1Hz"
  },
  NEUTRA: {
    rate: "-5%",
    pitch: "0Hz"
  },
  AMBIGUA: {
    rate: "-5%",
    pitch: "0Hz"
  }
};

// Contadores para GET /api/health: permiten ver cuántas veces falla la voz neural y por qué
const ttsStats = {
  neural: 0,
  retries: 0,
  failures: 0,
  skipped: 0,
  lastError: null
};

// Caché LRU ligera en memoria para respuestas frecuentes (evita latencia de red repetida)
const MAX_AUDIO_CACHE_ENTRIES = 40;
const audioCache = new Map();

// Un cliente reutilizable y precalentado por acompañante (minimiza el handshake WebSocket):
// companion -> { voiceName, readyPromise }
const ttsClients = new Map();

/** Normaliza el acompañante recibido de la app: "female" o "male" (por defecto "female"). */
function resolveCompanion(companion) {
  return Object.hasOwn(VOICE_PROFILES, companion) ? companion : DEFAULT_COMPANION;
}

/** Voz que suena para un acompañante (la alternativa si la principal falló al conectar). */
function voiceNameFor(companion) {
  return ttsClients.get(companion)?.voiceName || VOICE_PROFILES[companion].primary;
}

/**
 * Obtiene la configuración de prosodia (rate y pitch) para una emoción dada.
 *
 * @param {string} userEmotion Categoría emocional detectada.
 * @returns {{ rate: string, pitch: string }}
 */
function getProsodyForEmotion(userEmotion) {
  const normalized = (userEmotion || "NEUTRA").trim().toUpperCase();
  return AFFECTIVE_PROSODY_MAP[normalized] || AFFECTIVE_PROSODY_MAP.NEUTRA;
}

/**
 * Escapa caracteres especiales XML/SSML para evitar errores de síntesis en Edge TTS.
 */
function escapeSsmlText(text) {
  if (!text || typeof text !== "string") return "";
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

/**
 * Calcula la duración estimada del audio en milisegundos (Reloj Maestro de Sincronía).
 * - Si existe el buffer MP3 (tasa constante TTS_BITRATE_KBPS), calcula la duración exacta.
 * - Si no hay buffer (fallback), estima según conteo de palabras y factor de velocidad.
 *
 * @param {string} spokenText Texto hablado.
 * @param {Buffer|null} audioBuffer Buffer MP3 generado (opcional).
 * @param {string} rateStr Porcentaje de velocidad ej. "-15%".
 * @returns {number} Duración estimada en milisegundos.
 */
function calculateAudioDurationMs(spokenText, audioBuffer = null, rateStr = "-12%") {
  if (audioBuffer && Buffer.isBuffer(audioBuffer) && audioBuffer.length > 0) {
    // kbps = bits por milisegundo: 96 kbps = 12 bytes/ms
    const mp3DurationMs = Math.round((audioBuffer.length * 8) / TTS_BITRATE_KBPS);
    return Math.max(800, mp3DurationMs);
  }

  const words = (spokenText || "")
    .trim()
    .split(/\s+/)
    .filter(Boolean).length;

  const rateNumeric = parseFloat(rateStr) || -12;
  const speedMultiplier = Math.max(0.6, 1 + rateNumeric / 100);
  const wordsPerSecond = 2.3 * speedMultiplier;

  return Math.max(1200, Math.round((words / wordsPerSecond) * 1000));
}

/**
 * Recupera (o crea) el cliente MsEdgeTTS del acompañante. Si la voz principal no conecta,
 * se intenta una vez con la alternativa del mismo acompañante.
 */
function getTtsEntry(companion) {
  const existing = ttsClients.get(companion);
  if (existing) return existing;

  const profile = VOICE_PROFILES[companion];
  const client = new MsEdgeTTS({
    enableLogger: false,
    connectionTimeoutMs: TTS_CONNECTION_TIMEOUT_MS,
    synthesisTimeoutMs: TTS_FIRST_BYTE_TIMEOUT_MS
  });
  const entry = { voiceName: profile.primary, readyPromise: null };

  entry.readyPromise = client
    .setMetadata(profile.primary, TTS_FORMAT)
    .then(() => client)
    .catch(async (err) => {
      console.warn(`⚠️  [EdgeTTS] Reintentando con voz alternativa ${profile.fallback}: ${err.message}`);
      entry.voiceName = profile.fallback;
      await client.setMetadata(profile.fallback, TTS_FORMAT);
      return client;
    });
  ttsClients.set(companion, entry);
  return entry;
}

/** Descarta un cliente fallido; la siguiente síntesis abre uno nuevo. */
function dropTtsEntry(companion, entry) {
  if (ttsClients.get(companion) === entry) {
    ttsClients.delete(companion);
  }
}

/**
 * Reabre en segundo plano la conexión del acompañante si Edge la cerró por inactividad.
 * handleChat la llama al recibir el mensaje, así la reconexión ocurre mientras Gemini piensa.
 */
function prewarmTts(companion) {
  if (!EDGE_TTS_ENABLED) return;
  const voiceCompanion = resolveCompanion(companion);
  const entry = getTtsEntry(voiceCompanion);
  entry.readyPromise
    // setMetadata no hace nada si el WebSocket sigue abierto; si está cerrado, reconecta
    .then((client) => client.setMetadata(entry.voiceName, TTS_FORMAT))
    .catch(() => dropTtsEntry(voiceCompanion, entry));
}

/**
 * Un intento de síntesis hacia Buffer MP3 usando MsEdgeTTS, con plazos propios para la
 * conexión, el primer audio y el total (nunca más allá de `deadline`).
 */
async function synthesizeBufferOnce(spokenText, prosody, companion, deadline) {
  const entry = getTtsEntry(companion);
  const attemptBudgetMs = Math.min(TTS_MAX_STREAM_MS, Math.max(0, deadline - Date.now()));
  let firstChunkTimer = null;
  let totalStreamTimer = null;

  return new Promise((resolve, reject) => {
    let settled = false;
    let audioStream = null;
    const chunks = [];

    const cleanupTimers = () => {
      if (firstChunkTimer) clearTimeout(firstChunkTimer);
      if (totalStreamTimer) clearTimeout(totalStreamTimer);
      firstChunkTimer = null;
      totalStreamTimer = null;
    };

    const failFast = (err) => {
      if (settled) return;
      settled = true;
      cleanupTimers();
      if (audioStream) {
        try {
          audioStream.destroy();
        } catch (_) {}
      }
      dropTtsEntry(companion, entry);
      reject(err);
    };

    // Límite del intento completo (conexión + audio), acotado por el plazo de la petición
    totalStreamTimer = setTimeout(() => {
      failFast(new Error(`Timeout total del intento (${attemptBudgetMs}ms) en Edge TTS`));
    }, attemptBudgetMs);

    // 1. Plazo para tener la conexión inicial lista
    firstChunkTimer = setTimeout(() => {
      failFast(new Error(`Timeout de conexión (${TTS_CONNECTION_TIMEOUT_MS}ms) con Edge TTS`));
    }, TTS_CONNECTION_TIMEOUT_MS);

    entry.readyPromise
      .then((client) => {
        if (settled) return;
        if (firstChunkTimer) {
          clearTimeout(firstChunkTimer);
        }

        // 2. Plazo para el primer audio (incluye reconectar si Edge cerró el WebSocket por inactividad)
        firstChunkTimer = setTimeout(() => {
          failFast(new Error(`Timeout (${TTS_FIRST_BYTE_TIMEOUT_MS}ms) sin recibir audio de Edge TTS`));
        }, TTS_FIRST_BYTE_TIMEOUT_MS);

        const cleanSsmlText = escapeSsmlText(spokenText);

        try {
          const streamBundle = client.toStream(cleanSsmlText, {
            rate: prosody.rate,
            pitch: prosody.pitch
          });
          audioStream = streamBundle.audioStream;
        } catch (err) {
          return failFast(err);
        }

        audioStream.on("data", (chunk) => {
          if (settled || !chunk) return;
          if (firstChunkTimer) {
            clearTimeout(firstChunkTimer);
            firstChunkTimer = null;
          }
          chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
        });

        audioStream.once("end", () => {
          if (settled) return;
          settled = true;
          cleanupTimers();
          const fullBuffer = Buffer.concat(chunks);
          if (fullBuffer.length === 0) {
            dropTtsEntry(companion, entry);
            return reject(new Error("El stream de Edge TTS finalizó sin bytes de audio."));
          }
          resolve(fullBuffer);
        });

        // "close" sin "end" = el audio quedó cortado: mejor reintentar que entregar media frase
        audioStream.once("close", () => {
          failFast(new Error("El stream de Edge TTS se cerró antes de terminar el audio."));
        });

        audioStream.once("error", (err) => {
          failFast(err);
        });
      })
      .catch((err) => {
        failFast(err);
      });
  });
}

// La clave incluye el acompañante: el mismo texto suena con voz distinta para cada uno
function audioCacheKey(trimmedText, userEmotion, companion) {
  return `${companion}|${userEmotion}|${trimmedText}`;
}

/** true si el audio de este texto ya está en caché (se entrega sin latencia de red). */
function hasCachedAudio(spokenText, userEmotion = "NEUTRA", companion = DEFAULT_COMPANION) {
  if (!EDGE_TTS_ENABLED || !spokenText || typeof spokenText !== "string") return false;
  return audioCache.has(audioCacheKey(spokenText.trim(), userEmotion, resolveCompanion(companion)));
}

/**
 * Convierte `spoken_text` en audio MP3 codificado en Base64 con modulación afectiva.
 * Si un intento falla (conexión lenta, audio cortado) se reintenta una vez con una conexión
 * nueva mientras quede plazo hasta `deadline`. Si no se logra, devuelve `audio_base64: null`
 * junto con `audio_duration_ms` estimado y la app usa su TTS local.
 *
 * @param {string} spokenText Texto que pronunciará el asistente.
 * @param {string} userEmotion Emoción detectada (POSITIVA, NEGATIVA_TRISTEZA_SOLEDAD, etc.).
 * @param {{ companion?: string, deadline?: number }} options Acompañante ("female" | "male") y
 *   momento límite (ms epoch) para entregar el audio; por defecto, un intento completo.
 * @returns {Promise<{ audio_base64: string | null, audio_duration_ms: number, voice: string, prosody: { rate: string, pitch: string } }>}
 */
async function synthesizeSpeechWithEmotion(
  spokenText,
  userEmotion = "NEUTRA",
  { companion = DEFAULT_COMPANION, deadline = Date.now() + TTS_MAX_STREAM_MS } = {}
) {
  const voiceCompanion = resolveCompanion(companion);
  const prosody = getProsodyForEmotion(userEmotion);
  const fallbackDurationMs = calculateAudioDurationMs(spokenText, null, prosody.rate);

  if (!EDGE_TTS_ENABLED || !spokenText || typeof spokenText !== "string" || !spokenText.trim()) {
    return {
      audio_base64: null,
      audio_duration_ms: fallbackDurationMs,
      voice: voiceNameFor(voiceCompanion),
      prosody
    };
  }

  const trimmedText = spokenText.trim();
  const cacheKey = audioCacheKey(trimmedText, userEmotion, voiceCompanion);

  if (audioCache.has(cacheKey)) {
    const cached = audioCache.get(cacheKey);
    return {
      audio_base64: cached.audio_base64,
      audio_duration_ms: cached.audio_duration_ms,
      voice: voiceNameFor(voiceCompanion),
      prosody
    };
  }

  const startTime = Date.now();
  let lastError = null;

  for (let attempt = 1; attempt <= TTS_MAX_ATTEMPTS; attempt++) {
    if (deadline - Date.now() < TTS_MIN_ATTEMPT_MS) break;
    if (attempt > 1) ttsStats.retries++;

    try {
      const mp3Buffer = await synthesizeBufferOnce(trimmedText, prosody, voiceCompanion, deadline);

      const audioBase64 = mp3Buffer.toString("base64");
      const durationMs = calculateAudioDurationMs(trimmedText, mp3Buffer, prosody.rate);
      const elapsedMs = Date.now() - startTime;
      ttsStats.neural++;

      console.log(
        `🎙️  [Neural TTS] Voz=${voiceNameFor(voiceCompanion)} | Emoción=${userEmotion} (rate=${prosody.rate}, pitch=${prosody.pitch}) | ` +
          `Duración=${durationMs}ms | Generado en ${elapsedMs}ms (${mp3Buffer.length} bytes, intento ${attempt})`
      );

      if (audioCache.size >= MAX_AUDIO_CACHE_ENTRIES) {
        const oldestKey = audioCache.keys().next().value;
        if (oldestKey) audioCache.delete(oldestKey);
      }
      audioCache.set(cacheKey, {
        audio_base64: audioBase64,
        audio_duration_ms: durationMs
      });

      return {
        audio_base64: audioBase64,
        audio_duration_ms: durationMs,
        voice: voiceNameFor(voiceCompanion),
        prosody
      };
    } catch (err) {
      lastError = err;
      console.warn(`🔁 [Neural TTS] Intento ${attempt}/${TTS_MAX_ATTEMPTS} falló tras ${Date.now() - startTime}ms: ${err.message}`);
    }
  }

  const elapsedMs = Date.now() - startTime;
  const reason = lastError ? lastError.message : "sin plazo suficiente para intentarlo";
  if (lastError) {
    ttsStats.failures++;
  } else {
    ttsStats.skipped++;
  }
  ttsStats.lastError = { message: String(reason).slice(0, 300), at: new Date().toISOString() };
  console.warn(
    `⚠️  [Neural TTS Fallback] Sin audio neural tras ${elapsedMs}ms (${reason}). ` +
      `Devolviendo audio_base64: null para respaldo TTS local en Android.`
  );

  return {
    audio_base64: null,
    audio_duration_ms: fallbackDurationMs,
    voice: voiceNameFor(voiceCompanion),
    prosody
  };
}

/** Estado de la voz neural para GET /api/health. */
function getTtsStatus() {
  return {
    enabled: EDGE_TTS_ENABLED,
    voices: Object.fromEntries(Object.keys(VOICE_PROFILES).map((c) => [c, voiceNameFor(c)])),
    stats: { ...ttsStats }
  };
}

// Precalentar en segundo plano la conexión de cada acompañante (sin bloquear el arranque)
if (EDGE_TTS_ENABLED) {
  for (const companion of Object.keys(VOICE_PROFILES)) {
    const entry = getTtsEntry(companion);
    entry.readyPromise.catch((err) => {
      dropTtsEntry(companion, entry);
      console.warn(
        `ℹ️  [Neural TTS Init] Precalentamiento diferido de la voz "${companion}" (${err.message}). Se conectará bajo demanda.`
      );
    });
  }
}

module.exports = {
  VOICE_PROFILES,
  DEFAULT_COMPANION,
  resolveCompanion,
  AFFECTIVE_PROSODY_MAP,
  getProsodyForEmotion,
  calculateAudioDurationMs,
  synthesizeSpeechWithEmotion,
  hasCachedAudio,
  prewarmTts,
  getTtsStatus,
  TTS_MIN_ATTEMPT_MS
};
