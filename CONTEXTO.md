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
  - `ui/ChatbotApp.kt`, `ui/screens/*`, `ui/components/*`: pantallas Compose (incluida `ThinkingScreen`, espera mientras la IA responde).
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
- Pruebas de UI en emulador (no corren en CI): `./gradlew :app:connectedDebugAndroidTest` (incluye `ThinkingScreenTest`).
- Configuración app ↔ backend en `local.properties`: `care.apiBaseUrl.debug`, `care.apiBaseUrl.release` (HTTPS obligatorio), `care.apiToken` (igual a `API_ACCESS_TOKEN`).
- Android 17+ (`targetSdk = 37`): la app debug pide al abrirse el permiso «Dispositivos cercanos / red local» (`ACCESS_LOCAL_NETWORK`). Si se deniega, no llega al backend (ni siquiera a `10.0.2.2`) y todas las respuestas salen del respaldo local. Alternativa sin permiso: `adb reverse tcp:3000 tcp:3000` y `care.apiBaseUrl.debug=http://127.0.0.1:3000/`.
- Comprobar que responde Gemini: cada mensaje que llega deja en la consola del backend `POST /api/chat`, `⚡ [Gemini LLM Latency]` y `🎯 [Gemini Emotion]` (o `🛡️ [Fallback Activado]`); `GET /api/health` muestra `aiProvider.stats.gemini`, `stats.retries`, `lastSuccessAt` y `lastError`. Los reintentos aparecen como `🔁 [Gemini Retry]`. En logcat (`ChatbotViewModel`), «Respuesta CARE recibida» = backend; «Aplicando respaldo CARE local» = no llegó. Consola del backend en silencio = la petición no llegó.

## 5. Decisiones
- Respuesta con `spoken_text` completo y `screen_text` de 4 a 7 palabras que no lo copia (Complementariedad).
- Chequeo de emergencia antes del LLM, en backend y app, con los mismos casos de prueba compartidos.
- Si Gemini falla o tarda, banco de respuestas con anti-repetición por sesión; si falla la red, fallback local en la app.
- Proyecto universitario: todo debe quedarse en niveles gratuitos. Ante la saturación de Gemini (503) se reintenta el mismo modelo (`GEMINI_MAX_RETRIES=2`, espera exponencial, dentro de los 8 s) en vez de cambiar de modelo o añadir otro proveedor de pago. Al 2026-11-03, los modelos más nuevos (`gemini-3.8-flash`, `gemini-flash-latest`) estaban más saturados, los «lite» tardaban más de 20 s y `gemini-2.5-flash` ya no está disponible para cuentas nuevas.
- Presupuesto de 18 s en backend, por debajo del `readTimeout` de 25 s de la app, para no guardar turnos que el usuario no vio.
- El texto del usuario no se escribe en logs por defecto (`userTextForLog()`, logs Android solo en debug).
- En segundo plano se pausa el audio y el micrófono; solo una alerta de emergencia suena igualmente.
- `.idea/deploymentTargetSelector.xml` fuera de git (es local de cada equipo).
- Pantalla "Pensando" (`UiState.Thinking`) desde el fin de la voz hasta la respuesta: solo en pantalla, sin voz (Asignación), para no solaparse con la respuesta; a los 8 s cambia el mensaje para tranquilizar. Es transitoria como `Listening` (no entra en el historial de "Atrás").
- El icono de escucha (barras + halo) sigue el volumen real de la voz (`onRmsChanged` → `voiceLevel`), leído en fase de dibujo para no recomponer la app.
- Sin barra de estados de prueba: se quitó para las pruebas con usuarios reales.
- `ACCESS_LOCAL_NETWORK` solo en el manifest de debug y pedido en `MainActivity` solo en debug con Android 17+: el backend de desarrollo vive en la red local; release usa una URL HTTPS pública y no lo necesita.

## 6. Estado actual
- Correcciones de criticidad alta, media y baja integradas en `main` (PR #2, #3 y #4).
- App preparada para pruebas con usuarios reales (rama `feat/pruebas-usuarios`): sin barra de prueba, con pantalla "Pensando" e icono de escucha reactivo a la voz.
- Pruebas en verde al 2026-11-03: backend 19/19, `:app:testDebugUnitTest` 10/10, `ThinkingScreenTest` 3/3 en emulador.
- El reconocimiento de voz ya funciona en el emulador (Android 17). Hasta este arreglo, la app nunca llegaba al backend (Android 17 bloqueaba `10.0.2.2`) y todas las respuestas eran del respaldo local. Con el permiso concedido, la conexión a `10.0.2.2:3000` funciona; falta ver una conversación completa respondida por Gemini.
- Problemas conocidos: el `JAVA_HOME` global de esta máquina apunta a un JRE inexistente; `.idea/vcs.xml` aparece sin versionar (generado por Android Studio).

## 7. Próximos pasos
- Probar el flujo completo en un dispositivo físico contra el backend con Gemini real (voz real: animación del icono y pantalla "Pensando").
- Definir la URL HTTPS de release y desplegar el backend con `API_ACCESS_TOKEN`.
- Decidir si se versiona o se ignora `.idea/vcs.xml`.

## 8. Historial
- **2026-11-03 (4)**: Gemini devolvía 503 («high demand») en varios turnos. Se añaden reintentos con espera exponencial ante 5xx (`withGeminiRetries`, `GEMINI_MAX_RETRIES`) dentro del mismo plazo, con 4 pruebas nuevas. Se descartó cambiar de proveedor para no salir del nivel gratuito.
- **2026-11-03 (3)**: diagnóstico de «el backend no muestra nada»: Android 17 (protección de red local) descartaba las conexiones de la app a `10.0.2.2` y la app respondía con su respaldo local. Se declara `ACCESS_LOCAL_NETWORK` en el manifest de debug y `MainActivity` lo pide en debug con Android 17+.
- **2026-11-03 (2)**: preparación para pruebas con usuarios: eliminada la barra de estados de prueba (y sus funciones de demostración en el ViewModel), nueva pantalla `ThinkingScreen` (`UiState.Thinking`), icono de escucha con barras y halo que siguen el volumen de la voz, y se descartan resultados/errores tardíos del reconocedor. Pruebas: `VoiceLevelTest` (unitaria) y `ThinkingScreenTest` (UI).
- **2026-11-03**: PR #4 (criticidad baja) fusionado: validación de `sessionId` en rutas de sesión, idioma de reconocimiento según el teléfono, pausa en segundo plano, `screen_text` de 4–7 palabras, diálogo "Abrir ajustes" para el micrófono y limpieza. Creado este `CONTEXTO.md`.
- **Anteriores**: commit inicial; PR #2 (criticidad alta) y PR #3 (criticidad media: robustez de red y voz, privacidad de logs, pruebas del controlador y CI).
