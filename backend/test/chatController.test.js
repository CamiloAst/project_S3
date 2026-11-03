const { test } = require("node:test");
const assert = require("node:assert/strict");

// Sin Gemini, sin voz neural y sin escribir sesiones en disco: el controlador usa el banco de respaldo
process.env.GEMINI_API_KEY = "";
process.env.EDGE_TTS_ENABLED = "false";
process.env.SESSION_STORE = "memory";

const {
  handleChat,
  containsMedicalPrescription,
  detectAffectiveSituation,
  selectNonRepeatingVariation,
  createEmptySession,
  RESPONSE_VARIATIONS_BANK,
  EMOTION_CATEGORIES
} = require("../controllers/chatController");

const VALID_ACTIONS = ["continue", "show_decision_buttons", "clarify", "emergency_alert"];

/** Ejecuta handleChat con un req/res mínimos y devuelve { status, body }. */
async function callChat(body) {
  let status = 200;
  let payload;
  const res = {
    status(code) {
      status = code;
      return this;
    },
    json(data) {
      payload = data;
      return this;
    }
  };
  let forwardedError;
  await handleChat({ body }, res, (err) => {
    forwardedError = err;
  });
  if (forwardedError) throw forwardedError;
  return { status, body: payload };
}

test("valida message y sessionId", async () => {
  assert.equal((await callChat({ sessionId: "s1" })).status, 400);
  assert.equal((await callChat({ message: "   ", sessionId: "s1" })).status, 400);
  assert.equal((await callChat({ message: "hola" })).status, 400);
  assert.equal((await callChat({ message: "hola", sessionId: "con espacios" })).status, 400);
  assert.equal((await callChat({ message: "x".repeat(2001), sessionId: "s1" })).status, 400);
});

test("una posible emergencia responde emergency_alert sin prometer llamadas", async () => {
  const { status, body } = await callChat({ message: "me duele mucho el pecho", sessionId: "s_emergencia" });
  assert.equal(status, 200);
  assert.equal(body.source, "emergency_guardrail");
  assert.equal(body.data.suggested_action, "emergency_alert");
  assert.doesNotMatch(body.data.spoken_text, /te comunico|vamos a avisar|llamar[eé]/i);
  assert.equal(body.data.audio_base64, null);
});

test("sin Gemini responde desde el banco con un contrato válido", async () => {
  const { status, body } = await callChat({ message: "hoy vinieron mis nietos y estoy feliz", sessionId: "s_banco" });
  assert.equal(status, 200);
  assert.equal(body.source, "fallback_bank");
  assert.equal(body.fallback_reason, "gemini_not_configured");
  assert.ok(EMOTION_CATEGORIES.includes(body.data.user_emotion));
  assert.ok(VALID_ACTIONS.includes(body.data.suggested_action));
  assert.ok(body.data.spoken_text.length > 0);
  const screenWords = body.data.screen_text.trim().split(/\s+/).length;
  assert.ok(screenWords >= 3 && screenWords <= 7, `screen_text con ${screenWords} palabras`);
  assert.notEqual(body.data.screen_text, body.data.spoken_text);
});

test("no repite la misma respuesta en turnos seguidos de una sesión", async () => {
  const first = await callChat({ message: "me siento muy solo", sessionId: "s_repeticion" });
  const second = await callChat({ message: "me siento muy solo", sessionId: "s_repeticion" });
  assert.notEqual(first.body.data.spoken_text, second.body.data.spoken_text);
});

test("detector de respaldo clasifica negaciones y saludos", () => {
  assert.equal(detectAffectiveSituation("estoy muy feliz"), "POSITIVA");
  assert.equal(detectAffectiveSituation("no me siento bien"), "NEGATIVA_TRISTEZA_SOLEDAD");
  assert.equal(detectAffectiveSituation("buenas tardes"), "NEUTRA");
  assert.equal(detectAffectiveSituation("se me olvidó la pastilla"), "NEGATIVA_ANSIEDAD_PREOCUPACION");
  assert.equal(detectAffectiveSituation("eh"), "AMBIGUA");
});

test("guardrail médico detecta indicaciones de dosis sin falsos positivos", () => {
  assert.equal(containsMedicalPrescription("Tómate dos pastillas ahora"), true);
  assert.equal(containsMedicalPrescription("Toma 500 mg cada ocho horas"), true);
  assert.equal(containsMedicalPrescription("Deja de tomar ese medicamento"), true);
  assert.equal(containsMedicalPrescription("¿Tomamos un respiro?"), false);
  assert.equal(containsMedicalPrescription("Toma un vaso de agua con calma"), false);
});

test("el banco no repite variaciones hasta agotarlas", () => {
  const session = createEmptySession();
  const total = RESPONSE_VARIATIONS_BANK.POSITIVA.length;
  const seen = new Set();
  for (let i = 0; i < total; i++) {
    const variation = selectNonRepeatingVariation(session, "POSITIVA");
    seen.add(variation.spoken_text);
    session.lastSpokenTexts.push(variation.spoken_text);
  }
  assert.ok(seen.size >= total - 1, `Solo ${seen.size} variaciones distintas de ${total}`);
});
