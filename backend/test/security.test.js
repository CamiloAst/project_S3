const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");

// security.js lee la configuración al cargarse: se define antes del require
process.env.API_ACCESS_TOKEN = "token-de-prueba";
process.env.CHAT_RATE_LIMIT_PER_MINUTE = "3";

const express = require("express");
const { requireApiToken, chatRateLimiter } = require("../middleware/security");

let server;
let baseUrl;

before(async () => {
  const app = express();
  app.post("/protegido", requireApiToken, (req, res) => res.json({ ok: true }));
  app.post("/limitado", chatRateLimiter, (req, res) => res.json({ ok: true }));
  await new Promise((resolve) => {
    server = app.listen(0, resolve);
  });
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(() => server.close());

const post = (path, headers = {}) => fetch(`${baseUrl}${path}`, { method: "POST", headers });

test("rechaza peticiones sin token o con token incorrecto", async () => {
  assert.equal((await post("/protegido")).status, 401);
  assert.equal((await post("/protegido", { Authorization: "Bearer otro" })).status, 401);
});

test("acepta el token por Authorization o X-API-Key", async () => {
  assert.equal((await post("/protegido", { Authorization: "Bearer token-de-prueba" })).status, 200);
  assert.equal((await post("/protegido", { "X-API-Key": "token-de-prueba" })).status, 200);
});

test("limita las peticiones por minuto", async () => {
  const statuses = [];
  for (let i = 0; i < 4; i++) {
    statuses.push((await post("/limitado")).status);
  }
  assert.deepEqual(statuses, [200, 200, 200, 429]);
});
