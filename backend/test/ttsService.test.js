const { test } = require("node:test");
const assert = require("node:assert/strict");

// Sin red: la síntesis devuelve audio_base64=null pero indica la voz que habría usado
process.env.EDGE_TTS_ENABLED = "false";

const {
  resolveCompanion,
  synthesizeSpeechWithEmotion,
  calculateAudioDurationMs,
  prewarmTts,
  getTtsStatus,
  VOICE_PROFILES
} = require("../services/ttsService");

test("el acompañante se normaliza a female o male", () => {
  assert.equal(resolveCompanion("male"), "male");
  assert.equal(resolveCompanion("female"), "female");
  assert.equal(resolveCompanion(undefined), "female");
  assert.equal(resolveCompanion("MALE"), "female");
  assert.equal(resolveCompanion("__proto__"), "female");
  assert.equal(resolveCompanion("toString"), "female");
});

test("cada acompañante usa su propia voz neural", async () => {
  const male = await synthesizeSpeechWithEmotion("Hola, ¿cómo estás?", "NEUTRA", { companion: "male" });
  const female = await synthesizeSpeechWithEmotion("Hola, ¿cómo estás?", "NEUTRA", { companion: "female" });
  const byDefault = await synthesizeSpeechWithEmotion("Hola, ¿cómo estás?", "NEUTRA");

  assert.equal(male.voice, VOICE_PROFILES.male.primary);
  assert.equal(female.voice, VOICE_PROFILES.female.primary);
  assert.equal(byDefault.voice, VOICE_PROFILES.female.primary);
  assert.notEqual(male.voice, female.voice);
  assert.equal(male.audio_base64, null);
});

test("la duración del MP3 se calcula a 96 kbps", () => {
  // 96 kbps = 12 bytes por milisegundo
  assert.equal(calculateAudioDurationMs("hola", Buffer.alloc(12_000)), 1000);
});

test("el precalentamiento sin voz neural no hace nada y el estado lo indica", () => {
  assert.doesNotThrow(() => prewarmTts("male"));
  const status = getTtsStatus();
  assert.equal(status.enabled, false);
  assert.equal(status.voices.male, VOICE_PROFILES.male.primary);
  assert.equal(status.stats.failures, 0);
});
