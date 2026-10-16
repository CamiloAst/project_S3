# Project S3 - Chatbot Asistente Gerontológico (Modelo CARE)

Aplicación móvil Android y Backend de Inteligencia Artificial enfocado en el acompañamiento conversacional, apoyo emocional y asistencia accesible para adultos mayores, implementando los principios del **Modelo CARE**.

---

## 👥 Resumen del Proyecto

**Project S3** es una solución integral compuesta por:
1. **Aplicación Android (Jetpack Compose & Kotlin)**: Interfaz de usuario diseñada con principios de diseño universal y accesibilidad gerontológica (altos contrastes, fuentes legibles, interacción optimizada por voz, botones táctiles amplios y pantallas de decisión simplificadas).
2. **Backend API (Node.js, Express & Google Gemini AI)**: Servidor RESTful orquestador con Google Gemini (@google/generative-ai), provisto de un motor de prompts gerontológicos y salvaguardas (guardrails) de seguridad y ética.

---

## 🧠 Principios del Modelo CARE

La experiencia de interacción se basa en la metodología **CARE** adaptada para la gerontología digital:

- **C - Calma y Comprensión:** Cadencia pausada, frases cortas, sintaxis clara y validación emocional antes de emitir cualquier sugerencia.
- **A - Autonomía y Dignidad:** Trato respetuoso de adulto a adulto. Se erradica activamente la infantilización y el uso de diminutivos paternalistas (*abuelito*, *viejito*, etc.).
- **R - Redirección y Alivio:** En momentos de rumiación, soledad o angustia, valida el sentir y propone actividades suaves (música, recuerdos gratos, ejercicios de respiración).
- **E - Ética y Emergencia:** Prohibición estricta de formular diagnósticos o prescribir medicamentos. Detección inmediata de alertas vitales (caídas, dolor torácico, emergencias) activando el protocolo `emergency_alert`.

---

## 📁 Estructura del Repositorio

```text
projectS3/
├── app/                        # Módulo de la Aplicación Android
│   ├── src/main/java/uq/seminario/projects3/
│   │   ├── audio/              # Motor de audio, STT y TTS (AudioEngine)
│   │   ├── network/            # Cliente HTTP / Retrofit (ChatApiClient)
│   │   ├── ui/                 # Pantallas en Jetpack Compose, componentes y tema
│   │   │   ├── components/     # Diálogos de permisos y componentes accesibles
│   │   │   ├── screens/        # VoiceInputScreen, BotResponseScreen, DecisionScreen, RepairScreen
│   │   │   └── theme/          # Paleta accesible, tipografía y diseño
│   │   ├── viewmodel/          # ChatbotViewModel y gestión de estados UI
│   │   └── MainActivity.kt     # Punto de entrada de la aplicación
│   └── build.gradle.kts        # Dependencias de Android y Jetpack Compose
│
├── backend/                    # Servidor API REST
│   ├── controllers/            # Lógica del orquestador y llamadas a Gemini SDK
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
- **Detección de Emergencias**: Si el backend detecta una alerta crítica, la interfaz entra en modo de alerta destacada para facilitar auxilio.

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

## 🔗 Integración y Conexión Local

Para comunicar la app Android con el backend local:
- **En Emulador Android:** La app apunta a `http://10.0.2.2:3000/api/chat`.
- **En Dispositivo Físico:** Configura la IP local de tu máquina de desarrollo (ejemplo: `http://192.168.1.X:3000/api/chat`) en `ChatApiClient.kt`.
