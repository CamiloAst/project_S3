# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Voice companion assistant for older adults (Spanish-language UI and content). Two parts in one repo:
- `app/`: Android app (Kotlin, Jetpack Compose, single `app` module, package `uq.seminario.projects3`).
- `backend/`: Node.js/Express API that orchestrates Google Gemini (`@google/genai`) and Edge neural TTS (`msedge-tts`).

All user-facing text, prompts, comments and docs are written in Spanish; keep it that way.

### Modelo CARE (meaning matters)

CARE here is the **multimodal interaction model**, not a list of conversational virtues:
- **Complementariedad**: modalities complement each other (`spoken_text` = full spoken answer, `screen_text` = short 4–7 word summary that must never copy it).
- **Asignación**: each task goes to the most efficient modality (conversation by voice; Sí/No decisions, navigation and alerts on screen; touch confirmation by vibration).
- **Redundancia**: critical messages go through several channels at once (e.g. emergency/repair messages are spoken and shown).
- **Equivalencia**: the user can perform the same action through different modalities (tap Sí/No or answer by voice; hardware back or on-screen "Atrás").

Tone rules (calm, no infantilizing diminutives, no prescribing medication) are separate "pautas conversacionales", not CARE letters.

## Commands

### Android (run from repo root)
```bash
./gradlew assembleDebug                 # build APK
./gradlew :app:compileDebugKotlin       # fast compile check
./gradlew :app:testDebugUnitTest        # JVM unit tests
./gradlew :app:testDebugUnitTest --tests "uq.seminario.projects3.safety.EmergencyDetectorTest"   # single test class
```
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
node test-api.js          # manual scenario script; needs the server already running, no assertions
```
`SESSION_STORE=memory` avoids writing `backend/data/sessions.json` during experiments. There is no automated backend test suite or linter.

## Architecture

### Request flow
1. `AudioEngine` (Android SpeechRecognizer) transcribes speech → `ChatbotViewModel.handleSpeechRecognitionResult`.
2. `ChatApiClient` (Retrofit) `POST /api/chat` with `{ message, sessionId }`. Base URL is hardcoded to the emulator host `http://10.0.2.2:3000/`.
3. `backend/controllers/chatController.js` `handleChat`:
   - pre-LLM emergency regex (`CRITICAL_EMERGENCY_REGEX`) → short-circuits to `source: "emergency_guardrail"`;
   - otherwise Gemini with structured JSON output (`careResponseSchema`) and the system prompt `GERONTOLOGICAL_SYSTEM_PROMPT`; Gemini also classifies `user_emotion` (5 fixed categories);
   - on any Gemini failure/timeout → keyword detector + `RESPONSE_VARIATIONS_BANK` with per-session anti-repetition (`source: "fallback_bank"`, `fallback_reason` set);
   - `sanitizeAndEnforceGuardrails` post-processes every answer (medical-prescription filter, screen_text length, enum validation);
   - session history persisted via `services/sessionStore.js` (`file` | `redis` | `memory`);
   - `services/ttsService.js` synthesizes MP3 with emotion-based prosody; it fails fast and returns `audio_base64: null` so the app falls back to local TTS.
4. `ChatbotViewModel.executeMasterClockSync` plays the neural MP3 (MediaPlayer) or local TTS and maps `suggested_action` to a `UiState`:
   `continue`/`clarify` → `Speaking` (affective emotions render `RecommendationScreen`, others `BotResponseScreen`), `show_decision_buttons` → `AwaitingDecision`, `emergency_alert` → `Emergency`, `retry` → `Repair`.
5. If the HTTP call fails, `generateLocalFallbackChatData` builds a local response (emergency check first).

### API contract
Response `data` fields: `spoken_text`, `screen_text`, `user_emotion` (`POSITIVA`, `NEGATIVA_TRISTEZA_SOLEDAD`, `NEGATIVA_ANSIEDAD_PREOCUPACION`, `NEUTRA`, `AMBIGUA`), `suggested_action` (`continue`, `show_decision_buttons`, `clarify`, `emergency_alert`), `audio_base64`, `audio_duration_ms`. Changing a field or enum requires updating the Gemini schema, the fallback bank, `ChatData` in `ChatApiClient.kt` and the ViewModel mapping together.

### Things that must stay in sync
- **Emergency phrases**: `CRITICAL_EMERGENCY_REGEX` in `chatController.js` and `safety/EmergencyDetector.kt` (Kotlin version matches on accent-stripped lowercase text).
- **Emergency messages**: `EMERGENCY_SPOKEN_TEXT` / `EMERGENCY_SCREEN_TEXT` in `chatController.js` and `UiState.EMERGENCY_*` in `ChatbotViewModel.kt`.
- **Test environment rule**: the emergency flow only shows `EmergencyScreen` and asks the user to seek help from someone nearby. Do **not** add phone numbers, dialing, SMS or caregiver notifications, and never let the prompt or texts claim the app will call/notify anyone.

### Sessions
`sessionId` is required by the backend (400 if missing; regex `^[\w\-.:]+$`, max 128). The app generates one per install in `session/SessionIdProvider.kt` (`android_<UUID>` in SharedPreferences `care_session`, excluded from backups in `res/xml/backup_rules.xml` and `data_extraction_rules.xml`). Never reintroduce a shared default session id.

### UI navigation
`ChatbotViewModel` keeps its own back stack (`stateHistory: ArrayDeque<UiState>`); `ChatbotApp.kt` renders one screen per `UiState` inside `AnimatedContent` and wires `BackHandler` to `navigateBack()`. `UiState` is a sealed interface, so adding a state requires handling it in `ChatbotApp`'s `when`, `onMicClicked`, `navigateBack` and `resolveCurrentSpokenText`. The bottom `DemoStateSwitcherBar` in `ChatbotApp.kt` is a test-mode tool that forces each state (including Emergency).
