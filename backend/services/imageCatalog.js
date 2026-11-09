/**
 * services/imageCatalog.js
 *
 * Banco LOCAL de ilustraciones que acompañan cada respuesta (Modelo CARE).
 *
 * Las imágenes viven en la app Android (res/drawable/img_<clave>.xml); el backend solo decide
 * la clave (`image_key`). Así no se genera ni se descarga ninguna imagen y Gemini gasta unos
 * pocos tokens por turno: elige una clave de un enum. Si Gemini no responde, la clave sale del
 * tema que detectan las palabras clave del mensaje o, en su defecto, de la emoción.
 *
 * - Complementariedad: la imagen ilustra el tema de la conversación (música, familia, la cita
 *   médica...); no repite screen_text ni contradice a la voz.
 * - Asignación: el apoyo visual va a la pantalla, pero la imagen nunca es el único canal de un
 *   dato ni de una acción.
 * - Redundancia: ante una posible emergencia la imagen es fija (`ayuda_cercana`) y refuerza el
 *   mismo mensaje que se oye y se lee.
 * - Equivalencia: en la app cada imagen tiene una descripción accesible que TalkBack lee en voz.
 *
 * ⚠️ La app replica las claves en app/.../images/ContextImage.kt. Ambas pruebas comparan su
 *    catálogo con test/image-keys.json: al añadir o quitar una imagen, actualizar los tres.
 */

/** Clave reservada para posibles emergencias: Gemini no puede elegirla. */
const EMERGENCY_IMAGE_KEY = "ayuda_cercana";
/** Imagen del guardrail médico (consultar al médico; nunca se muestran medicamentos). */
const MEDICAL_GUARDRAIL_IMAGE_KEY = "salud";
const DEFAULT_IMAGE_KEY = "saludo";

/**
 * Catálogo seleccionable. `hint` va al prompt de Gemini: corto, para gastar pocos tokens, y
 * vacío cuando la clave ya se explica sola ("mascotas", "clima").
 * `keywords` se usan solo en el respaldo, sobre texto normalizado (sin tildes) y con plural
 * opcional. El ORDEN es la prioridad cuando un mensaje menciona varios temas
 * ("la cita con el médico" -> salud antes que agenda; "sopa de letras" -> lectura antes que comida).
 */
const IMAGE_CATALOG = [
  {
    key: "salud",
    hint: "médico, controles, ejercicio",
    keywords: [
      "medico", "medica", "doctor", "doctora", "hospital", "clinica", "enfermera", "enfermero",
      "pastilla", "medicamento", "medicina", "remedio", "presion arterial", "tension", "salud",
      "ejercicio", "terapia", "examen", "control medico"
    ]
  },
  {
    key: "agenda",
    hint: "citas, horarios, pendientes, olvidos",
    keywords: [
      "cita", "agenda", "pendiente", "horario", "calendario", "recordatorio", "se me olvido",
      "olvide", "no me acuerdo", "compromiso", "diligencia", "tramite"
    ]
  },
  {
    key: "familia",
    hint: "hijos, nietos, visitas gratas",
    keywords: [
      "familia", "hijo", "hija", "nieto", "nieta", "bisnieto", "bisnieta", "esposo", "esposa",
      "marido", "hermano", "hermana", "sobrino", "sobrina", "yerno", "nuera", "visita",
      "me visitaron", "vinieron a verme"
    ]
  },
  {
    key: "mascotas",
    hint: "",
    keywords: [
      "mascota", "perro", "perra", "perrito", "perrita", "gato", "gata", "gatico", "gatito",
      "loro", "pajaro", "canario"
    ]
  },
  {
    key: "musica",
    hint: "canciones, baile, radio",
    keywords: [
      "musica", "cancion", "cantar", "bailar", "baile", "radio", "bolero", "tango", "vallenato",
      "bambuco", "guitarra", "serenata", "melodia"
    ]
  },
  {
    key: "lectura",
    hint: "libros, periódico, noticias",
    keywords: [
      "leer", "leyendo", "libro", "lectura", "periodico", "noticia", "revista", "crucigrama",
      "sopa de letras"
    ]
  },
  {
    key: "comida",
    hint: "cocina, recetas",
    keywords: [
      "comida", "comer", "almuerzo", "almorzar", "desayuno", "desayunar", "cena", "cenar",
      "cocinar", "cocina", "receta", "sopa", "sancocho", "arepa", "postre"
    ]
  },
  {
    key: "naturaleza",
    hint: "paseos, parque, plantas",
    keywords: [
      "paseo", "pasear", "caminar", "caminata", "parque", "jardin", "flor", "planta", "arbol",
      "campo", "finca", "montana"
    ]
  },
  {
    key: "espiritualidad",
    hint: "fe, oración, misa",
    keywords: ["misa", "rezar", "rezo", "orar", "oracion", "iglesia", "virgen", "rosario", "bendicion"]
  },
  {
    key: "descanso",
    hint: "sueño, noche",
    keywords: [
      "dormir", "dormi", "dormido", "dormida", "duermo", "sueno", "insomnio", "descansar", "descanso", "siesta", "cama",
      "desvelo", "desvelado", "desvelada"
    ]
  },
  {
    key: "clima",
    hint: "",
    keywords: [
      "clima", "lluvia", "llueve", "llovio", "llover", "sol", "soleado", "calor", "frio", "nublado",
      "aguacero", "invierno", "verano"
    ]
  },
  {
    key: "recuerdos",
    hint: "juventud, fotos, historias de antes",
    keywords: [
      "recuerdo", "juventud", "cuando era joven", "cuando era nino", "cuando era nina",
      "de joven", "infancia", "foto", "album", "antiguamente"
    ]
  },
  {
    key: "alegria",
    hint: "logros, celebraciones, buenas noticias",
    keywords: [
      "cumpleanos", "celebrar", "celebracion", "fiesta", "logre", "logro", "buena noticia",
      "aniversario", "graduacion", "premio"
    ]
  },
  {
    key: "calma",
    hint: "respirar, relajarse",
    keywords: ["respirar", "relajarme", "relajar", "calmarme", "tranquilizarme", "meditar"]
  },
  { key: "acompanamiento", hint: "soledad, tristeza", keywords: [] },
  { key: "escucha", hint: "pedir aclaración", keywords: [] },
  { key: DEFAULT_IMAGE_KEY, hint: "charla cotidiana", keywords: [] }
];

const SELECTABLE_IMAGE_KEYS = IMAGE_CATALOG.map(({ key }) => key);
const ALL_IMAGE_KEYS = [...SELECTABLE_IMAGE_KEYS, EMERGENCY_IMAGE_KEY];

/** Imagen cuando el mensaje no tiene un tema reconocible (o el tema no conviene a la emoción). */
const DEFAULT_IMAGE_BY_EMOTION = {
  POSITIVA: "alegria",
  NEGATIVA_TRISTEZA_SOLEDAD: "acompanamiento",
  NEGATIVA_ANSIEDAD_PREOCUPACION: "calma",
  NEUTRA: DEFAULT_IMAGE_KEY,
  AMBIGUA: "escucha"
};

/**
 * Imágenes que no se muestran con una emoción negativa: una familia sonriente o una
 * celebración pueden acentuar lo que falta ("mis hijos no me han llamado") o restar seriedad
 * a una preocupación. Se sustituyen por la imagen de la emoción (compañía o calma).
 */
const IMAGES_AVOIDED_BY_EMOTION = {
  NEGATIVA_TRISTEZA_SOLEDAD: ["familia", "alegria"],
  NEGATIVA_ANSIEDAD_PREOCUPACION: ["familia", "alegria"]
};

/** Mismo criterio que normalizeText del controlador: minúsculas, sin tildes ni puntuación. */
function normalizeForTopics(text) {
  if (!text || typeof text !== "string") return "";
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[¿?¡!.,;:()"'…]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function escapeRegExp(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

// Patrones precompilados: palabra o frase completa con plural opcional ("nietos", "canciones")
const TOPIC_PATTERNS = IMAGE_CATALOG.filter(({ keywords }) => keywords.length > 0).map(({ key, keywords }) => ({
  key,
  pattern: new RegExp(
    `(?:^|\\s)(?:${keywords.map((kw) => escapeRegExp(normalizeForTopics(kw))).join("|")})(?:s|es)?(?=\\s|$)`
  )
}));

/** Tema del mensaje según las palabras clave (respaldo sin Gemini); null si no hay ninguno. */
function detectImageTopic(text) {
  const normalized = normalizeForTopics(text);
  if (!normalized) return null;
  const match = TOPIC_PATTERNS.find(({ pattern }) => pattern.test(normalized));
  return match ? match.key : null;
}

/**
 * Clave final de la imagen de una respuesta.
 * 1. Posible emergencia -> siempre `ayuda_cercana` (Redundancia).
 * 2. La clave propuesta (Gemini o banco) si es válida; si no, el tema del mensaje del usuario.
 * 3. Sin nada utilizable, o si la imagen no conviene a la emoción, la imagen de la emoción.
 */
function resolveImageKey({ candidate, userInput = "", emotion, action } = {}) {
  if (action === "emergency_alert") return EMERGENCY_IMAGE_KEY;

  const emotionDefault = DEFAULT_IMAGE_BY_EMOTION[emotion] || DEFAULT_IMAGE_KEY;
  const proposed = typeof candidate === "string" ? candidate.trim().toLowerCase() : "";
  let key = SELECTABLE_IMAGE_KEYS.includes(proposed) ? proposed : detectImageTopic(userInput);
  if (!key || (IMAGES_AVOIDED_BY_EMOTION[emotion] || []).includes(key)) {
    key = emotionDefault;
  }
  return key;
}

/** Bloque del system prompt que explica a Gemini cómo elegir image_key. */
function buildImagePromptGuide() {
  const options = IMAGE_CATALOG.map(({ key, hint }) => (hint ? `${key} (${hint})` : key)).join("; ");
  return (
    `- Imagen (CARE): en image_key elige la ilustración que mejor represente el tema de la conversación; complementa la voz y el texto, nunca los contradice: ${options}.\n` +
    `  Si el usuario está triste o preocupado no uses "familia" ni "alegria": usa "acompanamiento" o "calma".`
  );
}

module.exports = {
  IMAGE_CATALOG,
  SELECTABLE_IMAGE_KEYS,
  ALL_IMAGE_KEYS,
  EMERGENCY_IMAGE_KEY,
  MEDICAL_GUARDRAIL_IMAGE_KEY,
  DEFAULT_IMAGE_KEY,
  DEFAULT_IMAGE_BY_EMOTION,
  IMAGES_AVOIDED_BY_EMOTION,
  detectImageTopic,
  resolveImageKey,
  buildImagePromptGuide
};
