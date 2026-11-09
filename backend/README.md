# Cerebro IA Gerontológico - Backend API (Modelo CARE)

API RESTful desarrollada con **Node.js**, **Express**, **Google Gemini (SDK oficial `@google/genai`)** y **Microsoft Edge Neural TTS (`msedge-tts`)** diseñada como el cerebro conversacional para aplicaciones de asistencia y acompañamiento emocional a adultos mayores.

---

## 📋 Modelo CARE de Interacción Multimodal

La respuesta de la API está pensada para que la app Android combine sus modalidades según el **Modelo CARE**:

* **C - Complementariedad:** `spoken_text` (voz) y `screen_text` (pantalla) se complementan: la voz da la respuesta completa y la pantalla un resumen corto de 4 a 7 palabras que nunca es copia literal. `image_key` añade una ilustración del tema de la conversación tomada del banco local de la app.
* **A - Asignación:** cada tarea va a la modalidad más eficiente; `suggested_action` indica a la app cuándo pasar la interacción a la pantalla (botones Sí/No, aclaración, alerta).
* **R - Redundancia:** los mensajes críticos (p. ej. `emergency_alert`) se entregan a la vez en voz, en pantalla y con una imagen fija (`ayuda_cercana`) para evitar errores de comprensión.
* **E - Equivalencia:** el usuario puede responder por voz o tocando la pantalla; el backend recibe el mismo texto sin importar el canal.

### Pautas conversacionales del asistente

* **Calma y claridad:** frases cortas, tono pausado y validación emocional antes de sugerir.
* **Dignidad:** trato entre adultos, sin **infantilización** ni diminutivos paternalistas (*abuelito*, *viejito*, *chiquito*).
* **Ética:** prohibido prescribir medicamentos, alterar dosis o diagnosticar.
* **Posibles emergencias:** las expresiones de riesgo vital (dolor de pecho, caídas, ideación suicida) activan `emergency_alert`. El mensaje pide buscar ayuda de una persona cercana y **no promete** llamar ni avisar a nadie: en el ambiente de pruebas no existe ninguna integración con cuidadores ni servicios de emergencia.

---

## 🛠️ Estructura del Proyecto

```text
backend/
├── controllers/
│   └── chatController.js       # Orquestación con @google/genai, System Prompt, Guardrails y Banco CARE
├── middleware/
│   └── security.js             # Token de acceso, límite de peticiones por IP y CORS
├── services/
│   ├── emergencyDetector.js    # Frases de posible emergencia (replicadas en la app Android)
│   ├── sessionStore.js         # Persistencia de sesiones (archivo JSON atómico, Redis o memoria)
│   └── ttsService.js           # Síntesis de voz neural (msedge-tts) con modulación afectiva y fail-fast
├── test/                       # Pruebas automáticas (npm test) y casos compartidos con Android
├── package.json                # Dependencias (Express, @google/genai, msedge-tts, cors, etc.)
├── server.js                   # Servidor Express, CORS, healthcheck y cierre ordenado
├── test-api.js                 # Script de pruebas automatizadas de escenarios CARE
├── postman_collection.json     # Colección Postman para pruebas manuales
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
GEMINI_MODEL=gemini-3.5-flash
GEMINI_TIMEOUT_MS=8000
SESSION_STORE=file
SESSION_FILE_PATH=./data/sessions.json
SESSION_MAX_MESSAGES=20
SESSION_TTL_MINUTES=60
```
> **Nota:** Si no se define una `GEMINI_API_KEY`, el backend entra en **modo Banco CARE de Respaldo**, permitiendo probar todos los casos de uso y respuestas emocionales de inmediato sin costos de API.

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

#### Autenticación y límites
Si `API_ACCESS_TOKEN` está definido (obligatorio en producción), todas las rutas `/api/chat*` exigen `Authorization: Bearer <token>` o `X-API-Key: <token>`; sin él responden `401`. Al superar el límite por minuto responden `429`. `GET /api/health` es público e indica `authRequired`.

#### Request Body
`sessionId` es **obligatorio** (1-128 caracteres: letras, números, `_`, `-`, `.` o `:`). La app Android envía un identificador único por instalación; si falta, la API responde `400`.
`companion` es **opcional**: el acompañante elegido en la app, `"female"` (compañera, voz `es-CO-SalomeNeural`) o `"male"` (compañero, voz `es-CO-GonzaloNeural`). Fija la voz neural y el género con el que el asistente habla de sí. Si falta o no es válido se usa `"female"`.
```json
{
  "message": "Me siento muy solo hoy, mis hijos no me han llamado",
  "sessionId": "elderly_user_123",
  "companion": "female"
}
```

#### Response Body (Esquema CARE Multimodal)
```json
{
  "success": true,
  "sessionId": "elderly_user_123",
  "source": "gemini",
  "data": {
    "user_emotion": "NEGATIVA_TRISTEZA_SOLEDAD",
    "spoken_text": "Lamento mucho que hoy se sienta tan silencioso el día y extrañes la llamada de tus hijos. Quiero que sepas que estoy aquí contigo para acompañarte con toda calma. ¿Te gustaría que platiquemos de algún recuerdo bonito junto a ellos?",
    "screen_text": "Acompañamiento cálido en todo momento",
    "suggested_action": "continue",
    "image_key": "acompanamiento",
    "audio_base64": "//NExAAAAANIAAAAAExBTUUzLjEwMKqqqqqq...",
    "audio_duration_ms": 13450
  }
}
```

### Descripción de Campos de Salida (`data`):
| Campo | Tipo | Canal | Descripción |
|---|---|---|---|
| `spoken_text` | `string` | **Voz (TTS)** | Texto cálido, conversacional y pausado (2–3 oraciones) para ser leído en voz alta. |
| `screen_text` | `string` | **Pantalla** | Resumen o título complementario corto (**4 a 7 palabras**, nunca copia literal de `spoken_text`). |
| `user_emotion` | `string` | **Emoción / Prosodia** | Categoría emocional clasificada por Gemini (o por el detector de respaldo): `POSITIVA`, `NEGATIVA_TRISTEZA_SOLEDAD`, `NEGATIVA_ANSIEDAD_PREOCUPACION`, `NEUTRA`, `AMBIGUA`. Modula la prosodia del TTS y la UI en Android. |
| `suggested_action` | `string` | **Control UI** | `continue` (charla normal), `show_decision_buttons` (botones accesibles Sí/No), `clarify` (pedir aclaración amable), `emergency_alert` (pantalla de alerta de posible emergencia; sin llamadas ni avisos en el ambiente de pruebas). |
| `image_key` | `string` | **Imagen (pantalla)** | Clave de la ilustración del banco local de la app (`res/drawable/img_<clave>.xml`); no viaja ninguna imagen. Gemini la elige de un `enum` (`salud`, `agenda`, `familia`, `mascotas`, `musica`, `lectura`, `comida`, `naturaleza`, `espiritualidad`, `descanso`, `clima`, `recuerdos`, `alegria`, `calma`, `acompanamiento`, `escucha`, `saludo`); sin Gemini sale del tema del mensaje o de la emoción. `ayuda_cercana` está reservada a `emergency_alert`. Con tristeza o preocupación nunca se usan `familia` ni `alegria`. Ver `services/imageCatalog.js`. |
| `audio_base64` | `string \| null` | **Audio Neural** | MP3 (24 kHz, 48 kbps mono) codificado en Base64 generado con `msedge-tts` (voz según `companion`), o `null` si aplica fallback al TTS local de Android. |
| `audio_duration_ms` | `number` | **Reloj Maestro** | Duración estimada/exacta del audio en milisegundos para sincronizar la interfaz. |

En la raíz de la respuesta también se incluye `source` (`"gemini"`, `"fallback_bank"` o `"emergency_guardrail"`) y, cuando aplica el banco por fallo de red/API, `fallback_reason` (`"timeout"`, `"quota_exceeded"`, `"service_unavailable"`, etc.).

---

## 🛡️ Clasificación Emocional, Guardrails y Persistencia

1. **Pre-LLM Emergency Guardrail (< 5 ms):** `services/emergencyDetector.js` intercepta frases de posible riesgo vital (dolor en el pecho, posible infarto, caídas, dificultad para respirar, signos de ACV, desmayo, sangrado, ideación suicida) sobre el texto normalizado, antes de llamar al LLM, forzando `emergency_alert`. Se usan frases explícitas para evitar falsos positivos como *"mi vecino tuvo un infarto"*.
2. **Clasificación Emocional con Gemini (Opción A):** Gemini clasifica la emoción del último mensaje en una de las 5 categorías (`user_emotion` con `enum` estricto en Structured Outputs) atendiendo negaciones y contexto (*"no me siento bien"*, *"buenas tardes"*, *"solo quería saludarte"*), y adapta la respuesta según la guía CARE correspondiente.
3. **Banco de Respaldo y Anti-Repetición:** Si Gemini supera el timeout o no está disponible, se registra el motivo exacto del fallo y entra el detector por palabras clave + `RESPONSE_VARIATIONS_BANK` (6 variaciones por categoría sin repetición por sesión).
4. **Post-LLM Medical Guardrail:** Detecta indicaciones de dosis o prescripción médica con límites de palabra (`\b`) sin falsos positivos en frases cotidianas como *"¿tomamos un respiro?"* o *"toma un vaso con agua"*.
5. **Sesiones Persistentes (`services/sessionStore.js`):**
   - `SESSION_STORE=file` (por defecto): guarda las sesiones en `data/sessions.json` con escritura atómica para sobrevivir a reinicios del servidor.
   - `SESSION_STORE=redis`: almacena las sesiones en Redis (`REDIS_URL`) con expiración automática por TTL, recomendado para producción.
   - `SESSION_STORE=memory`: solo en memoria RAM (pruebas).

---

## 📱 Conexión desde la Aplicación Android

- **Desde el Emulador de Android:** `http://10.0.2.2:3000/api/chat`
- **Desde un Dispositivo Físico (mismo Wi-Fi):** `http://192.168.X.X:3000/api/chat`
