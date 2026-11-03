// test-api.js
// Lee API_ACCESS_TOKEN desde .env para autenticarse igual que la app Android
require("dotenv").config();

const testCases = [
  {
    name: "1. POSITIVA (Reforzar la situación)",
    payload: {
      message: "Hoy vinieron mis nietos de visita y estoy muy feliz y agradecido",
      sessionId: "senior_carlos_101"
    }
  },
  {
    name: "2. NEGATIVA_TRISTEZA_SOLEDAD (Caso María/Roberto - Turno 1)",
    payload: {
      message: "Me siento muy solo hoy, nadie viene a visitarme y la casa está en silencio",
      sessionId: "senior_carlos_101"
    }
  },
  {
    name: "3. NEGATIVA_TRISTEZA_SOLEDAD (Anti-Repetición - Turno 2)",
    payload: {
      message: "Sigo un poco triste y con mucha nostalgia en este día largo",
      sessionId: "senior_carlos_101"
    }
  },
  {
    name: "4. NEGATIVA_ANSIEDAD_PREOCUPACION (Caso Don Carlos - Olvido/Cita/Pastilla)",
    payload: {
      message: "Estoy preocupado porque se me olvidó a qué hora tenía mi cita médica y mi pastilla",
      sessionId: "senior_carlos_101"
    }
  },
  {
    name: "5. NEUTRA (Mantener estado neutro)",
    payload: {
      message: "Hola, buenos días, ¿cómo está el clima hoy? Platiquemos un rato",
      sessionId: "senior_carlos_101"
    }
  },
  {
    name: "6. AMBIGUA (Indagar/Aclarar - Expresión de duda o muy corta)",
    payload: {
      message: "No sé, tal vez...",
      sessionId: "senior_carlos_101"
    }
  },
  {
    name: "7. Emergencia médica severa (dolor en pecho / caída)",
    payload: {
      message: "¡Me caí y no puedo levantarme, me duele el pecho!",
      sessionId: "senior_carlos_101"
    }
  }
];

async function runTests() {
  console.log("=== INICIANDO PRUEBAS DE LA API CEREBRO IA GERONTOLÓGICO (MODELO CARE) ===\n");

  for (const tc of testCases) {
    console.log(`▶ Test: [${tc.name}]`);
    console.log(`  Input: "${tc.payload.message}"`);
    try {
      const res = await fetch("http://localhost:3000/api/chat", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(process.env.API_ACCESS_TOKEN ? { Authorization: `Bearer ${process.env.API_ACCESS_TOKEN}` } : {})
        },
        body: JSON.stringify(tc.payload)
      });
      const data = await res.json();
      console.log("  Status:", res.status);
      console.log("  Source:", data.source, data.fallback_reason ? `(fallback_reason: ${data.fallback_reason})` : "");
      console.log("  Root audio_base64 duplicado?:", "audio_base64" in data);
      console.log("  Response Payload:");
      console.log("   - spoken_text (TTS)     :", data.data.spoken_text);
      console.log("   - screen_text (Pantalla):", data.data.screen_text);
      console.log("   - user_emotion          :", data.data.user_emotion);
      console.log("   - suggested_action      :", data.data.suggested_action);
      console.log("   - audio_duration_ms     :", data.data.audio_duration_ms);
      console.log("   - audio_base64 (MP3)    :", data.data.audio_base64 ? `Presente (${data.data.audio_base64.length} chars Base64)` : "null (Fallback TTS local)");
      console.log("------------------------------------------------------------\n");
    } catch (err) {
      console.error("  Error ejecutando prueba:", err.message);
    }
  }
}

runTests();
