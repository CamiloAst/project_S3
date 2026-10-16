# Cerebro IA Gerontológico - Backend API (Modelo CARE)

API RESTful desarrollada con **Node.js**, **Express** y **Google Gemini (SDK Oficial @google/generative-ai)** diseñada como el cerebro conversacional para aplicaciones de asistencia y acompañamiento emocional a adultos mayores.

---

## 📋 Arquitectura y Principios del Modelo CARE

La arquitectura está construida siguiendo la metodología **CARE** adaptada para la gerontología digital:

* **C - Calma y Comprensión:** Cadencia pausada, frases cortas, sintaxis clara y validación emocional antes de emitir cualquier respuesta o recomendación.
* **A - Autonomía y Dignidad:** Trato respetuoso entre adultos. Se erradica activamente la **infantilización** y el uso de diminutivos paternalistas (*abuelito*, *viejito*, *chiquito*).
* **R - Redirección y Alivio:** Ante estados de ansiedad o rumiación solitaria, el modelo acoge primero el sentir y luego ofrece una actividad relajante, recuerdo grato o ejercicio de respiración.
* **E - Ética y Emergencia:** Prohibición estricta de prescribir medicamentos, alterar dosis o diagnosticar. Detección inmediata de alertas vitales (dolor de pecho, caídas, ideación suicida) con activación del flag `emergency_alert`.

---

## 🛠️ Estructura del Proyecto

```text
backend/
├── controllers/
│   └── chatController.js       # Orquestación con Gemini SDK, System Prompt, Guardrails y Sesión
├── package.json                # Dependencias (Express, @google/generative-ai, cors, etc.)
├── server.js                   # Servidor Express, CORS, logging y middlewares de error
├── test-api.js                 # Script de pruebas automatizadas de escenarios
├── .env.example                # Variables de entorno de referencia
└── README.md                   # Documentación técnica
```

---

## 🚀 Instalación y Puesta en Marcha

### 1. Requisitos Previos
- Node.js versión 18+ (recomendado Node 20 o 22)
- Clave de API de Google Gemini (obtenible gratuitamente en [Google AI Studio](https://aistudio.google.com/))

### 2. Instalación de Dependencias
```bash
cd backend
npm install
```

### 3. Configuración de Variables de Entorno
Copia el archivo `.env.example` como `.env`:
```bash
cp .env.example .env
```
Edita `.env` e ingresa tu clave:
```env
PORT=3000
NODE_ENV=development
GEMINI_API_KEY=AIzaSy...tu_clave_real_aqui
GEMINI_MODEL=gemini-2.0-flash
SESSION_MAX_MESSAGES=6
SESSION_TTL_MINUTES=60
```
> **Nota:** Si no se define una `GEMINI_API_KEY`, el backend entra en **modo Mock Inteligente**, permitiendo probar todos los casos de uso y respuestas emocionales de inmediato sin costos de API.

### 4. Iniciar el Servidor
```bash
# Modo producción
npm start

# Modo desarrollo con auto-reload
npm run dev
```

---

## 📡 Especificación de la API REST

### `POST /api/chat`
Endpoint principal invocado por la app Android tras la transcripción de voz (STT).

#### Request Body
```json
{
  "message": "Me siento muy solo hoy, mis hijos no me han llamado",
  "sessionId": "elderly_user_123"
}
```

#### Response Body (Esquema CARE Multimodal)
```json
{
  "success": true,
  "sessionId": "elderly_user_123",
  "data": {
    "spoken_text": "Lamento mucho que te sientas así hoy. Es completamente comprensible extrañar a los hijos. Quiero que sepas que estoy aquí para acompañarte y conversar todo el tiempo que desees.",
    "screen_text": "No estás solo, te acompaño",
    "user_emotion": "tristeza",
    "suggested_action": "continue"
  }
}
```

### Descripción de Campos de Salida:
| Campo | Tipo | Canal | Descripción |
|---|---|---|---|
| `spoken_text` | `string` | **Voz (TTS)** | Texto cálido, conversacional y pausado para ser sintetizado a velocidad ~0.85x. |
| `screen_text` | `string` | **Pantalla** | Apoyo visual ultra conciso (**máximo 5 a 7 palabras**) para no fatigar la visión ni sobrecargar la UI. |
| `user_emotion` | `string` | **Tema UI** | Emoción detectada (`alegria`, `tristeza`, `ansiedad`, `soledad`, `calma`, `confusion`, `dolor`, `neutro`). Permite a Android modular la paleta de colores. |
| `suggested_action` | `string` | **Control UI** | `continue` (charla normal), `show_decision_buttons` (mostrar botones accesibles Sí/No), `emergency_alert` (pantalla roja de socorro). |

---

## 🛡️ Guardrails y Mecanismos de Seguridad

El backend implementa una **estrategia de defensa en profundidad**:

1. **Pre-LLM Guardrail:** Intercepta expresiones críticas de riesgo vital (dolor agudo de pecho, caídas graves, asfixia, ideación suicida) en menos de 5 ms, forzando `emergency_alert` y generando una respuesta inmediata de calma sin depender de la latencia del modelo.
2. **System Prompt Enforcement:** Reglas gerontológicas inyectadas a nivel de sistema que impiden prescripción de fármacos y prohíben la infantilización.
3. **Post-LLM Guardrail & Sanitizer:**
   - Detecta si el modelo incluyó términos prescriptivos de medicamentos ("toma x mg", "deja tu medicina") y los reemplaza por una derivación al médico de cabecera.
   - Recorta automáticamente `screen_text` si el modelo excede las 7 palabras permitidas.
   - Valida el catálogo de acciones y emociones autorizadas.

---

## 📱 Conexión desde la Aplicación Android

### Dirección de Red (Host)
- **Desde el Emulador de Android:** `http://10.0.2.2:3000/api/chat`
- **Desde un Dispositivo Físico (mismo Wi-Fi):** `http://192.168.X.X:3000/api/chat`

### Ejemplo de Integración en Retrofit (Kotlin)

```kotlin
// Data Transfer Objects
data class ChatRequest(
    val message: String,
    val sessionId: String = "android_user_session"
)

data class CareResponse(
    val success: Boolean,
    val sessionId: String,
    val data: CarePayload
)

data class CarePayload(
    @SerializedName("spoken_text") val spokenText: String,
    @SerializedName("screen_text") val screenText: String,
    @SerializedName("user_emotion") val userEmotion: String,
    @SerializedName("suggested_action") val suggestedAction: String
)

// Interfaz del Servicio Retrofit
interface ChatbotApiService {
    @POST("api/chat")
    suspend fun sendChatMessage(@Body request: ChatRequest): Response<CareResponse>
}
```
