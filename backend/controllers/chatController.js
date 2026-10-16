/**
 * controllers/chatController.js
 * 
 * Controlador principal para el orquestador de Inteligencia Artificial Gerontológica.
 * Conecta el endpoint REST con el SDK oficial de Google Gemini, implementando:
 * 1. Ingeniería de System Prompt Gerontológico (Modelo CARE).
 * 2. Formato estructurado obligatorio (JSON Schema) para separación TTS/Pantalla.
 * 3. Historial de sesión en memoria con ventana deslizante (últimos 5-6 mensajes).
 * 4. Guardrails y filtros de seguridad multi-nivel (pre y post LLM).
 */

const { GoogleGenerativeAI, SchemaType } = require("@google/generative-ai");

// =============================================================================
// 1. INGENIERÍA DE SYSTEM PROMPT GERONTOLÓGICO (MODELO C.A.R.E.)
// =============================================================================
const GERONTOLOGICAL_SYSTEM_PROMPT = `
Eres un asistente de acompañamiento emocional y soporte empático para adultos mayores en una aplicación móvil Android.
Tu propósito es ofrecer compañía afectuosa, escucha activa, validación de sentimientos y calma cotidiana.

### FILOSOFÍA Y PERSONALIDAD (MODELO C.A.R.E.):
- C - CALMA Y COMPRENSIÓN: Habla con ritmo tranquilo, tono sereno y afectuoso. Valida siempre lo que la persona siente antes de responder o cambiar de tema.
- A - AUTONOMÍA Y DIGNIDAD: Trata al usuario como a un adulto digno y sabio. 
  * ESTÁ ESTRICTAMENTE PROHIBIDO infantilizar al usuario.
  * NO uses diminutivos condescendientes (como "abuelito", "viejito", "chiquito").
  * Usa un trato respetuoso pero cercano (un "tú" cariñoso o "usted" educado según el contexto).
- R - REDIRECCIÓN Y ALIVIO: Si la persona expresa soledad, rumiación o desánimo, acompáñala primero reconociendo su emoción, y luego ofrécele un recuerdo agradable, una respiración suave o una charla agradable.
- E - ÉTICA Y EMERGENCIAS: 
  * NUNCA recetes medicamentos, ni ajustes dosis, ni des diagnósticos médicos definitivos.
  * Si el usuario menciona dolor agudo en el pecho, dificultad severa para respirar, caídas con imposibilidad de levantarse, desorientación extrema, o deseos de morir/autolesión:
    1. Activa inmediatamente "suggested_action": "emergency_alert".
    2. En "spoken_text", transmite calma profunda y pídele que no se mueva o que presione el botón de alerta, asegurándole que pediremos ayuda a su cuidador o a emergencias de inmediato.

### FORMATO MULTIMODAL DE RESPUESTA (ESTRICTO JSON):
Debes responder SIEMPRE con el siguiente esquema JSON:
{
  "spoken_text": "Texto completo y fluido para ser pronunciado por el sintetizador de voz (TTS). Frases cortas, pausas naturales, lenguaje muy simple y sin tecnicismos.",
  "screen_text": "Texto de apoyo visual ultra breve (MÁXIMO 5 A 7 PALABRAS). Debe ser sintético y directo para pantalla grande sin repetir textualmente todo el audio.",
  "user_emotion": "Emoción principal identificada: 'ansiedad' | 'tristeza' | 'soledad' | 'alegria' | 'calma' | 'confusion' | 'dolor' | 'neutro'.",
  "suggested_action": "Acción para la UI Android: 'continue' (charla habitual) | 'show_decision_buttons' (mostrar opciones Sí/No para no forzar la voz) | 'emergency_alert' (situación de riesgo vital o dolor grave)."
}
`;

// =============================================================================
// 2. ESQUEMA ESTRUCTURADO PARA GOOGLE GEMINI (Structured Outputs)
// =============================================================================
const careResponseSchema = {
  type: SchemaType.OBJECT,
  properties: {
    spoken_text: {
      type: SchemaType.STRING,
      description: "Texto conversacional completo para el motor TTS de Android. Claro, empático, sin infantilismos ni tecnicismos."
    },
    screen_text: {
      type: SchemaType.STRING,
      description: "Resumen visual para pantalla grande de Android. Estrictamente entre 3 y 7 palabras."
    },
    user_emotion: {
      type: SchemaType.STRING,
      description: "Emoción del usuario detectada: ansiedad, tristeza, soledad, alegria, calma, confusion, dolor, neutro."
    },
    suggested_action: {
      type: SchemaType.STRING,
      description: "Acción requerida en la app: continue, show_decision_buttons, emergency_alert."
    }
  },
  required: ["spoken_text", "screen_text", "user_emotion", "suggested_action"]
};

// =============================================================================
// 3. INICIALIZACIÓN DEL CLIENTE GEMINI
// =============================================================================
const apiKey = process.env.GEMINI_API_KEY;
let genAI = null;
let model = null;

if (apiKey && apiKey !== "tu_gemini_api_key_aqui") {
  genAI = new GoogleGenerativeAI(apiKey);
  model = genAI.getGenerativeModel({
    model: process.env.GEMINI_MODEL || "gemini-2.0-flash",
    systemInstruction: GERONTOLOGICAL_SYSTEM_PROMPT,
    generationConfig: {
      responseMimeType: "application/json",
      responseSchema: careResponseSchema,
      temperature: 0.6,
      maxOutputTokens: 500
    }
  });
} else {
  console.warn("⚠️  [ChatController] GEMINI_API_KEY no configurada o es la plantilla. El servidor funcionará en modo MOCK para desarrollo.");
}

// =============================================================================
// 4. GESTIÓN DE HISTORIAL EN MEMORIA (Ventana Deslizante por Sesión)
// =============================================================================
const MAX_HISTORY_MESSAGES = parseInt(process.env.SESSION_MAX_MESSAGES || "6", 10);
const SESSION_TTL_MS = parseInt(process.env.SESSION_TTL_MINUTES || "60", 10) * 60 * 1000;

// Almacén temporal: Map<sessionId, { messages: Array, lastUpdated: number }>
const sessionStore = new Map();

/**
 * Limpieza periódica de sesiones huérfanas o inactivas para evitar memory leaks.
 */
setInterval(() => {
  const now = Date.now();
  for (const [sessionId, session] of sessionStore.entries()) {
    if (now - session.lastUpdated > SESSION_TTL_MS) {
      sessionStore.delete(sessionId);
      console.log(`[Memory Cleanup] Sesión inactiva eliminada: ${sessionId}`);
    }
  }
}, 15 * 60 * 1000); // Cada 15 minutos

/**
 * Obtiene el historial de una sesión o inicializa uno vacío.
 */
function getSessionHistory(sessionId) {
  if (!sessionStore.has(sessionId)) {
    sessionStore.set(sessionId, {
      messages: [],
      lastUpdated: Date.now()
    });
  }
  const session = sessionStore.get(sessionId);
  session.lastUpdated = Date.now();
  return session;
}

/**
 * Agrega interacción respetando la ventana deslizante (últimos N mensajes).
 */
function appendToSessionHistory(sessionId, userText, botSpokenText) {
  const session = getSessionHistory(sessionId);
  session.messages.push({ role: "user", parts: [{ text: userText }] });
  session.messages.push({ role: "model", parts: [{ text: botSpokenText }] });

  // Mantener solo los últimos MAX_HISTORY_MESSAGES
  if (session.messages.length > MAX_HISTORY_MESSAGES) {
    session.messages = session.messages.slice(-MAX_HISTORY_MESSAGES);
  }
}

// =============================================================================
// 5. GUARDRAILS Y SEGURIDAD CRÍTICA (Filtros Pre y Post LLM)
// =============================================================================

// Expresiones de riesgo vital extremo o ideación suicida
const CRITICAL_EMERGENCY_REGEX = /(?:me quiero morir|quiero morir|no quiero vivir|acabar con mi vida|matarme|quitarme la vida|desaparecer para siempre|me tom(?:e|é) muchas pastillas|fuerte dolor en el pecho|dolor opresivo|no puedo respirar|me falta el aire|me ca[ií] y no puedo levantarme|no siento las piernas|se me durmi(?:o|ó) la mitad de la cara)/i;

// Expresiones de solicitud médica/fármacos que deben ser contenidas
const MEDICAL_PRESCRIPTION_REGEX = /(?:tómate|toma|inyéctate|consume|receto|dosis de|miligramos|mg de|deja de tomar)/i;

/**
 * Verifica si el texto del usuario activa una alerta de emergencia crítica directa.
 */
function evaluateInputGuardrails(text) {
  if (CRITICAL_EMERGENCY_REGEX.test(text)) {
    return {
      triggered: true,
      reason: "critical_risk_keyword"
    };
  }
  return { triggered: false };
}

/**
 * Aplica filtros de seguridad sobre la respuesta del LLM antes de enviarla a Android.
 */
function sanitizeAndEnforceGuardrails(responseObj, userInput) {
  const sanitized = { ...responseObj };

  // 1. Red de seguridad de emergencia si el input contiene riesgo vital
  if (CRITICAL_EMERGENCY_REGEX.test(userInput)) {
    sanitized.suggested_action = "emergency_alert";
    sanitized.user_emotion = sanitized.user_emotion || "dolor";
    sanitized.screen_text = "Pedir ayuda urgente";
    sanitized.spoken_text = "Te estoy escuchando y mantén la calma. En este momento es muy importante que no hagas esfuerzos. Ya mismo te comunico con tu cuidador y los servicios de emergencia para que vengan a ayudarte.";
    return sanitized;
  }

  // 2. Si el LLM intentó recetar o indicar medicamentos, neutralizarlo
  if (MEDICAL_PRESCRIPTION_REGEX.test(sanitized.spoken_text)) {
    sanitized.spoken_text = "Comprendo lo que me dices sobre tus medicinas, pero recuerda que siempre debemos seguir las indicaciones exactas de tu médico de cabecera. ¿Quieres que contactemos a tu cuidador para consultarlo?";
    sanitized.screen_text = "Consulta a tu médico";
    sanitized.suggested_action = "show_decision_buttons";
  }

  // 3. Garantizar que screen_text no exceda 7 palabras (para no saturar la pantalla accesible)
  if (sanitized.screen_text) {
    const words = sanitized.screen_text.trim().split(/\s+/);
    if (words.length > 7) {
      sanitized.screen_text = words.slice(0, 7).join(" ");
    }
  } else {
    sanitized.screen_text = "Aquí contigo";
  }

  // 4. Validar enumeraciones de acción permitidas
  const validActions = ["continue", "show_decision_buttons", "emergency_alert"];
  if (!validActions.includes(sanitized.suggested_action)) {
    sanitized.suggested_action = "continue";
  }

  return sanitized;
}

// =============================================================================
// 6. RESPUESTAS MOCK PARA DESARROLLO LOCAL SIN API KEY
// =============================================================================
function generateMockResponse(userInput) {
  const lower = (userInput || "").toLowerCase();

  if (CRITICAL_EMERGENCY_REGEX.test(lower)) {
    return {
      spoken_text: "Estoy aquí contigo. Mantén la calma y respira despacio. Vamos a avisar a tu cuidador y a pedir ayuda de inmediato.",
      screen_text: "Pedir ayuda urgente",
      user_emotion: "ansiedad",
      suggested_action: "emergency_alert"
    };
  }

  if (lower.includes("triste") || lower.includes("sola") || lower.includes("solo") || lower.includes("desánimo")) {
    return {
      spoken_text: "Lamento que te sientas así en este momento. Es completamente válido tener días difíciles. Quiero que sepas que no estás solo y que cuentas conmigo para conversar el tiempo que desees.",
      screen_text: "No estás solo, te acompaño",
      user_emotion: "tristeza",
      suggested_action: "continue"
    };
  }

  if (lower.includes("pastilla") || lower.includes("medicamento") || lower.includes("remedio")) {
    return {
      spoken_text: "Recuerda que es muy bueno tomar tus medicinas siempre con un vaso de agua y a las horas que te indicó tu médico.",
      screen_text: "Recuerda tomar tu medicina",
      user_emotion: "calma",
      suggested_action: "continue"
    };
  }

  if (lower.includes("cita") || lower.includes("médic") || lower.includes("doctor")) {
    return {
      spoken_text: "Tu salud es lo más valioso. Es una excelente idea tener presente tu cita médica. ¿Te gustaría que la anotemos o revisemos juntos?",
      screen_text: "Cita médica programada",
      user_emotion: "neutro",
      suggested_action: "show_decision_buttons"
    };
  }

  return {
    spoken_text: `Te escuché con mucha atención cuando mencionaste "${userInput}". Me alegra compartir este ratito contigo. ¿Cómo ha estado tu día hoy?`,
    screen_text: "Estoy aquí para escucharte",
    user_emotion: "calma",
    suggested_action: "continue"
  };
}

// =============================================================================
// 7. CONTROLADOR PRINCIPAL: POST /api/chat
// =============================================================================
/**
 * Procesa la solicitud de chat recibida desde la aplicación Android.
 * Body esperado:
 * {
 *   "message": "Texto transcrito del STT en Android",
 *   "sessionId": "identificador_de_sesión_opcional"
 * }
 */
async function handleChat(req, res, next) {
  try {
    const { message, sessionId = "default_senior_session" } = req.body;

    // Validación básica de entrada
    if (!message || typeof message !== "string" || message.trim() === "") {
      return res.status(400).json({
        error: "Bad Request",
        message: "El campo 'message' es obligatorio y debe contener texto transcrito no vacío."
      });
    }

    const trimmedMessage = message.trim();
    const session = getSessionHistory(sessionId);

    // Verificación de guardrails previos al LLM
    const preCheck = evaluateInputGuardrails(trimmedMessage);
    if (preCheck.triggered) {
      console.warn(`🚨 [Guardrails Triggered] Riesgo crítico detectado en input: "${trimmedMessage}"`);
      const emergencyResponse = sanitizeAndEnforceGuardrails({}, trimmedMessage);
      appendToSessionHistory(sessionId, trimmedMessage, emergencyResponse.spoken_text);
      return res.status(200).json({
        success: true,
        sessionId,
        data: emergencyResponse
      });
    }

    let parsedOutput = null;

    // Si Gemini no está configurado, usar fallback Mock inteligente
    if (!model) {
      console.log(`[ChatController Mock] Procesando mensaje sin API Key: "${trimmedMessage}"`);
      parsedOutput = generateMockResponse(trimmedMessage);
    } else {
      // Iniciar o reanudar sesión de chat en Gemini con historial existente
      const chat = model.startChat({
        history: session.messages
      });

      const result = await chat.sendMessage(trimmedMessage);
      const rawText = result.response.text();

      try {
        parsedOutput = JSON.parse(rawText);
      } catch (parseError) {
        console.error("Error al parsear JSON estructurado de Gemini:", parseError, rawText);
        // Fallback en caso de contingencia
        parsedOutput = {
          spoken_text: rawText.replace(/[{}]/g, "").trim(),
          screen_text: "Te escucho con atención",
          user_emotion: "neutro",
          suggested_action: "continue"
        };
      }
    }

    // Aplicar capa de sanitización y guardrails post-inferencia
    const finalResponse = sanitizeAndEnforceGuardrails(parsedOutput, trimmedMessage);

    // Guardar en el historial de sesión en memoria
    appendToSessionHistory(sessionId, trimmedMessage, finalResponse.spoken_text);

    return res.status(200).json({
      success: true,
      sessionId,
      data: finalResponse
    });

  } catch (error) {
    console.error("❌ [ChatController Error]:", error);
    next(error);
  }
}

/**
 * Endpoint para limpiar el historial de una sesión de conversación.
 * DELETE /api/chat/session/:sessionId
 */
function clearSession(req, res) {
  const { sessionId } = req.params;
  if (sessionStore.has(sessionId)) {
    sessionStore.delete(sessionId);
    return res.status(200).json({
      success: true,
      message: `Historial de sesión '${sessionId}' eliminado exitosamente.`
    });
  }
  return res.status(404).json({
    success: false,
    message: `No se encontró historial activo para la sesión '${sessionId}'.`
  });
}

/**
 * Endpoint para inspeccionar el estado del historial (útil en depuración).
 * GET /api/chat/session/:sessionId
 */
function getSessionInfo(req, res) {
  const { sessionId } = req.params;
  if (sessionStore.has(sessionId)) {
    const session = sessionStore.get(sessionId);
    return res.status(200).json({
      success: true,
      sessionId,
      messageCount: session.messages.length,
      lastUpdated: new Date(session.lastUpdated).toISOString()
    });
  }
  return res.status(404).json({
    success: false,
    message: `La sesión '${sessionId}' no existe o ya expiró.`
  });
}

module.exports = {
  handleChat,
  clearSession,
  getSessionInfo
};
