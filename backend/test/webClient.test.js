const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const path = require("path");

// ttsService lee la configuración al cargarse: sin red durante las pruebas
process.env.EDGE_TTS_ENABLED = "false";

const express = require("express");
const { serveWebClient, PUBLIC_DIR } = require("../middleware/webClient");
const { VOICE_PROFILES } = require("../services/ttsService");

let server;
let baseUrl;

before(async () => {
  const app = express();
  app.use(serveWebClient());
  app.get("/api/health", (req, res) => res.json({ status: "ok" }));
  await new Promise((resolve) => {
    server = app.listen(0, resolve);
  });
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(() => server.close());

const appJs = () => fs.readFileSync(path.join(PUBLIC_DIR, "app.js"), "utf8");

test("sirve la versión web en / con una CSP que solo admite recursos propios", async () => {
  const respuesta = await fetch(`${baseUrl}/`);
  assert.equal(respuesta.status, 200);
  assert.match(respuesta.headers.get("content-type"), /text\/html/);
  const csp = respuesta.headers.get("content-security-policy");
  assert.match(csp, /script-src 'self'/);
  assert.doesNotMatch(csp, /unsafe-inline|unsafe-eval/);
  assert.match(await respuesta.text(), /<script src="app\.js" defer><\/script>/);
});

test("sirve los recursos de la página sin tapar las rutas de la API", async () => {
  for (const ruta of ["/app.js", "/estilos.css", "/img/avatar_companera.webp", "/img/avatar_companero.webp"]) {
    assert.equal((await fetch(`${baseUrl}${ruta}`)).status, 200, ruta);
  }
  assert.equal((await fetch(`${baseUrl}/api/health`)).status, 200);
});

test("la página nunca interpreta como HTML los textos que llegan del servidor", () => {
  assert.doesNotMatch(appJs(), /innerHTML|outerHTML|insertAdjacentHTML|document\.write|\beval\(/);
});

test("la web tiene una ilustración y una descripción para cada clave del banco de imágenes", async () => {
  const claves = require("./image-keys.json");
  for (const clave of [...claves.selectable, claves.emergency]) {
    const respuesta = await fetch(`${baseUrl}/img/temas/${clave}.svg`);
    assert.equal(respuesta.status, 200, `img/temas/${clave}.svg`);
    assert.match(respuesta.headers.get("content-type"), /image\/svg\+xml/);
    assert.match(appJs(), new RegExp(`\\b${clave}: "[^"]+"`), `descripción de "${clave}" en IMAGENES`);
  }
  assert.match(appJs(), new RegExp(`IMAGEN_EMERGENCIA = "${claves.emergency}"`));
  for (const [emocion, clave] of Object.entries(claves.defaultsByEmotion)) {
    assert.match(appJs(), new RegExp(`${emocion}: "${clave}"`), emocion);
  }
});

test("los acompañantes de la web son las voces del backend", () => {
  for (const companion of Object.keys(VOICE_PROFILES)) {
    assert.match(appJs(), new RegExp(`\\b${companion}: \\{ etiqueta:`), companion);
  }
});
