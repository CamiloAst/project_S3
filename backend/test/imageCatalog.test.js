const { test } = require("node:test");
const assert = require("node:assert/strict");
const sharedKeys = require("./image-keys.json");

const {
  SELECTABLE_IMAGE_KEYS,
  ALL_IMAGE_KEYS,
  EMERGENCY_IMAGE_KEY,
  DEFAULT_IMAGE_BY_EMOTION,
  detectImageTopic,
  resolveImageKey,
  buildImagePromptGuide
} = require("../services/imageCatalog");

test("el catálogo coincide con las claves compartidas con la app", () => {
  assert.deepEqual(SELECTABLE_IMAGE_KEYS, sharedKeys.selectable);
  assert.equal(EMERGENCY_IMAGE_KEY, sharedKeys.emergency);
  assert.deepEqual(DEFAULT_IMAGE_BY_EMOTION, sharedKeys.defaultsByEmotion);
  assert.equal(new Set(ALL_IMAGE_KEYS).size, ALL_IMAGE_KEYS.length, "claves repetidas");
  for (const key of ALL_IMAGE_KEYS) {
    assert.match(key, /^[a-z_]+$/, `"${key}" debe servir como nombre de recurso Android (img_${key})`);
  }
});

test("Gemini no puede elegir la imagen reservada para emergencias", () => {
  assert.ok(!SELECTABLE_IMAGE_KEYS.includes(EMERGENCY_IMAGE_KEY));
  assert.doesNotMatch(buildImagePromptGuide(), new RegExp(EMERGENCY_IMAGE_KEY));
  for (const key of SELECTABLE_IMAGE_KEYS) {
    assert.match(buildImagePromptGuide(), new RegExp(`\\b${key}\\b`), `el prompt debe ofrecer "${key}"`);
  }
});

test("el detector de temas reconoce el contexto del mensaje", () => {
  const cases = {
    "Hoy vinieron mis nietos a almorzar": "familia",
    "Tengo cita con el médico el martes": "salud",
    "Se me olvidó qué tenía pendiente": "agenda",
    "Me encantan los boleros de antes": "musica",
    "Fuimos a caminar al parque": "naturaleza",
    "Anoche no he dormido nada": "descanso",
    "Qué frío está haciendo": "clima",
    "Mi perrita se llama Luna": "mascotas",
    "Estoy haciendo una sopa de letras": "lectura",
    "Preparé un sancocho": "comida",
    "Voy a misa los domingos": "espiritualidad",
    "Encontré unas fotos de mi juventud": "recuerdos",
    "Hoy es mi cumpleaños": "alegria"
  };
  for (const [text, expected] of Object.entries(cases)) {
    assert.equal(detectImageTopic(text), expected, text);
  }
});

test("sin tema reconocible el detector no inventa uno", () => {
  for (const text of ["buenas tardes", "eh", "me río mucho", "solo quería saludarte", "", null]) {
    assert.equal(detectImageTopic(text), null, String(text));
  }
});

test("una posible emergencia siempre muestra la imagen de ayuda cercana", () => {
  assert.equal(
    resolveImageKey({ candidate: "alegria", emotion: "POSITIVA", action: "emergency_alert" }),
    EMERGENCY_IMAGE_KEY
  );
});

test("la clave de Gemini se respeta si es válida y apropiada", () => {
  assert.equal(resolveImageKey({ candidate: "musica", emotion: "NEUTRA", action: "continue" }), "musica");
  assert.equal(resolveImageKey({ candidate: " MUSICA ", emotion: "NEUTRA", action: "continue" }), "musica");
});

test("una clave inválida o ausente usa el tema del mensaje y luego la emoción", () => {
  assert.equal(
    resolveImageKey({ candidate: "pastillas", userInput: "fui al parque", emotion: "POSITIVA", action: "continue" }),
    "naturaleza"
  );
  assert.equal(
    resolveImageKey({ candidate: EMERGENCY_IMAGE_KEY, userInput: "hola", emotion: "NEUTRA", action: "continue" }),
    "saludo",
    "la imagen de emergencia no se muestra fuera de una emergencia"
  );
  for (const [emotion, expected] of Object.entries(DEFAULT_IMAGE_BY_EMOTION)) {
    assert.equal(resolveImageKey({ userInput: "buenas tardes", emotion, action: "continue" }), expected, emotion);
  }
  assert.equal(resolveImageKey({ emotion: "DESCONOCIDA", action: "continue" }), "saludo");
});

test("con tristeza o preocupación no se muestran familia ni celebraciones", () => {
  assert.equal(
    resolveImageKey({
      candidate: "familia",
      userInput: "mis hijos no me han llamado",
      emotion: "NEGATIVA_TRISTEZA_SOLEDAD",
      action: "continue"
    }),
    "acompanamiento"
  );
  assert.equal(
    resolveImageKey({ candidate: "alegria", emotion: "NEGATIVA_ANSIEDAD_PREOCUPACION", action: "continue" }),
    "calma"
  );
  assert.equal(
    resolveImageKey({ candidate: "familia", emotion: "POSITIVA", action: "continue" }),
    "familia",
    "en un momento grato la familia sí se muestra"
  );
});
