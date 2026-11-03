# Project S3 - Chatbot Asistente Gerontológico (Modelo CARE)

Aplicación móvil Android y Backend de Inteligencia Artificial enfocado en el acompañamiento conversacional, apoyo emocional y asistencia accesible para adultos mayores. La interacción multimodal (voz, texto en pantalla, botones y vibración) se diseña siguiendo el **Modelo CARE** de interacción multimodal.

---

## 👥 Resumen del Proyecto

**Project S3** es una solución integral compuesta por:
1. **Aplicación Android (Jetpack Compose & Kotlin)**: Interfaz de usuario diseñada con principios de diseño universal y accesibilidad gerontológica (altos contrastes, fuentes legibles, interacción optimizada por voz, botones táctiles amplios y pantallas de decisión simplificadas).
2. **Backend API (Node.js, Express & Google Gemini AI)**: Servidor RESTful orquestador con Google Gemini (`@google/genai`), síntesis de voz neural (`msedge-tts`), sesiones persistentes y salvaguardas (guardrails) de seguridad y ética.

---

## 🧠 Modelo CARE de Interacción Multimodal

**CARE** describe cómo se relacionan las distintas modalidades de interacción (voz, texto, botones, vibración) dentro del sistema:

| Propiedad | Significado | Cómo se aplica en Project S3 |
|---|---|---|
| **C - Complementariedad** (*Complementarity*) | Las distintas modalidades se complementan para transmitir un mensaje más rico. | La voz (`spoken_text`) entrega la respuesta completa y cálida, mientras la pantalla (`screen_text`) muestra un resumen corto de 4 a 7 palabras que **no** copia la locución. Los colores e íconos de la pantalla acompañan la emoción detectada. |
| **A - Asignación** (*Assignment*) | Cada tarea se asigna a la modalidad más eficiente. | La conversación y las explicaciones van por **voz**; las decisiones Sí/No, la navegación (Atrás / Inicio) y la alerta de emergencia van en **pantalla** con botones grandes; la confirmación de cada toque va por **vibración** (háptica). |
| **R - Redundancia** (*Redundancy*) | El mismo mensaje se envía por varios canales para evitar errores de comprensión. | Los mensajes críticos se dicen en voz alta **y** se muestran en pantalla a la vez: la pantalla de reparación ("No he entendido…") y la alerta de posible emergencia. El botón **Repetir** permite volver a escuchar la última respuesta. |
| **E - Equivalencia** (*Equivalence*) | El usuario elige libremente qué modalidad usar para una misma acción. | En la pantalla de decisión se puede responder **tocando** Sí/No o **hablando**; para volver se puede usar el botón físico de Android o el botón "← Atrás" en pantalla. |

### Pautas conversacionales gerontológicas

Además del modelo CARE, el asistente sigue estas pautas en su contenido:

- **Calma y claridad:** cadencia pausada (`~0.85x`), 2 a 3 oraciones cortas, lenguaje sencillo y validación emocional antes de sugerir algo.
- **Dignidad:** trato respetuoso de adulto a adulto, sin infantilizar ni usar diminutivos paternalistas (*abuelito*, *viejito*, etc.).
- **Ética:** prohibido diagnosticar, recetar o ajustar dosis de medicamentos (guardrail médico post-LLM).
- **Posibles emergencias:** ante expresiones de riesgo vital (dolor en el pecho, caídas, dificultad para respirar, ideación suicida) se activa `emergency_alert`.

> ⚠️ **Ambiente de pruebas:** la alerta de emergencia **solo** muestra una pantalla destacada y pide en voz y texto que el usuario busque ayuda de una persona cercana. La app **no realiza llamadas ni envía avisos** a cuidadores ni a servicios de emergencia, para evitar alertas falsas durante las pruebas.

---

## 📁 Estructura del Repositorio

```text
projectS3/
├── app/                        # Módulo de la Aplicación Android
│   ├── src/main/java/uq/seminario/projects3/
│   │   ├── audio/              # Motor de audio, STT y TTS (AudioEngine)
│   │   ├── network/            # Cliente HTTP / Retrofit (ChatApiClient)
│   │   ├── safety/             # Detector local de posibles emergencias (respaldo sin conexión)
│   │   ├── session/            # Identificador de sesión único por instalación
│   │   ├── ui/                 # Pantallas en Jetpack Compose, componentes y tema
│   │   │   ├── components/     # Encabezado de navegación, controles de lectura y diálogo de permisos
│   │   │   ├── screens/        # VoiceInput, BotResponse, Recommendation, Decision, Repair y Emergency
│   │   │   └── theme/          # Paleta accesible, tipografía y diseño
│   │   ├── viewmodel/          # ChatbotViewModel y gestión de estados UI
│   │   └── MainActivity.kt     # Punto de entrada de la aplicación
│   └── build.gradle.kts        # Dependencias de Android y Jetpack Compose
│
├── backend/                    # Servidor API REST
│   ├── controllers/            # Lógica del orquestador y llamadas a Gemini SDK (@google/genai)
│   ├── services/               # TTS neural (ttsService.js) y sesiones persistentes (sessionStore.js)
│   ├── .env.example            # Plantilla de variables de entorno
│   ├── package.json            # Dependencias de Node.js
│   ├── server.js               # Servidor Express, CORS y endpoints
│   ├── test-api.js             # Script de pruebas automatizadas
│   └── README.md               # Documentación detallada del backend
│
├── gradle/                     # Wrappers y catálogos de dependencias Gradle
├── build.gradle.kts            # Configuración raíz de Gradle
├── settings.gradle.kts         # Configuración de módulos del proyecto
└── README.md                   # Documentación general del proyecto
```

---

## 📱 Aplicación Móvil (Android)

### Características Principales
- **Jetpack Compose & Material 3**: UI moderna, fluida y adaptativa.
- **Reconocimiento y Síntesis de Voz**: Flujo manos libres con lectura en voz alta pausada (`~0.85x`) y captura por micrófono.
- **Manejo de Errores y Reparación Conversacional**: Pantalla de reparación (`RepairScreen`) amigable ante fallos de conexión o incomprensión auditiva.
- **Alerta de Posible Emergencia**: Si el backend responde `emergency_alert` (o, sin conexión, el detector local reconoce una frase de riesgo), la app muestra la pantalla `EmergencyScreen`, vibra y lee el mensaje en voz alta pidiendo buscar ayuda de una persona cercana. En esta fase de pruebas **no se realizan llamadas ni avisos automáticos**.
- **Sesión por Instalación**: Cada instalación genera un `sessionId` aleatorio (UUID) para que el historial conversacional de un usuario nunca se mezcle con el de otro dispositivo.

### Requisitos y Compilación
- **Android Studio** (Koala / Ladybug o superior)
- **JDK 11** o superior
- **Android SDK**: `minSdk 26` (Android 8.0 Oreo), `targetSdk 37`
- Para compilar desde la terminal:
  ```bash
  ./gradlew assembleDebug
  ```

---

## 🖥️ Backend (Node.js & Gemini AI)

El backend procesa los mensajes del usuario, analiza su estado emocional, aplica guardrails de seguridad y genera respuestas multimodales (voz y apoyo visual conciso).

### Instalación y Ejecución
1. Navega a la carpeta backend:
   ```bash
   cd backend
   ```
2. Instala las dependencias:
   ```bash
   npm install
   ```
3. Configura el archivo de entorno `.env`:
   ```bash
   cp .env.example .env
   ```
   Configura tu `GEMINI_API_KEY` (si se deja vacía, se activará el **modo Mock Inteligente** para pruebas locales sin costos de API).
4. Inicia el servidor:
   ```bash
   # Modo desarrollo
   npm run dev

   # Modo producción
   npm start
   ```

Consulta [backend/README.md](backend/README.md) para más detalles sobre endpoints y especificación técnica del payload JSON.

---

## 🔗 Integración y Conexión con el Backend

La URL del backend y el token de acceso se configuran en `local.properties` (no se versiona), sin tocar el código:

```properties
# Debug: por defecto http://10.0.2.2:3000/ (emulador). En un teléfono físico usa la IP de tu PC en la misma Wi-Fi.
care.apiBaseUrl.debug=http://192.168.1.50:3000/
# Release: obligatorio y solo HTTPS (el build release se detiene si falta)
care.apiBaseUrl.release=https://tu-servidor.example/
# Mismo valor que API_ACCESS_TOKEN en backend/.env
care.apiToken=pega_aqui_el_token
```

- El tráfico HTTP sin cifrar solo está permitido en builds **debug** (`app/src/debug/AndroidManifest.xml`); release exige HTTPS.
- La barra "Modo de prueba" para forzar estados y los logs completos de red solo aparecen en builds **debug**.

## 🔐 Seguridad de la API

- `API_ACCESS_TOKEN` (en `backend/.env`): token compartido que la app envía como `Authorization: Bearer <token>`. Es obligatorio con `NODE_ENV=production`; en desarrollo, si está vacío, la API acepta peticiones sin token y lo avisa en consola.
- Límite de peticiones por IP: `CHAT_RATE_LIMIT_PER_MINUTE` (20) y `SESSION_RATE_LIMIT_PER_MINUTE` (30). Detrás de un proxy (ngrok, Render, Nginx) define `TRUST_PROXY=1` para que el límite sea por cliente y no global.
- CORS desactivado salvo los orígenes declarados en `CORS_ORIGINS` (la app Android no lo necesita).
- El token viaja dentro del APK: frena el uso casual o automatizado, pero no reemplaza una autenticación real por usuario.

## ✅ Pruebas

```bash
# Backend (desde backend/)
npm test
# Android (desde la raíz)
./gradlew :app:testDebugUnitTest
```
Las frases de posible emergencia existen en el backend (`services/emergencyDetector.js`) y en la app (`safety/EmergencyDetector.kt`); ambas suites usan los mismos casos de `backend/test/emergency-cases.json`.

Las pruebas del controlador se ejecutan sin Gemini, sin voz neural (`EDGE_TTS_ENABLED=false`) y con sesiones en memoria, así que no necesitan red ni claves. GitHub Actions (`.github/workflows/ci.yml`) ejecuta ambas suites en cada pull request y en cada push a `main`.

## 🔏 Privacidad de los registros

- **Backend:** los logs muestran solo la longitud de cada mensaje (`<42 caracteres>`). Para ver el texto completo al depurar en local, usa `LOG_USER_TEXT=true`. Los errores 500 nunca devuelven detalles internos al cliente, salvo con `DEBUG_ERRORS=true`.
- **Android:** el texto dictado, los resúmenes de respuesta y los cuerpos HTTP solo se registran en builds **debug**. Si el backend rechaza el token (401), Logcat lo indica de forma explícita.
