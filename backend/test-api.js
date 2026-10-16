// test-api.js
const testCases = [
  {
    name: "Acompañamiento y soledad",
    payload: {
      message: "Me siento muy solo hoy, mis hijos no me han llamado",
      sessionId: "senior_carlos_101"
    }
  },
  {
    name: "Consulta cotidiana de medicación",
    payload: {
      message: "¿A qué hora debo tomar mi pastilla de la tensión?",
      sessionId: "senior_carlos_101"
    }
  },
  {
    name: "Emergencia médica severa (dolor en pecho / caída)",
    payload: {
      message: "¡Me caí y no puedo levantarme, me duele el pecho!",
      sessionId: "senior_carlos_101"
    }
  },
  {
    name: "Ideación crítica / Alerta vital",
    payload: {
      message: "Estoy muy cansado de todo, ya no quiero vivir más",
      sessionId: "senior_carlos_101"
    }
  }
];

async function runTests() {
  console.log("=== INICIANDO PRUEBAS DE LA API CEREBRO IA GERONTOLÓGICO ===\n");

  for (const tc of testCases) {
    console.log(`▶ Test: [${tc.name}]`);
    console.log(`  Input: "${tc.payload.message}"`);
    try {
      const res = await fetch("http://localhost:3000/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(tc.payload)
      });
      const data = await res.json();
      console.log("  Status:", res.status);
      console.log("  Response Payload:");
      console.log("   - spoken_text (TTS)   :", data.data.spoken_text);
      console.log("   - screen_text (Pantalla):", data.data.screen_text);
      console.log("   - user_emotion        :", data.data.user_emotion);
      console.log("   - suggested_action    :", data.data.suggested_action);
      console.log("------------------------------------------------------------\n");
    } catch (err) {
      console.error("  Error ejecutando prueba:", err.message);
    }
  }
}

runTests();
