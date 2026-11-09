# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Voice companion assistant for older adults (Spanish-language UI and content). Two parts in one repo:
- `app/`: Android app (Kotlin, Jetpack Compose, single `app` module, package `uq.seminario.projects3`).
- `backend/`: Node.js/Express API that orchestrates Google Gemini (`@google/genai`) and Edge neural TTS (`msedge-tts`).

All user-facing text, prompts, comments and docs are written in Spanish; keep it that way.

It is a university project that must stay on free tiers: do not add paid LLM providers or features that require enabling billing.

### Modelo CARE (meaning matters)

CARE here is the **multimodal interaction model**, not a list of conversational virtues:
- **Complementariedad**: modalities complement each other (`spoken_text` = full spoken answer, `screen_text` = short 4–7 word summary that must never copy it, `image_key` = illustration of the conversation topic from the app's local image bank).
- **Asignación**: each task goes to the most efficient modality (conversation by voice; Sí/No decisions, navigation and alerts on screen; touch confirmation by vibration). The image is visual support only: never the sole carrier of information or an action.
- **Redundancia**: critical messages go through several channels at once (e.g. emergency/repair messages are spoken and shown; the emergency screen always shows the fixed `ayuda_cercana` image).
- **Equivalencia**: the user can perform the same action through different modalities (tap Sí/No or answer by voice; hardware back or on-screen "Atrás"). Each image has a TalkBack description ("Imagen: …").

Tone rules (calm, no infantilizing diminutives, no prescribing medication) are separate "pautas conversacionales", not CARE letters.

## Commands

### Android (run from repo root)
```bash
./gradlew assembleDebug                 # build APK
./gradlew :app:compileDebugKotlin       # fast compile check
./gradlew :app:testDebugUnitTest        # JVM unit tests
./gradlew :app:testDebugUnitTest --tests "uq.seminario.projects3.safety.EmergencyDetectorTest"   # single test class
```
Release builds stop at `preReleaseBuild` unless `care.apiBaseUrl.release=https://...` is set (in `local.properties` or `-P`).

On this Windows machine the global `JAVA_HOME` points to a missing JRE and a stale Gradle daemon may use a JRE without `jlink`. Use Android Studio's JDK (PowerShell):
```powershell
$env:JAVA_HOME = "E:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --stop
.\gradlew.bat :app:testDebugUnitTest "-Dorg.gradle.java.home=E:/Program Files/Android/Android Studio/jbr"
```

### Backend (from `backend/`)
```bash
npm install
cp .env.example .env      # set GEMINI_API_KEY; without it the API answers from the fallback bank
npm run dev               # node --watch server.js (port 3000)
npm test                  # node:test suite in backend/test/ (controller, emergency detector, image catalog, security middleware, TTS)
node --test test/security.test.js   # single test file
node test-api.js          # manual scenario script; needs the server running, no assertions
```
`SESSION_STORE=memory` avoids writing `backend/data/sessions.json` during experiments, and `EDGE_TTS_ENABLED=false` disables the network TTS (the controller tests set both and an empty `GEMINI_API_KEY` before requiring the controller, because modules read env at load time). There is no linter. Never read or print `backend/.env` (it holds the real Gemini key).

CI (`.github/workflows/ci.yml`) runs `npm test` and `:app:testDebugUnitTest` on every PR; it uses JDK 25 to match `gradle/gradle-daemon-jvm.properties`.

User text must not reach logs by default: backend logs go through `userTextForLog()` (full text only with `LOG_USER_TEXT=true`), and Android logs containing conversation content are wrapped in `if (BuildConfig.DEBUG)`.

### App ↔ backend configuration
Android reads, from `local.properties` (not versioned) or `-P`: `care.apiBaseUrl.debug` (default `http://10.0.2.2:3000/`), `care.apiBaseUrl.release` (must be HTTPS) and `care.apiToken`, exposed as `BuildConfig.CARE_API_BASE_URL` / `CARE_API_TOKEN`. `care.apiToken` must equal the backend's `API_ACCESS_TOKEN` (required when `NODE_ENV=production`); the app sends it as `Authorization: Bearer`. Cleartext HTTP is allowed only by `app/src/debug/AndroidManifest.xml`. Full HTTP body logging exists only in debug builds.

Android 17+ (local network protection, `targetSdk = 37`) silently drops connections to private addresses, including the emulator's `10.0.2.2`, unless the app holds `ACCESS_LOCAL_NETWORK`. The debug manifest declares it and `MainActivity` requests it at launch (debug builds on API 37+ only). If it is denied, every request times out and the app answers from `generateLocalFallbackChatData` while the backend logs nothing. A silent backend console means the request never arrived.

## Architecture

### Request flow
1. `AudioEngine` (Android SpeechRecognizer) transcribes speech → `ChatbotViewModel.handleSpeechRecognitionResult`. While listening, `onRmsChanged` feeds `voiceLevel` (0–1, `AudioEngine.normalizeRmsDb`) that animates the mic icon's bars and halo; end of speech (or tapping the mic) switches to `UiState.Thinking` (`ThinkingScreen`) until the answer arrives. Recognizer results/errors that arrive outside `Listening`/`Thinking` are ignored.
2. `ChatApiClient` (Retrofit) `POST /api/chat` with `{ message, sessionId, companion }` and the bearer token. `server.js` applies a per-IP rate limit and `requireApiToken` (`middleware/security.js`) before the controller.
3. `backend/controllers/chatController.js` `handleChat`:
   - pre-LLM emergency check (`isCriticalRisk` from `services/emergencyDetector.js`) → short-circuits to `source: "emergency_guardrail"`;
   - otherwise Gemini with structured JSON output (`careResponseSchema`) and the system prompt `GERONTOLOGICAL_SYSTEM_PROMPT`; Gemini also classifies `user_emotion` (5 fixed categories) and picks `image_key` from an enum (`SELECTABLE_IMAGE_KEYS`, a few output tokens; no image is generated or sent);
   - Gemini 5xx errors (e.g. 503 "high demand") are first retried with exponential backoff by `withGeminiRetries` (`GEMINI_MAX_RETRIES`, default 2) within the same `GEMINI_TIMEOUT_MS` deadline; 429 (free-tier quota) and timeouts are not retried;
   - on any remaining Gemini failure/timeout → keyword detector + `RESPONSE_VARIATIONS_BANK` with per-session anti-repetition (`source: "fallback_bank"`, `fallback_reason` set);
   - `sanitizeAndEnforceGuardrails` post-processes every answer (medical-prescription filter, screen_text length, enum validation) and fixes the image with `resolveImageKey` (`services/imageCatalog.js`): `emergency_alert` → `ayuda_cercana` (reserved, Gemini cannot pick it); invalid/missing key → topic keywords of the user's message (`detectImageTopic`, also used by the fallback bank, ahead of each bank variation's own `image_key`) → emotion default (`DEFAULT_IMAGE_BY_EMOTION`); `familia`/`alegria` are replaced in sad or anxious turns (`IMAGES_AVOIDED_BY_EMOTION`); the medical guardrail sets `salud`;
   - session history persisted via `services/sessionStore.js` (`file` | `redis` | `memory`); Gemini receives the last `SESSION_MAX_MESSAGES` messages (default 20 = 10 exchanges, forced even) plus `session.memory`, short notes about the user that Gemini rewrites every turn in the internal schema field `conversation_memory` (`mergeConversationMemory` keeps the previous notes if it comes back empty, caps them at 600 chars; `handleChat` strips the field, so it is not part of the API contract). Sessions (history and memory) expire after `SESSION_TTL_MINUTES` (60) of inactivity;
   - `services/ttsService.js` synthesizes MP3 with emotion-based prosody and the companion's voice (`VOICE_PROFILES`: `female` → `es-CO-SalomeNeural`, `male` → `es-CO-GonzaloNeural`, one Edge client per voice, 96 kbps MP3). Edge closes idle WebSockets after 20–60 s and sometimes connects slowly or cuts the stream, so `handleChat` calls `prewarmTts` on arrival (reconnects while Gemini runs) and each synthesis gets up to `TTS_MAX_ATTEMPTS` (2) attempts with per-attempt timeouts, all bounded by the deadline `requestStartedAt + CHAT_RESPONSE_BUDGET_MS` (18 s). Only when every attempt fails (or less than `TTS_MIN_ATTEMPT_MS` remains) does it return `audio_base64: null` and the app falls back to its (more robotic) local TTS. `GET /api/health` → `tts.stats` counts neural audios, retries, failures and skips.
   - Timeouts are coupled: the backend budget must stay below the app's `readTimeout` (25 s in `ChatApiClient.kt`); otherwise the app shows its local fallback while the backend already saved a different turn in the session history.
4. `ChatbotViewModel.executeMasterClockSync` plays the neural MP3 (MediaPlayer) or local TTS, resolves the illustration with `ContextImage.resolve(image_key, emotion, action)` (stored in `Speaking`/`AwaitingDecision.image`, rendered by `ui/components/ContextImageCard.kt`) and maps `suggested_action` to a `UiState`:
   `continue`/`clarify` → `Speaking` (affective emotions render `RecommendationScreen`, others `BotResponseScreen`), `show_decision_buttons` → `AwaitingDecision`, `emergency_alert` → `Emergency`, `retry` → `Repair`.
5. If the HTTP call fails, `generateLocalFallbackChatData` builds a local response (emergency check first).

### API contract
Request: `message`, `sessionId` and optional `companion` (`"female"` | `"male"`; missing or unknown → `"female"`, never a 400). `companion` picks the neural voice and is injected into the turn prompt (`COMPANION_PERSONA` in `buildTurnPrompt`) so Gemini uses that grammatical gender for itself.
Response `data` fields: `spoken_text`, `screen_text`, `user_emotion` (`POSITIVA`, `NEGATIVA_TRISTEZA_SOLEDAD`, `NEGATIVA_ANSIEDAD_PREOCUPACION`, `NEUTRA`, `AMBIGUA`), `suggested_action` (`continue`, `show_decision_buttons`, `clarify`, `emergency_alert`), `image_key` (key of the local image bank, see below), `audio_base64`, `audio_duration_ms`. The app tolerates a missing or unknown `image_key` (older/newer backend): it shows the emotion's default image. Changing a field or enum requires updating the Gemini schema, the fallback bank, `ChatDataDto` + `toChatResponse()` + `ChatData` in `ChatApiClient.kt` and the ViewModel mapping together. Gson ignores Kotlin nullability, so Retrofit always parses into the all-nullable `ChatResponseDto` and `toChatResponse()` converts it (missing texts become `""`, unusable responses become a failure that triggers the local fallback).

### Things that must stay in sync
- **Emergency phrases**: `EMERGENCY_PATTERNS` in `backend/services/emergencyDetector.js` and in `app/.../safety/EmergencyDetector.kt` (both match on lowercase, accent- and punctuation-stripped text). Both test suites read the same cases from `backend/test/emergency-cases.json`; add a case there for every new phrase and run `npm test` and `:app:testDebugUnitTest`.
- **Emergency messages**: `EMERGENCY_SPOKEN_TEXT` / `EMERGENCY_SCREEN_TEXT` in `chatController.js` and `UiState.EMERGENCY_*` in `ChatbotViewModel.kt`.
- **Image bank keys**: `IMAGE_CATALOG` / `DEFAULT_IMAGE_BY_EMOTION` in `backend/services/imageCatalog.js`, the `ContextImage` enum (`app/.../images/ContextImage.kt`, `apiValue` + `defaultFor`) and the drawables `res/drawable/img_<key>.xml`. Both test suites read `backend/test/image-keys.json`; adding or removing an image means updating all of them (and the bank's `image_key` values). The illustrations are original vector drawables; one can be replaced by any image with the same resource name. Keys must be valid resource names (`[a-z_]`).
- **Test environment rule**: the emergency flow only shows `EmergencyScreen` and asks the user to seek help from someone nearby. Do **not** add phone numbers, dialing, SMS or caregiver notifications, and never let the prompt or texts claim the app will call/notify anyone.

### Sessions
`sessionId` is required by the backend (400 if missing; regex `^[\w\-.:]+$`, max 128). The app generates one per install in `session/SessionIdProvider.kt` (`android_<UUID>` in SharedPreferences `care_session`, excluded from backups in `res/xml/backup_rules.xml` and `data_extraction_rules.xml`). Never reintroduce a shared default session id.

### Lifecycle
`ChatbotApp` observes the lifecycle: ON_STOP (ignored during configuration changes) calls `onAppBackgrounded()`, which cancels listening and pauses audio; ON_START calls `onAppForegrounded()` and re-reads the mic permission. While in background, async responses start paused (`startsPaused()`, `speakIfForeground()`); only `emergency_alert` plays anyway (`playInBackground = true`).

### UI navigation
`ChatbotViewModel` keeps its own back stack (`stateHistory: ArrayDeque<UiState>`); `ChatbotApp.kt` renders one screen per `UiState` inside `AnimatedContent` and wires `BackHandler` to `navigateBack()`. `UiState` is a sealed interface, so adding a state requires handling it in `ChatbotApp`'s `when`, `onMicClicked`, `navigateBack` and `resolveCurrentSpokenText`. `Listening` and `Thinking` are transient: `transitionToState` never pushes them to `stateHistory`. There is no on-screen state switcher (removed for tests with real users); `ThinkingScreenTest`, `AvatarSelectionScreenTest` and `VoiceInputScreenTest` (androidTest) cover those screens; `ContextImageCardTest` renders every illustration and checks the image on the response, decision and emergency screens.

### Companion avatar
`UiState.ChoosingAvatar` (`AvatarSelectionScreen`) is the start state while `AvatarPreferences` (SharedPreferences `care_avatar`) holds no choice, and reopens from "Cambiar acompañante" on Idle. On the first choice there is no way back (`BackHandler` disabled, `navigateBack` ignores it). `ChatbotViewModel.avatar` feeds every screen an `AssistantAvatar` (`avatar` parameter, default `AssistantAvatar.DEFAULT`) rendered with `AssistantAvatarBadge`, which shows waves while the voice plays. The enum `name` is the stored value: do not rename constants without migrating it. `AssistantAvatar.apiValue` is sent as `companion` and must match the `VOICE_PROFILES` keys in `ttsService.js`; `localTtsPitch` lowers the local fallback TTS pitch for the male companion (Android does not expose voice gender). Fixed texts (fallback bank, local fallback, `UiState` defaults) avoid gendered self-references ("listo", "atento") so they fit both companions; a backend test enforces it for the bank.
