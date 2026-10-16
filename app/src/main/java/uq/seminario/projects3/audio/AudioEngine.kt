package uq.seminario.projects3.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Motor de audio bidireccional accesible para adultos mayores.
 * Integra:
 * 1. Speech-to-Text (STT) con tolerancia ampliada a pausas en el habla.
 * 2. Text-to-Speech (TTS) en español a velocidad reducida (0.85f).
 * 3. Retroalimentación háptica suave para confirmación física del toque.
 */
class AudioEngine(
    private val context: Context,
    private val onSpeechRecognized: (String) -> Unit,
    private val onSpeechError: (Int) -> Unit,
    private val onTtsFinished: () -> Unit
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())

    // --- Componentes Hardware / Servicios del Sistema ---
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var pendingSpeechText: String? = null

    // Servicio háptico adaptativo según versión de Android
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        // Inicialización segura del reconocedor de voz en el hilo principal
        mainHandler.post {
            initSpeechRecognizer()
        }
        // Inicialización de síntesis de voz TTS
        textToSpeech = TextToSpeech(context.applicationContext, this)
    }

    // =========================================================================
    // 1. GESTIÓN HÁPTICA (Retroalimentación física para motricidad y percepción)
    // =========================================================================

    /**
     * Emite una vibración suave (80ms) para confirmar al usuario que su toque fue recibido.
     */
    fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80L)
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "No se pudo emitir retroalimentación háptica", e)
        }
    }

    // =========================================================================
    // 2. SPEECH-TO-TEXT (STT)
    // =========================================================================

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("AudioEngine", "El servicio de reconocimiento de voz no está disponible en este dispositivo.")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("AudioEngine", "STT: Listo para escuchar")
                }

                override fun onBeginningOfSpeech() {
                    Log.d("AudioEngine", "STT: El usuario comenzó a hablar")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d("AudioEngine", "STT: Fin de entrada de voz")
                }

                override fun onError(error: Int) {
                    Log.w("AudioEngine", "STT Error código: $error")
                    // Se delega el error al ViewModel para activar el estado de reparación silencioso
                    mainHandler.post {
                        onSpeechError(error)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    if (!text.isNullOrEmpty()) {
                        Log.d("AudioEngine", "STT Resultado: $text")
                        mainHandler.post {
                            onSpeechRecognized(text)
                        }
                    } else {
                        mainHandler.post {
                            onSpeechError(SpeechRecognizer.ERROR_NO_MATCH)
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    /**
     * Inicia la captura de voz con tolerancia ampliada a pausas en adultos mayores.
     */
    fun startListening() {
        mainHandler.post {
            try {
                // Detener cualquier síntesis activa antes de escuchar
                stopSpeaking()

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-ES")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                    // Tolerancia ampliada a pausas naturales y ritmo pausado de adultos mayores
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
                }

                if (speechRecognizer == null) {
                    initSpeechRecognizer()
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("AudioEngine", "Error al iniciar STT", e)
                onSpeechError(SpeechRecognizer.ERROR_CLIENT)
            }
        }
    }

    /**
     * Detiene la escucha activa.
     */
    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w("AudioEngine", "Error al detener STT", e)
            }
        }
    }

    // =========================================================================
    // 3. TEXT-TO-SPEECH (TTS)
    // =========================================================================

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val localeSpanish = Locale.forLanguageTag("es-ES")
            val result = textToSpeech?.setLanguage(localeSpanish)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Intento con español genérico
                textToSpeech?.setLanguage(Locale.forLanguageTag("es"))
            }

            // Velocidad reducida a 0.85f para facilitar la comprensión auditiva
            textToSpeech?.setSpeechRate(0.85f)
            textToSpeech?.setPitch(1.0f)

            // Listener de fin de lectura
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("AudioEngine", "TTS: Iniciando lectura: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("AudioEngine", "TTS: Lectura completada")
                    mainHandler.post {
                        onTtsFinished()
                    }
                }

                @Deprecated("Deprecated in Java", ReplaceWith("Unit"))
                override fun onError(utteranceId: String?) {
                    Log.w("AudioEngine", "TTS: Error en lectura")
                }
            })

            isTtsInitialized = true

            // Si había un texto en cola antes de que terminara la inicialización
            pendingSpeechText?.let {
                speak(it)
                pendingSpeechText = null
            }
        } else {
            Log.e("AudioEngine", "Fallo al inicializar TextToSpeech. Código: $status")
        }
    }

    /**
     * Lee un texto en voz alta en español a 0.85f de velocidad.
     */
    fun speak(text: String) {
        if (!isTtsInitialized || textToSpeech == null) {
            pendingSpeechText = text
            return
        }

        val utteranceId = "chatbot_speech_${System.currentTimeMillis()}"
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Pausa o detiene la reproducción del TTS.
     */
    fun stopSpeaking() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.w("AudioEngine", "Error al detener TTS", e)
        }
    }

    // =========================================================================
    // 4. LIBERACIÓN DE RECURSOS (Anti-Memory Leaks)
    // =========================================================================

    /**
     * Libera de forma segura todos los recursos hardware y servicios asociados.
     */
    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.w("AudioEngine", "Error al destruir SpeechRecognizer", e)
            }

            try {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
                textToSpeech = null
                isTtsInitialized = false
            } catch (e: Exception) {
                Log.w("AudioEngine", "Error al apagar TextToSpeech", e)
            }
        }
    }
}
