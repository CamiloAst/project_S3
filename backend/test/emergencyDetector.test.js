const { test } = require("node:test");
const assert = require("node:assert/strict");
const { isCriticalRisk } = require("../services/emergencyDetector");
const cases = require("./emergency-cases.json");

test("detecta frases de posible emergencia", () => {
  for (const text of cases.critical) {
    assert.equal(isCriticalRisk(text), true, `Debió detectarse: "${text}"`);
  }
});

test("no marca conversación cotidiana", () => {
  for (const text of cases.safe) {
    assert.equal(isCriticalRisk(text), false, `Falso positivo: "${text}"`);
  }
});
