"use strict";
/*
 * Versión web de prueba del asistente CARE.
 * Replica la máquina de estados de ChatbotViewModel.kt (app Android) contra la misma API, para
 * probar la conversación desde un navegador (p. ej. un iPhone, que no puede instalar la APK).
 * Diferencias con la app: el reconocimiento de voz es el del navegador (si no hay, se escribe el
 * mensaje) y, sin conexión, no hay banco de respuestas local: se muestra la pantalla de reparación.
 */
(() => {
  const CLAVES = {
    token: "care_web_token",
    sesion: "care_web_session",
    acompanante: "care_web_companion"
  };
  // Igual que el readTimeout de la app (ChatApiClient.kt); el backend responde antes de 18 s
  const TIEMPO_MAXIMO_MS = 25000;
  // ThinkingScreen.kt: a los 8 s el mensaje de espera cambia para tranquilizar
  const ESPERA_LARGA_MS = 8000;
  // WAV vacío: reproducirlo con el primer toque desbloquea el audio en Safari (sin gesto no suena)
  const SILENCIO = "data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQAAAAA=";

  // Las claves son los valores de `companion` en POST /api/chat (VOICE_PROFILES en ttsService.js).
  // `imagen` es la imagen base (saludo), que también es el gesto neutral; `gestos`, las demás
  // expresiones (AssistantAvatar.imageFor en la app, mismas imágenes de res/drawable-nodpi).
  const ACOMPANANTES = {
    male: {
      etiqueta: "Compañero",
      imagen: "img/avatar_companero.webp",
      gestos: {
        alegre: "img/avatar_companero_alegre.webp",
        comprensivo: "img/avatar_companero_triste.webp",
        pensativo: "img/avatar_companero_pensativo.webp",
        hablando: "img/avatar_companero_hablando.webp"
      },
      tonoLocal: 0.8
    },
    female: {
      etiqueta: "Compañera",
      imagen: "img/avatar_companera.webp",
      gestos: {
        alegre: "img/avatar_companera_alegre.webp",
        comprensivo: "img/avatar_companera_triste.webp",
        pensativo: "img/avatar_companera_pensativa.webp",
        hablando: "img/avatar_companera_hablando.webp"
      },
      tonoLocal: 1.0
    }
  };

  // El avatar representa la emoción de lo que dice (AvatarExpression.kt en la app): el gesto sale de
  // `user_emotion`, porque la respuesta se ajusta a esa emoción. Es apoyo visual (CARE: Redundancia
  // con el tono de la voz) y el lector de pantalla lo lee en el texto alternativo (Equivalencia).
  // Mientras habla, estilos.css alterna el gesto con la pose de hablar (ciclo de 6 s, como la app).
  const GESTOS = {
    neutral: "gesto tranquilo",
    alegre: "gesto alegre",
    comprensivo: "gesto comprensivo",
    pensativo: "gesto pensativo"
  };

  // Banco local de imágenes (modelo CARE): mismas claves y descripciones que ContextImage.kt en la
  // app; las ilustraciones son las de res/drawable/img_*.xml, aquí como img/temas/<clave>.svg.
  // El backend solo envía `image_key` y la página únicamente carga claves de esta lista.
  const IMAGENES = {
    saludo: "Taza de café caliente frente a una ventana con sol",
    salud: "Carpeta médica con un corazón",
    agenda: "Calendario con un día marcado y un reloj",
    familia: "Familia reunida bajo un corazón",
    mascotas: "Un perro y un gato sentados juntos",
    musica: "Radio antigua con notas musicales",
    lectura: "Libro abierto y unas gafas",
    comida: "Plato de sopa caliente con una arepa",
    naturaleza: "Sendero entre árboles bajo el sol",
    espiritualidad: "Vela encendida con luz cálida",
    descanso: "Cama bajo un cielo de noche con luna y estrellas",
    clima: "Sol detrás de una nube con lluvia",
    recuerdos: "Fotografías antiguas",
    alegria: "Sol radiante sobre flores de colores",
    calma: "Hoja verde dentro de círculos serenos",
    acompanamiento: "Dos personas sentadas juntas en una banca",
    escucha: "Dos globos de conversación con un signo de pregunta",
    ayuda_cercana: "Una persona apoya la mano en el hombro de otra para ayudarla"
  };
  // Reservada a las posibles emergencias (Redundancia): ninguna otra respuesta la muestra
  const IMAGEN_EMERGENCIA = "ayuda_cercana";
  // Imagen por defecto de cada emoción (DEFAULT_IMAGE_BY_EMOTION en imageCatalog.js)
  const IMAGEN_POR_EMOCION = {
    POSITIVA: "alegria",
    NEGATIVA_TRISTEZA_SOLEDAD: "acompanamiento",
    NEGATIVA_ANSIEDAD_PREOCUPACION: "calma",
    NEUTRA: "saludo",
    AMBIGUA: "escucha"
  };

  // Mismos textos que UiState y las pantallas de la app
  const TEXTO = {
    reparacion: "No he entendido tu pregunta. ¿Puedes repetirla?",
    pistaReparacion: "Puedes hablar más despacio o acercarte un poco más al teléfono.",
    emergenciaPantalla: "Pide ayuda a alguien cercano",
    respuestaVacia: "Estoy aquí contigo para escucharte y acompañarte.",
    preguntaDecision: "¿Quieres continuar con la conversación?"
  };

  // localStorage puede fallar (navegación privada antigua): entonces se recuerda solo en memoria
  const respaldo = {};
  const almacen = {
    leer(clave) {
      try { return localStorage.getItem(clave); } catch { return respaldo[clave] ?? null; }
    },
    guardar(clave, valor) {
      try { localStorage.setItem(clave, valor); } catch { respaldo[clave] = valor; }
    },
    borrar(clave) {
      try { localStorage.removeItem(clave); } catch { delete respaldo[clave]; }
    }
  };

  const raiz = document.getElementById("app");
  const Reconocimiento = window.SpeechRecognition || window.webkitSpeechRecognition;
  const esIOS = /iPhone|iPad|iPod/.test(navigator.userAgent) ||
    (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

  let token = almacen.leer(CLAVES.token);
  let acompanante = ACOMPANANTES[almacen.leer(CLAVES.acompanante)] ? almacen.leer(CLAVES.acompanante) : null;
  let actual = null;             // pantalla visible
  const historial = [];          // pila de "Atrás" (stateHistory en la app)
  let modoEscritura = !Reconocimiento;
  let temporizadorEspera = null;

  // ===========================================================================
  // Utilidades
  // ===========================================================================

  // Crea los elementos con el DOM: los textos del servidor nunca se interpretan como HTML
  function h(etiqueta, atributos, ...hijos) {
    const elemento = document.createElement(etiqueta);
    for (const [clave, valor] of Object.entries(atributos || {})) {
      if (valor === false || valor == null) continue;
      if (clave.startsWith("on")) elemento.addEventListener(clave.slice(2), valor);
      else if (clave === "clase") elemento.className = valor;
      else elemento.setAttribute(clave, valor === true ? "" : String(valor));
    }
    for (const hijo of hijos.flat()) {
      if (hijo == null || hijo === false || hijo === "") continue;
      elemento.append(hijo instanceof Node ? hijo : String(hijo));
    }
    return elemento;
  }

  function iconoMicrofono() {
    const ns = "http://www.w3.org/2000/svg";
    const svg = document.createElementNS(ns, "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("aria-hidden", "true");
    const trazo = document.createElementNS(ns, "path");
    trazo.setAttribute("fill", "currentColor");
    trazo.setAttribute("d", "M12 14a3 3 0 0 0 3-3V5a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.92V21h2v-3.08A7 7 0 0 0 19 11h-2z");
    svg.append(trazo);
    return svg;
  }

  function nuevoId() {
    if (crypto.randomUUID) return crypto.randomUUID();
    return Array.from(crypto.getRandomValues(new Uint8Array(16)), (b) => b.toString(16).padStart(2, "0")).join("");
  }

  // sessionId propio de este navegador (como SessionIdProvider en la app); cumple ^[\w\-.:]+$
  function idSesion() {
    let id = almacen.leer(CLAVES.sesion);
    if (!id) {
      id = `web_${nuevoId()}`;
      almacen.guardar(CLAVES.sesion, id);
    }
    return id;
  }

  function vibrar(patron = 30) {
    if (navigator.vibrate) navigator.vibrate(patron);
  }

  function esAfectiva(emocion) {
    return /TRISTEZA|SOLEDAD|ANSIEDAD|PREOCUPACION/.test(emocion || "");
  }

  // Como AvatarExpression.forEmotion en la app (emoción desconocida o vacía → neutral)
  function gestoPorEmocion(emocion) {
    const valor = String(emocion || "").trim().toUpperCase();
    if (valor === "POSITIVA") return "alegre";
    if (valor.startsWith("NEGATIVA")) return "comprensivo";
    if (valor === "AMBIGUA") return "pensativo";
    return "neutral";
  }

  function imagenDelGesto(datos, gesto) {
    return gesto === "neutral" ? datos.imagen : datos.gestos[gesto];
  }

  // Descarga los gestos del acompañante elegido antes de que hagan falta, para que el primer
  // cambio de expresión no deje el círculo vacío mientras llega la imagen
  function precargarGestos() {
    const datos = ACOMPANANTES[acompanante];
    if (!datos) return;
    for (const src of Object.values(datos.gestos)) {
      const imagen = new Image();
      imagen.src = src;
    }
  }

  // Como ContextImage.resolve en la app: la emergencia lleva siempre su imagen; una clave ausente
  // (backend anterior), desconocida o reservada se cambia por la imagen de la emoción
  function imagenDe(clave, emocion, accion) {
    if (accion === "emergency_alert") return IMAGEN_EMERGENCIA;
    const valor = String(clave || "").trim().toLowerCase();
    const valida = Object.prototype.hasOwnProperty.call(IMAGENES, valor) && valor !== IMAGEN_EMERGENCIA;
    return valida ? valor : (IMAGEN_POR_EMOCION[emocion] || "saludo");
  }

  function saludo() {
    const hora = new Date().getHours();
    if (hora < 12) return "Buenos días";
    return hora < 19 ? "Buenas tardes" : "Buenas noches";
  }

  // ===========================================================================
  // Audio: voz neural del backend o, si no llega, la voz del navegador
  // ===========================================================================

  const reproductor = new Audio();
  let urlAudio = null;
  let pantallaDelAudio = null;
  let pausado = true;
  let audioDesbloqueado = false;

  function desbloquearAudio() {
    if (audioDesbloqueado) return;
    audioDesbloqueado = true;
    reproductor.src = SILENCIO;
    reproductor.play().then(() => reproductor.pause()).catch(() => {});
    if ("speechSynthesis" in window) speechSynthesis.speak(new SpeechSynthesisUtterance(""));
  }

  function detenerVoz() {
    reproductor.pause();
    if ("speechSynthesis" in window) speechSynthesis.cancel();
    pausado = true;
  }

  function prepararAudioNeural(pantalla) {
    if (pantallaDelAudio === pantalla) return true;
    if (urlAudio) URL.revokeObjectURL(urlAudio);
    urlAudio = null;
    pantallaDelAudio = null;
    if (!pantalla.audio) return false;
    try {
      const binario = atob(pantalla.audio);
      const bytes = new Uint8Array(binario.length);
      for (let i = 0; i < binario.length; i++) bytes[i] = binario.charCodeAt(i);
      urlAudio = URL.createObjectURL(new Blob([bytes], { type: "audio/mpeg" }));
    } catch {
      return false;
    }
    reproductor.src = urlAudio;
    pantallaDelAudio = pantalla;
    return true;
  }

  function vozEspanola() {
    const voces = speechSynthesis.getVoices().filter((v) => (v.lang || "").toLowerCase().startsWith("es"));
    for (const idioma of ["es-co", "es-mx", "es-us", "es-419", "es-es"]) {
      const voz = voces.find((v) => v.lang.toLowerCase().replace("_", "-") === idioma);
      if (voz) return voz;
    }
    return voces[0] || null;
  }

  function hablarConVozDelNavegador(texto) {
    if (!("speechSynthesis" in window) || !texto) {
      pausado = true;
      actualizarIndicadores();
      return;
    }
    speechSynthesis.cancel();
    const frase = new SpeechSynthesisUtterance(texto);
    const voz = vozEspanola();
    if (voz) frase.voice = voz;
    frase.lang = voz ? voz.lang : "es-CO";
    frase.rate = 0.9;
    frase.pitch = (ACOMPANANTES[acompanante] || ACOMPANANTES.female).tonoLocal;
    frase.onend = frase.onerror = () => {
      pausado = true;
      actualizarIndicadores();
    };
    pausado = false;
    speechSynthesis.speak(frase);
    actualizarIndicadores();
  }

  // Lee la pantalla: con la página oculta empieza en pausa, salvo una posible emergencia
  function reproducir(pantalla, { reiniciar = true } = {}) {
    detenerVoz();
    if (!pantalla || !pantalla.voz) return actualizarIndicadores();
    const tieneNeural = prepararAudioNeural(pantalla);
    if (document.hidden && pantalla.tipo !== "emergencia") return actualizarIndicadores();
    if (!tieneNeural) return hablarConVozDelNavegador(pantalla.voz);
    if (reiniciar) reproductor.currentTime = 0;
    reproductor.play().catch(() => hablarConVozDelNavegador(pantalla.voz));
  }

  reproductor.addEventListener("play", () => {
    if (urlAudio && reproductor.src === urlAudio) {
      pausado = false;
      actualizarIndicadores();
    }
  });
  for (const evento of ["pause", "ended"]) {
    reproductor.addEventListener(evento, () => {
      pausado = true;
      actualizarIndicadores();
    });
  }

  function alternarPausa() {
    desbloquearAudio();
    vibrar();
    if (pausado) reproducir(actual, { reiniciar: false });
    else detenerVoz();
    actualizarIndicadores();
  }

  function repetir() {
    desbloquearAudio();
    vibrar();
    reproducir(actual, { reiniciar: true });
  }

  // ===========================================================================
  // Reconocimiento de voz del navegador (SpeechRecognizer en la app)
  // ===========================================================================

  let reconocedor = null;
  let transcripcion = "";

  function idiomaReconocimiento() {
    const idiomas = navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language];
    return idiomas.find((idioma) => (idioma || "").toLowerCase().startsWith("es")) || "es-CO";
  }

  function esperandoVoz() {
    return Boolean(actual) && (actual.tipo === "escuchando" || actual.tipo === "pensando");
  }

  function cancelarEscucha() {
    const anterior = reconocedor;
    reconocedor = null;
    if (anterior) {
      try { anterior.abort(); } catch { /* ya estaba parado */ }
    }
  }

  function empezarAEscuchar() {
    desbloquearAudio();
    cancelarPeticion();
    cancelarEscucha();
    detenerVoz();
    vibrar();
    transicion({ tipo: "escuchando" });
    if (modoEscritura) return;

    transcripcion = "";
    const nuevo = new Reconocimiento();
    nuevo.lang = idiomaReconocimiento();
    nuevo.continuous = false;
    nuevo.interimResults = false;
    nuevo.maxAlternatives = 1;
    nuevo.onresult = (evento) => {
      if (reconocedor !== nuevo) return;
      transcripcion = Array.from(evento.results, (resultado) => resultado[0].transcript).join(" ").trim();
    };
    // Fin de la voz: "Pensando" mientras se transcribe y responde el backend
    nuevo.onspeechend = () => {
      if (reconocedor === nuevo && actual.tipo === "escuchando") transicion({ tipo: "pensando" });
    };
    nuevo.onerror = (evento) => {
      if (reconocedor !== nuevo) return;
      reconocedor = null;
      errorDeReconocimiento(evento.error);
    };
    nuevo.onend = () => {
      if (reconocedor !== nuevo) return;
      reconocedor = null;
      if (transcripcion) enviarMensaje(transcripcion);
      else errorDeReconocimiento("no-speech");
    };
    reconocedor = nuevo;
    try {
      nuevo.start();
    } catch {
      reconocedor = null;
      errorDeReconocimiento("service-not-allowed");
    }
  }

  // Micrófono mientras escucha: termina y envía lo dicho (onMicClicked en la app)
  function terminarDeHablar() {
    vibrar();
    if (actual.tipo === "escuchando") transicion({ tipo: "pensando" });
    if (reconocedor) {
      try { reconocedor.stop(); } catch { /* ya estaba parado */ }
    }
  }

  function errorDeReconocimiento(codigo) {
    if (!esperandoVoz() || codigo === "aborted") return;
    let mensaje = TEXTO.reparacion;
    let pista = TEXTO.pistaReparacion;
    if (["not-allowed", "service-not-allowed", "audio-capture", "network", "language-not-supported"].includes(codigo)) {
      // Sin micrófono o sin servicio de voz: a partir de ahora se escribe el mensaje
      modoEscritura = true;
      mensaje = codigo === "not-allowed" || codigo === "audio-capture"
        ? "No puedo usar el micrófono en este navegador."
        : "El reconocimiento de voz no está disponible en este navegador.";
      pista = esIOS
        ? "En iPhone, usa Safari, permite el micrófono y activa Dictado en Ajustes > General > Teclado. Mientras tanto, puedes escribir tu mensaje."
        : "Permite el micrófono para esta página o usa Chrome. Mientras tanto, puedes escribir tu mensaje.";
    }
    const pantalla = { tipo: "reparacion", mensaje, pista, voz: mensaje };
    transicion(pantalla);
    reproducir(pantalla);
  }

  // ===========================================================================
  // Backend: POST /api/chat y sincronización multimodal (executeMasterClockSync)
  // ===========================================================================

  let controlador = null;
  let numeroPeticion = 0;

  function cancelarPeticion() {
    numeroPeticion++;
    if (controlador) controlador.abort();
    controlador = null;
  }

  async function enviarMensaje(texto) {
    if (!esperandoVoz()) return;
    const mensaje = String(texto || "").trim().slice(0, 2000);
    if (!mensaje) return errorDeReconocimiento("no-speech");
    if (actual.tipo !== "pensando") transicion({ tipo: "pensando" });

    cancelarPeticion();
    const esta = numeroPeticion;
    const control = new AbortController();
    controlador = control;
    const plazo = setTimeout(() => control.abort(), TIEMPO_MAXIMO_MS);
    try {
      const respuesta = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ message: mensaje, sessionId: idSesion(), companion: acompanante || "female" }),
        signal: control.signal
      });
      if (esta !== numeroPeticion) return;
      if (respuesta.status === 401) {
        pedirCodigo("El código de acceso ya no es válido. Pide uno nuevo a quien te envió el enlace.");
        return;
      }
      if (respuesta.status === 429) {
        mostrarFallo("Vamos un poco rápido. Espera unos segundos y vuelve a hablar.");
        return;
      }
      if (!respuesta.ok) throw new Error(`HTTP ${respuesta.status}`);
      const cuerpo = await respuesta.json();
      if (esta !== numeroPeticion) return;
      if (!cuerpo || !cuerpo.data) throw new Error("Respuesta sin datos");
      sincronizar(cuerpo.data);
    } catch {
      if (esta !== numeroPeticion) return;
      mostrarFallo("No pude conectarme con el asistente. Inténtalo de nuevo en un momento.");
    } finally {
      clearTimeout(plazo);
      if (controlador === control) controlador = null;
    }
  }

  // Sin banco de respuestas local: si es una urgencia, la pista lo dice igualmente
  function mostrarFallo(mensaje) {
    const pantalla = {
      tipo: "reparacion",
      mensaje,
      pista: "Si es una urgencia, pide ayuda ahora a una persona cercana.",
      voz: mensaje
    };
    transicion(pantalla);
    reproducir(pantalla);
  }

  function sincronizar(datos) {
    const accion = String(datos.suggested_action || "").trim().toLowerCase();
    const emocion = String(datos.user_emotion || "").trim().toUpperCase() || "NEUTRA";
    const voz = String(datos.spoken_text || "").trim();
    const resumen = String(datos.screen_text || "").trim();
    const audio = typeof datos.audio_base64 === "string" && datos.audio_base64 ? datos.audio_base64 : null;
    const imagen = imagenDe(datos.image_key, emocion, accion);

    let pantalla;
    if (accion === "emergency_alert") {
      // Redundancia (CARE): la alerta se oye y se ve a la vez. Entorno de pruebas: no se avisa a nadie
      vibrar([200, 100, 200]);
      const textoPantalla = resumen || TEXTO.emergenciaPantalla;
      pantalla = { tipo: "emergencia", resumen: textoPantalla, voz: voz || textoPantalla, audio };
    } else if (accion === "retry") {
      pantalla = { tipo: "reparacion", mensaje: TEXTO.reparacion, pista: TEXTO.pistaReparacion, voz: voz || TEXTO.reparacion, audio };
    } else if (accion === "show_decision_buttons") {
      pantalla = { tipo: "decision", pregunta: resumen || TEXTO.preguntaDecision, voz: voz || TEXTO.respuestaVacia, emocion, imagen, audio };
    } else {
      const porDefecto = /TRISTEZA|SOLEDAD/.test(emocion) ? "Un lugar sereno para respirar" : "Estamos aquí contigo para acompañarte";
      pantalla = { tipo: "hablando", resumen: resumen || porDefecto, voz: voz || TEXTO.respuestaVacia, emocion, imagen, audio };
    }
    transicion(pantalla);
    reproducir(pantalla);
  }

  // ===========================================================================
  // Navegación: misma pila que la app; el "Atrás" del navegador equivale al de la pantalla
  // ===========================================================================

  const TRANSITORIAS = ["escuchando", "pensando", "acceso"];

  function transicion(nueva) {
    if (nueva.tipo === "inicio") {
      historial.length = 0;
    } else if (actual && actual !== nueva && !TRANSITORIAS.includes(actual.tipo)) {
      if (historial[historial.length - 1] !== actual) historial.push(actual);
    }
    mostrar(nueva);
  }

  function mostrar(pantalla) {
    actual = pantalla;
    clearTimeout(temporizadorEspera);
    if (pantalla.tipo === "pensando") {
      temporizadorEspera = setTimeout(() => {
        if (actual !== pantalla) return;
        pantalla.largo = true;
        render();
      }, ESPERA_LARGA_MS);
    }
    render();
    sincronizarNavegador();
  }

  function puedeVolver() {
    if (!actual || actual.tipo === "inicio" || actual.tipo === "acceso") return false;
    // La primera elección de acompañante no tiene pantalla anterior
    return !(actual.tipo === "eligiendo" && !acompanante);
  }

  function cancelarTodo() {
    cancelarPeticion();
    cancelarEscucha();
    detenerVoz();
  }

  function irAtras() {
    if (!puedeVolver()) return;
    cancelarTodo();
    vibrar();
    mostrar(historial.pop() || { tipo: "inicio" });
  }

  function irAInicio() {
    cancelarTodo();
    vibrar();
    historial.length = 0;
    mostrar({ tipo: "inicio" });
  }

  function sincronizarNavegador() {
    if (puedeVolver() && !(history.state && history.state.care)) history.pushState({ care: true }, "");
  }

  window.addEventListener("popstate", () => {
    if (!puedeVolver()) return;
    irAtras();
    sincronizarNavegador();
  });

  // Como ON_STOP en la app: oculta, la página no escucha ni habla
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) return;
    if (actual && actual.tipo === "escuchando") {
      cancelarEscucha();
      mostrar(historial.pop() || { tipo: "inicio" });
    }
    detenerVoz();
    actualizarIndicadores();
  });

  function decidir(confirmado) {
    desbloquearAudio();
    cancelarTodo();
    if (confirmado) empezarAEscuchar();
    else irAInicio();
  }

  function cambiarAcompanante() {
    cancelarTodo();
    vibrar();
    transicion({ tipo: "eligiendo", elegido: acompanante });
  }

  function elegirAcompanante(clave) {
    desbloquearAudio();
    vibrar();
    acompanante = clave;
    almacen.guardar(CLAVES.acompanante, clave);
    precargarGestos();
    transicion({ tipo: "inicio" });
  }

  async function conversacionNueva(pantalla) {
    if (!window.confirm("¿Empezar una conversación nueva? El asistente olvidará lo que hablaron.")) return;
    const anterior = idSesion();
    almacen.borrar(CLAVES.sesion);
    try {
      await fetch(`/api/chat/session/${encodeURIComponent(anterior)}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` }
      });
    } catch { /* la sesión caduca sola tras 60 min sin uso */ }
    pantalla.aviso = "Se empezó una conversación nueva.";
    render();
  }

  // ===========================================================================
  // Código de acceso: el mismo token que la app (API_ACCESS_TOKEN), por enlace o escrito
  // ===========================================================================

  function pedirCodigo(error) {
    cancelarTodo();
    token = null;
    almacen.borrar(CLAVES.token);
    historial.length = 0;
    mostrar({ tipo: "acceso", error });
  }

  async function probarCodigo(codigo, pantalla) {
    const limpio = String(codigo || "").trim();
    pantalla.valor = limpio;
    if (!limpio) {
      pantalla.error = "Escribe el código de acceso.";
      render();
      return;
    }
    pantalla.error = null;
    pantalla.comprobando = true;
    render();
    let estadoHttp = 0;
    try {
      const respuesta = await fetch(`/api/chat/session/${encodeURIComponent(idSesion())}`, {
        headers: { Authorization: `Bearer ${limpio}` }
      });
      estadoHttp = respuesta.status;
    } catch {
      estadoHttp = 0;
    }
    pantalla.comprobando = false;
    if (actual !== pantalla) return;
    if (estadoHttp === 401) {
      pantalla.error = "Ese código no es válido. Revísalo e inténtalo de nuevo.";
      render();
      return;
    }
    if (estadoHttp !== 200 && estadoHttp !== 404) {
      pantalla.error = "No pude conectar con el servidor. Inténtalo de nuevo en un momento.";
      render();
      return;
    }
    token = limpio;
    almacen.guardar(CLAVES.token, limpio);
    mostrar(acompanante ? { tipo: "inicio" } : { tipo: "eligiendo", elegido: null });
  }

  // ===========================================================================
  // Pantallas
  // ===========================================================================

  function cabecera({ conInicio = true } = {}) {
    return h("div", { clase: "cabecera" },
      h("button", { clase: "boton-nav", type: "button", onclick: irAtras, "aria-label": "Regresar a la pantalla anterior" }, "← Atrás"),
      conInicio && h("button", { clase: "boton-nav", type: "button", onclick: irAInicio, "aria-label": "Ir a la pantalla de inicio" }, "🏠 Inicio"));
  }

  // Dos imágenes superpuestas: el gesto y, encima, la pose de hablar, que estilos.css muestra
  // mientras suena la voz (clase "hablando", ver actualizarIndicadores)
  function avatar({ alerta = false, distintivo = null, alTocar = null, gesto = "neutral" } = {}) {
    const datos = ACOMPANANTES[acompanante] || ACOMPANANTES.female;
    const nombre = `Tu ${datos.etiqueta.toLowerCase()}`;
    const descripcion = gesto === "neutral" ? nombre : `${nombre}, ${GESTOS[gesto]}`;
    const contenido = [
      h("span", { clase: "poses" },
        h("img", { clase: "pose-gesto", src: imagenDelGesto(datos, gesto), alt: alTocar ? "" : descripcion, width: 128, height: 128 }),
        h("img", { clase: "pose-hablando", src: datos.gestos.hablando, alt: "", width: 128, height: 128 })),
      distintivo && h("span", { clase: "distintivo", "aria-hidden": "true" }, distintivo)
    ];
    const atributos = { clase: `avatar gesto-${gesto}${alerta ? " alerta" : ""}`, "data-avatar": true };
    if (!alTocar) return h("div", atributos, ...contenido);
    return h("button", { ...atributos, type: "button", onclick: alTocar, "aria-label": "Cambiar de acompañante" }, ...contenido);
  }

  // Complementariedad (CARE): ilustra el tema de la conversación. Equivalencia: el texto alternativo
  // la describe para quien usa un lector de pantalla. No se puede tocar (solo es apoyo visual).
  function imagenTema(clave, { compacta = false } = {}) {
    return h("figure", { clase: `imagen-tema${compacta ? " compacta" : ""}` },
      // Sin el prefijo "Imagen:" de la app: el lector de pantalla ya anuncia que es una imagen
      h("img", { src: `img/temas/${clave}.svg`, alt: IMAGENES[clave], width: 240, height: 160 }));
  }

  function microfono(escuchando) {
    return h("button", {
      clase: `microfono${escuchando ? " escuchando" : ""}`,
      type: "button",
      onclick: escuchando ? terminarDeHablar : empezarAEscuchar,
      "aria-label": escuchando
        ? "Micrófono activo, te estamos escuchando. Toca para enviar tu mensaje."
        : "Botón de micrófono. Toca para empezar a hablar."
    }, iconoMicrofono(), escuchando ? "Enviar" : (modoEscritura ? "Escribir" : "Hablar"));
  }

  function botonHablar(texto, etiqueta) {
    return h("button", { clase: "boton", type: "button", onclick: empezarAEscuchar, "aria-label": etiqueta }, texto);
  }

  function controlesDeLectura() {
    return h("div", { clase: "controles" },
      h("button", { clase: "boton secundario", type: "button", "data-control": "pausa", onclick: alternarPausa },
        pausado ? "Continuar lectura" : "Pausar lectura"),
      h("button", { clase: "boton secundario", type: "button", onclick: repetir, "aria-label": "Repetir la última respuesta desde el principio" },
        "Repetir"));
  }

  const PANTALLAS = {
    acceso(p) {
      const campo = h("input", {
        id: "codigo",
        type: "text",
        autocomplete: "off",
        autocapitalize: "off",
        spellcheck: "false",
        "data-enfocar": true
      });
      campo.value = p.valor || "";
      const entrar = () => {
        desbloquearAudio();
        probarCodigo(campo.value, p);
      };
      campo.addEventListener("keydown", (evento) => {
        if (evento.key === "Enter") entrar();
      });
      return [
        h("div", { clase: "crece" },
          h("h1", {}, "Asistente CARE"),
          h("p", { clase: "centro suave" }, "Versión web de prueba del asistente de voz para personas mayores."),
          h("label", { for: "codigo" }, "Código de acceso"),
          campo,
          p.error && h("p", { clase: "error", role: "alert" }, p.error)),
        h("button", { clase: "boton", type: "button", onclick: entrar, disabled: Boolean(p.comprobando) },
          p.comprobando ? "Comprobando…" : "Entrar")
      ];
    },

    eligiendo(p) {
      const opcion = (clave) => h("button", {
        clase: "opcion",
        type: "button",
        "aria-pressed": String(p.elegido === clave),
        onclick: () => {
          vibrar();
          p.elegido = clave;
          render();
        }
      }, h("img", { src: ACOMPANANTES[clave].imagen, alt: "", width: 150, height: 150 }), ACOMPANANTES[clave].etiqueta);
      return [
        acompanante && cabecera({ conInicio: false }),
        h("div", { clase: "crece" },
          h("h1", {}, "¿Con quién quieres conversar?"),
          h("p", { clase: "centro suave" }, "Elige a tu acompañante. Puedes cambiarlo cuando quieras."),
          h("div", { clase: "opciones" }, opcion("male"), opcion("female")),
          h("p", { clase: "centro" }, "Toca la imagen que prefieras.")),
        h("button", { clase: "boton", type: "button", disabled: !p.elegido, onclick: () => elegirAcompanante(p.elegido) },
          "Empezar a conversar")
      ];
    },

    inicio(p) {
      return [
        h("div", { clase: "crece" },
          avatar({ alTocar: cambiarAcompanante }),
          h("h1", {}, saludo()),
          h("p", { clase: "centro" }, "Estamos aquí para ayudarte"),
          microfono(false),
          h("p", { clase: "centro suave" },
            modoEscritura ? "Toca el botón verde para escribir tu mensaje." : "Toca el botón verde para comenzar a hablar."),
          p.aviso && h("p", { clase: "aviso", role: "status" }, p.aviso)),
        h("button", { clase: "boton secundario", type: "button", onclick: cambiarAcompanante }, "Cambiar acompañante"),
        h("button", { clase: "enlace", type: "button", onclick: () => conversacionNueva(p) }, "Empezar una conversación nueva")
      ];
    },

    escuchando() {
      if (modoEscritura) {
        const campo = h("textarea", {
          id: "mensaje",
          "aria-label": "Tu mensaje",
          placeholder: "Escribe aquí lo que quieras decir",
          maxlength: 2000,
          "data-enfocar": true
        });
        return [
          cabecera(),
          h("div", { clase: "crece" },
            avatar(),
            h("h1", {}, "Escribe tu mensaje"),
            h("p", { clase: "centro suave" }, "Sin reconocimiento de voz en este navegador: escribe lo que quieras decir."),
            campo),
          h("button", { clase: "boton", type: "button", onclick: () => enviarMensaje(campo.value) }, "Enviar")
        ];
      }
      return [
        cabecera(),
        h("div", { clase: "crece" },
          avatar(),
          h("h1", {}, "Te escucho"),
          h("p", { clase: "centro" }, "Habla cuando quieras, te estamos escuchando atentamente."),
          microfono(true),
          h("p", { clase: "centro suave pequeno" }, "Cuando termines, toca el botón o espera un momento."))
      ];
    },

    pensando(p) {
      return [
        cabecera(),
        h("div", { clase: "crece" },
          // Gesto pensativo mientras espera la respuesta, como ThinkingScreen en la app
          avatar({ gesto: "pensativo" }),
          h("p", { clase: "centro suave" }, "Ya te escuché"),
          h("h1", {}, p.largo ? "Sigo pensando, gracias por esperar" : "Estoy pensando qué responderte"),
          h("div", { clase: "puntos", role: "img", "aria-label": "Procesando tu mensaje" }, h("span"), h("span"), h("span")),
          h("p", { clase: "centro" }, p.largo ? "Esto puede tardar unos segundos más." : "Dame un momento, por favor."),
          h("p", { clase: "centro suave pequeno" }, "Si quieres cancelar, toca «Atrás»."))
      ];
    },

    hablando(p) {
      return [
        cabecera(),
        h("div", { clase: "crece" },
          avatar({ gesto: gestoPorEmocion(p.emocion) }),
          imagenTema(p.imagen),
          h("div", { clase: "tarjeta" }, h("h1", { clase: "resumen" }, p.resumen)),
          esAfectiva(p.emocion) && h("p", { clase: "respiracion" }, "Inhala despacio y exhala suavemente"),
          controlesDeLectura()),
        botonHablar(modoEscritura ? "Escribir" : "Hablar", "Hablar ahora con tu asistente")
      ];
    },

    decision(p) {
      return [
        cabecera(),
        h("div", { clase: "crece" },
          // Como en la app: la imagen va junto al avatar para no restar espacio a los botones Sí/No
          h("div", { clase: "fila-avatar" }, avatar({ gesto: gestoPorEmocion(p.emocion) }), imagenTema(p.imagen, { compacta: true })),
          h("div", { clase: "tarjeta" }, h("h1", { clase: "resumen" }, p.pregunta)),
          h("button", { clase: "decision si", type: "button", onclick: () => decidir(true) },
            h("strong", {}, "Sí"), h("span", {}, "Seguir hablando con tu asistente")),
          h("button", { clase: "decision no", type: "button", onclick: () => decidir(false) },
            h("strong", {}, "No"), h("span", {}, "Terminar la conversación por ahora"))),
        botonHablar(modoEscritura ? "Responder escribiendo" : "Responder hablando",
          "Responder usando tu voz en lugar de tocar la pantalla")
      ];
    },

    reparacion(p) {
      return [
        cabecera(),
        h("div", { clase: "crece" },
          avatar({ distintivo: "🔍", gesto: "pensativo" }),
          h("p", { clase: "centro suave" }, "Estamos para escucharte"),
          h("div", { clase: "tarjeta" }, h("h1", { clase: "resumen" }, p.mensaje)),
          h("p", { clase: "centro" }, p.pista),
          controlesDeLectura()),
        botonHablar(modoEscritura ? "Escribir mi mensaje" : "Volver a hablar", "Volver a hablar ahora")
      ];
    },

    emergencia(p) {
      return [
        h("div", { clase: "crece" },
          avatar({ alerta: true, distintivo: "!", gesto: "comprensivo" }),
          h("p", { clase: "etiqueta-alerta" }, "Posible emergencia"),
          h("div", { clase: "tarjeta emergencia" }, h("h1", { clase: "resumen" }, p.resumen)),
          imagenTema(IMAGEN_EMERGENCIA),
          h("p", { clase: "centro" }, "Busca ahora a un familiar, vecino o a tu cuidador. No hagas esfuerzos mientras llega la ayuda."),
          controlesDeLectura(),
          h("p", { clase: "nota-prueba" }, "Modo de prueba: esta aplicación no llama ni avisa a nadie automáticamente.")),
        botonHablar("Volver a hablar", "Volver a hablar con el asistente")
      ];
    }
  };

  function render() {
    const sereno = (actual.tipo === "hablando" || actual.tipo === "decision") && esAfectiva(actual.emocion);
    document.body.className = actual.tipo === "emergencia" ? "alerta" : (sereno ? "sereno" : "");
    raiz.replaceChildren(...[].concat(PANTALLAS[actual.tipo](actual)).filter(Boolean));
    actualizarIndicadores();
    // Foco en el campo de texto o en el título, para que un lector de pantalla anuncie el cambio
    const objetivo = raiz.querySelector("[data-enfocar]") || raiz.querySelector("h1");
    if (objetivo) {
      if (objetivo.tagName === "H1") objetivo.setAttribute("tabindex", "-1");
      objetivo.focus({ preventScroll: true });
    }
    window.scrollTo(0, 0);
  }

  function actualizarIndicadores() {
    const avatarVisible = raiz.querySelector("[data-avatar]");
    if (avatarVisible) avatarVisible.classList.toggle("hablando", !pausado);
    const botonPausa = raiz.querySelector('[data-control="pausa"]');
    if (botonPausa) botonPausa.textContent = pausado ? "Continuar lectura" : "Pausar lectura";
  }

  // ===========================================================================
  // Arranque: el enlace puede traer el código en el fragmento (#codigo=...), que nunca llega al servidor
  // ===========================================================================

  function iniciar() {
    precargarGestos();
    const codigoDelEnlace = new URLSearchParams(location.hash.slice(1)).get("codigo");
    if (location.hash) history.replaceState(null, "", location.pathname + location.search);
    if (codigoDelEnlace && codigoDelEnlace !== token) {
      const pantalla = { tipo: "acceso" };
      mostrar(pantalla);
      probarCodigo(codigoDelEnlace, pantalla);
      return;
    }
    if (!token) {
      mostrar({ tipo: "acceso" });
      return;
    }
    mostrar(acompanante ? { tipo: "inicio" } : { tipo: "eligiendo", elegido: null });
  }

  window.addEventListener("pagehide", cancelarTodo);
  // Abrir el enlace con la página ya cargada solo cambia el fragmento, sin recargarla
  window.addEventListener("hashchange", () => {
    if (new URLSearchParams(location.hash.slice(1)).has("codigo")) {
      cancelarTodo();
      historial.length = 0;
      iniciar();
    }
  });
  iniciar();
})();
