/**
 * services/emergencyDetector.js
 *
 * Guardrail determinista de posibles emergencias (se evalúa ANTES del LLM).
 *
 * Los patrones se aplican sobre el texto NORMALIZADO (minúsculas, sin tildes, sin
 * puntuación y con espacios simples), por lo que se escriben sin acentos.
 *
 * ⚠️ La app Android replica esta lista en
 *    app/src/main/java/uq/seminario/projects3/safety/EmergencyDetector.kt
 *    para reaccionar igual sin conexión. Si se modifica una, actualizar la otra
 *    (ambas tienen pruebas con los mismos casos).
 *
 * Se prefieren frases explícitas a palabras sueltas para evitar falsos positivos
 * ("mi vecino tuvo un infarto", "me caí ayer pero ya estoy bien").
 */

const EMERGENCY_PATTERNS = [
  // Ideación suicida o autolesión
  "me quiero morir",
  "quiero morir(?:me)?",
  "no quiero (?:seguir )?viv(?:ir|iendo)",
  "acabar con mi vida",
  "matarme",
  "me (?:voy a|quiero) matar",
  "suicidar(?:me)?",
  "quitarme la vida",
  "desaparecer para siempre",
  "(?:quisiera|quiero|prefiero) estar muert[oa]",
  "no vale la pena (?:seguir )?viviendo",
  "no vale la pena vivir",
  "me tome (?:muchas|todas las) pastillas",
  "me tome (?:un|el|todo el) frasco",
  // Dolor torácico / posible infarto
  "(?:fuerte |mucho |un )?dolor (?:muy )?(?:fuerte )?en el pecho",
  "dolor opresivo",
  "me duele (?:mucho |muy fuerte )?el pecho",
  "me aprieta el pecho",
  "presion en el pecho",
  "(?:me esta dando|creo que tengo|tengo|me dio) un infarto",
  // Dificultad para respirar
  "no puedo respirar",
  "me cuesta (?:mucho )?respirar",
  "me falta (?:el )?aire",
  "no me entra (?:el )?aire",
  "me (?:estoy )?ahog(?:o|ando)",
  // Caídas
  "me cai y no (?:me )?puedo (?:levantar(?:me)?|parar(?:me)?|mover(?:me)?)",
  "me acabo de caer",
  "me cai en el (?:bano|piso|suelo|cuarto|patio)",
  "estoy (?:tirad[oa] )?en el (?:piso|suelo) y no (?:me )?puedo",
  // Posible ACV
  "no siento (?:las piernas|la pierna|el brazo|los brazos|la mitad del cuerpo|un lado)",
  "se me durmio (?:la mitad de la cara|medio lado|la mitad del cuerpo)",
  "no puedo mover (?:el brazo|la pierna|un lado|la mitad)",
  "se me (?:torcio|desvio) la boca",
  // Pérdida de conciencia o sangrado
  "me voy a desmayar",
  "me desmaye",
  "estoy sangrando mucho",
  "(?:sale|tengo|hay) mucha sangre"
];

const CRITICAL_EMERGENCY_REGEX = new RegExp(`(?:^|\\s)(?:${EMERGENCY_PATTERNS.join("|")})(?=\\s|$)`);

/** Minúsculas, sin tildes ni puntuación y con espacios simples. */
function normalizeForEmergency(text) {
  if (!text || typeof text !== "string") return "";
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9ñ\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function isCriticalRisk(text) {
  const normalized = normalizeForEmergency(text);
  return normalized !== "" && CRITICAL_EMERGENCY_REGEX.test(normalized);
}

module.exports = {
  EMERGENCY_PATTERNS,
  isCriticalRisk,
  normalizeForEmergency
};
