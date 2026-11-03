# CONTEXTO

Punto de partida para cada sesión de trabajo. Los detalles de arquitectura, contrato de la API y reglas que deben mantenerse sincronizadas están en `CLAUDE.md`; este archivo resume el estado y el historial.

## 1. Resumen
Asistente conversacional por voz para personas mayores (interfaz y contenidos en español), basado en el modelo de interacción multimodal CARE (Complementariedad, Asignación, Redundancia, Equivalencia). Una app Android captura la voz, consulta un backend que orquesta Google Gemini y una voz neuronal (Edge TTS), y presenta la respuesta por voz y en pantalla. Incluye detección de posibles emergencias (solo muestra `EmergencyScreen` y pide buscar ayuda cercana; no llama ni notifica a nadie).

## 2. Stack
- **Android** (`app/`): Kotlin 2.2.10, AGP 9.4.1, Jetpack Compose (BOM 2026.02.01, Material 3), Lifecycle 2.11.0, Retrofit 2.11.0 + OkHttp 4.12.0, `SpeechRecognizer` y TTS local del sistema. JDK 25 en CI (`gradle/gradle-daemon-jvm.properties`).
- **Backend** (`backend/`, v1.2.0): Node.js + Express 4, `@google/genai` (Gemini con salida JSON estructurada), `msedge-tts`, `express-rate-limit`, `morgan`, `dotenv`. Pruebas con `node:test`.
- **CI**: `.github/workflows/ci.yml` ejecuta `npm test` y `:app:testDebugUnitTest` en cada PR.

## 3. Estructura
- `app/src/main/java/uq/seminario/projects3/`
  - `audio/AudioEngine.kt`: reconocimiento de voz (variante de español del teléfono o es-CO) y reproducción.
  - `network/ChatApiClient.kt`: Retrofit, DTO todo-nullable y `toChatResponse()`.
  - `safety/EmergencyDetector.kt`: detector local de frases de emergencia.
  - `session/SessionIdProvider.kt`: `sessionId` único por instalación.
  - `viewmodel/ChatbotViewModel.kt`: máquina de estados `UiState`, pila de navegación, fallback local.
  - `ui/ChatbotApp.kt`, `ui/screens/*`, `ui/components/*`: pantallas Compose y barra de demostración (solo debug).
- `backend/`
  - `server.js`: Express, rate limit, rutas `/api/chat` y de sesión.
  - `controllers/chatController.js`: guardarraíl de emergencia, Gemini, banco de respuestas de respaldo, saneamiento.
  - `services/emergencyDetector.js`, `services/sessionStore.js` (file/redis/memory), `services/ttsService.js`.
  - `middleware/security.js`: token Bearer y utilidades de privacidad de logs.
  - `test/`: pruebas del controlador, detector (casos compartidos en `emergency-cases.json`) y seguridad.

## 4. Cómo ejecutar
- Backend (desde `backend/`): `npm install`, copiar `.env.example` a `.env` y definir `GEMINI_API_KEY`; `npm run dev` (puerto 3000); `npm test`.
  Variables útiles: `API_ACCESS_TOKEN` (obligatoria con `NODE_ENV=production`), `SESSION_STORE`, `EDGE_TTS_ENABLED`, `LOG_USER_TEXT`.
- Android (desde la raíz): `./gradlew assembleDebug`, `./gradlew :app:testDebugUnitTest`. En este Windows usar el JDK de Android Studio (`JAVA_HOME=E:\Program Files\Android\Android Studio\jbr` y `-Dorg.gradle.java.home=...`).
- Configuración app ↔ backend en `local.properties`: `care.apiBaseUrl.debug`, `care.apiBaseUrl.release` (HTTPS obligatorio), `care.apiToken` (igual a `API_ACCESS_TOKEN`).

## 5. Decisiones
- Respuesta con `spoken_text` completo y `screen_text` de 4 a 7 palabras que no lo copia (Complementariedad).
- Chequeo de emergencia antes del LLM, en backend y app, con los mismos casos de prueba compartidos.
- Si Gemini falla o tarda, banco de respuestas con anti-repetición por sesión; si falla la red, fallback local en la app.
- Presupuesto de 18 s en backend, por debajo del `readTimeout` de 25 s de la app, para no guardar turnos que el usuario no vio.
- El texto del usuario no se escribe en logs por defecto (`userTextForLog()`, logs Android solo en debug).
- En segundo plano se pausa el audio y el micrófono; solo una alerta de emergencia suena igualmente.
- `.idea/deploymentTargetSelector.xml` fuera de git (es local de cada equipo).

## 6. Estado actual
- Correcciones de criticidad alta, media y baja integradas en `main` (PR #2, #3 y #4).
- Pruebas en verde al 2026-11-03: backend 15/15, `:app:testDebugUnitTest` OK.
- Problemas conocidos: el `JAVA_HOME` global de esta máquina apunta a un JRE inexistente; `.idea/vcs.xml` aparece sin versionar (generado por Android Studio).

## 7. Próximos pasos
- Probar el flujo completo en emulador o dispositivo contra el backend con Gemini real.
- Definir la URL HTTPS de release y desplegar el backend con `API_ACCESS_TOKEN`.
- Decidir si se versiona o se ignora `.idea/vcs.xml`.

## 8. Historial
- **2026-11-03**: PR #4 (criticidad baja) fusionado: validación de `sessionId` en rutas de sesión, idioma de reconocimiento según el teléfono, pausa en segundo plano, `screen_text` de 4–7 palabras, diálogo "Abrir ajustes" para el micrófono y limpieza. Creado este `CONTEXTO.md`.
- **Anteriores**: commit inicial; PR #2 (criticidad alta) y PR #3 (criticidad media: robustez de red y voz, privacidad de logs, pruebas del controlador y CI).
