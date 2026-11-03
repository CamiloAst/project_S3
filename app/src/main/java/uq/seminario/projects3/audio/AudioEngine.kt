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
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Motor de audio bidireccional accesible para adultos mayores.
 * Integra:
 * 1. Speech-to-Text (STT) con tolerancia ampliada a pausas en el habla y cancelación inmediata.
 * 2. Text-to-Speech (TTS) en español con control de velocidad (0.80f Pausado / 1.0f Normal),
 *    repetición de última locución (lastSpokenText), pausa/continuación y silenciamiento (Mute).
 * 3. Retroalimentación háptica suave para confirmación física del toque.
 */
class AudioEngine(
    private val context: Context,
    private val onSpeechRecognized: (String) -> Unit,
    private val onSpeechError: (Int) -> Unit,
    private val onTtsFinished: () -> Unit
) : TextToSpeech.OnInitListener {

    companion object {
        const val SPEECH_RATE_SLOW = 0.85f
        const val SPEECH_RATE_NORMAL = 1.0f
        const val DEFAULT_WARM_PITCH = 0.95f
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // --- Componentes Hardware / Servicios del Sistema ---
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var pendingSpeechText: String? = null

    // --- Estado de Agencia y Controles de Voz del Usuario ---
    @Volatile
    private var currentSpeechRate: Float = SPEECH_RATE_SLOW

    @Volatile
    private var isVoiceMuted: Boolean = false

    @Volatile
    private var lastSpokenText: String =
        "Claro. Tu cita médica está programada para mañana a las diez y media de la mañana en el centro de salud San José."

    @Volatile
    private var activeUtteranceId: String? = null

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
     * Emite una vibración suave (70ms) para confirmar al adulto mayor que su toque fue recibido.
     */
    fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(70L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(70L)
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
     * Detiene la escucha activa procesando lo capturado hasta el momento.
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

    /**
     * Cancela inmediatamente el reconocedor de voz (speechRecognizer.cancel()).
     */
    fun cancelListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w("AudioEngine", "Error al cancelar STT", e)
            }
        }
    }

    // =========================================================================
    // 3. TEXT-TO-SPEECH (TTS) Y CONTROLES DE ACCESIBILIDAD
    // =========================================================================

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val tts = textToSpeech
            if (tts != null) {
                configureHighQualitySpanishVoice(tts)
                tts.setPitch(DEFAULT_WARM_PITCH)
                tts.setSpeechRate(0.85f)
                currentSpeechRate = 0.85f
            }

            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("AudioEngine", "TTS: Iniciando lectura: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId != null && utteranceId == activeUtteranceId) {
                        Log.d("AudioEngine", "TTS: Lectura completada: $utteranceId")
                        mainHandler.post {
                            onTtsFinished()
                        }
                    }
                }

                @Deprecated("Deprecated in Java", ReplaceWith("Unit"))
                override fun onError(utteranceId: String?) {
                    Log.w("AudioEngine", "TTS: Error en lectura: $utteranceId")
                }
            })

            isTtsInitialized = true

            pendingSpeechText?.let {
                speak(it)
                pendingSpeechText = null
            }
        } else {
            Log.e("AudioEngine", "Fallo al inicializar TextToSpeech. Código: $status")
        }
    }

    /**
     * Busca en `tts.voices` una voz en español (`es-CO`, `es-MX`, `es-US` o `es-ES`)
     * con `quality >= Voice.QUALITY_HIGH` (preferiblemente con "network" o "wavenet" en el nombre)
     * y ajusta `pitch = 0.95f` y `speechRate = 0.85f` para eliminar el timbre metálico agudo.
     */
    private fun configureHighQualitySpanishVoice(tts: TextToSpeech) {
        val preferredTags = listOf("es-CO", "es-MX", "es-US", "es-ES")

        // 1. Configurar idioma base en español (priorizando es-CO, es-MX, es-US, es-ES)
        var languageConfigured = false
        for (tag in preferredTags) {
            val res = tts.setLanguage(Locale.forLanguageTag(tag))
            if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                languageConfigured = true
                break
            }
        }
        if (!languageConfigured) {
            tts.setLanguage(Locale.forLanguageTag("es"))
        }

        // 2. Buscar voz de alta calidad (quality >= Voice.QUALITY_HIGH) con preferencia "network" / "wavenet"
        try {
            val availableVoices = tts.voices?.filterNotNull().orEmpty()
            val spanishHighQualityVoices = availableVoices.filter { voice ->
                val lang = voice.locale?.language?.lowercase(Locale.ROOT) ?: ""
                val country = voice.locale?.country?.uppercase(Locale.ROOT) ?: ""
                val localeTag = if (country.isNotEmpty()) "$lang-$country" else lang
                val isTargetSpanish = lang == "es" &&
                        (preferredTags.any { it.equals(localeTag, ignoreCase = true) } || country.isEmpty())
                isTargetSpanish && voice.quality >= Voice.QUALITY_HIGH
            }

            val selectedVoice = spanishHighQualityVoices.maxWithOrNull(
                compareBy<Voice> { voice ->
                    val lowerName = voice.name.lowercase(Locale.ROOT)
                    lowerName.contains("wavenet") || lowerName.contains("network")
                }.thenBy { voice ->
                    val tag = "${voice.locale.language}-${voice.locale.country}"
                    val idx = preferredTags.indexOfFirst { it.equals(tag, ignoreCase = true) }
                    if (idx >= 0) (preferredTags.size - idx) else 0
                }.thenBy { voice ->
                    voice.quality
                }
            )

            if (selectedVoice != null) {
                tts.voice = selectedVoice
                Log.d(
                    "AudioEngine",
                    "TTS Voz de alta calidad seleccionada: ${selectedVoice.name} " +
                            "(locale=${selectedVoice.locale}, quality=${selectedVoice.quality})"
                )
            } else {
                Log.d("AudioEngine", "No se halló voz con QUALITY_HIGH; usando voz española predeterminada con pitch=0.95f y rate=0.85f.")
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "No se pudo inspeccionar tts.voices; usando configuración base en español", e)
        }

        tts.setPitch(DEFAULT_WARM_PITCH)
        tts.setSpeechRate(0.85f)
    }

    /**
     * Reproduce un texto en voz alta en español respetando la velocidad configurada
     * y el estado de silenciamiento (Mute). Guarda siempre lastSpokenText.
     */
    fun speak(text: String) {
        val cleanText = text.trim()
        if (cleanText.isNotEmpty()) {
            lastSpokenText = cleanText
        }

        if (isVoiceMuted) {
            Log.d("AudioEngine", "TTS silenciado por el usuario. No se emite audio.")
            stopSpeaking()
            return
        }

        if (!isTtsInitialized || textToSpeech == null) {
            pendingSpeechText = cleanText
            return
        }

        textToSpeech?.setPitch(DEFAULT_WARM_PITCH)
        textToSpeech?.setSpeechRate(currentSpeechRate)
        val utteranceId = "chatbot_speech_${System.currentTimeMillis()}"
        activeUtteranceId = utteranceId
        textToSpeech?.speak(lastSpokenText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Vuelve a reproducir desde el principio el último spoken_text del bot
     * (tts.speak(lastSpokenText, TextToSpeech.QUEUE_FLUSH, ...)).
     */
    fun repeatLastSpoken(fallbackText: String = "") {
        if (fallbackText.isNotBlank()) {
            lastSpokenText = fallbackText.trim()
        }
        if (lastSpokenText.isBlank()) return

        if (isVoiceMuted) {
            isVoiceMuted = false
        }

        if (!isTtsInitialized || textToSpeech == null) {
            pendingSpeechText = lastSpokenText
            return
        }

        textToSpeech?.setPitch(DEFAULT_WARM_PITCH)
        textToSpeech?.setSpeechRate(currentSpeechRate)
        val utteranceId = "chatbot_repeat_${System.currentTimeMillis()}"
        activeUtteranceId = utteranceId
        textToSpeech?.speak(lastSpokenText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Alterna o actualiza la velocidad de voz entre 0.80f (Pausado) y 1.0f (Normal)
     * y reinicia la lectura actual con la nueva velocidad.
     */
    fun setSpeechRateAndRestart(rate: Float, fallbackText: String = "") {
        currentSpeechRate = rate
        if (fallbackText.isNotBlank()) {
            lastSpokenText = fallbackText.trim()
        }

        if (!isTtsInitialized || textToSpeech == null) {
            return
        }

        textToSpeech?.setSpeechRate(currentSpeechRate)
        if (!isVoiceMuted && lastSpokenText.isNotBlank()) {
            val utteranceId = "chatbot_speed_${System.currentTimeMillis()}"
            activeUtteranceId = utteranceId
            textToSpeech?.speak(lastSpokenText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    /**
     * Silencia o reactiva la salida de voz del TTS.
     */
    fun setMuted(muted: Boolean, resumeText: String = "") {
        isVoiceMuted = muted
        if (resumeText.isNotBlank()) {
            lastSpokenText = resumeText.trim()
        }

        if (muted) {
            stopSpeaking()
        } else if (lastSpokenText.isNotBlank()) {
            speak(lastSpokenText)
        }
    }

    fun getLastSpokenText(): String = lastSpokenText

    /**
     * Pausa o detiene inmediatamente la reproducción del TTS (tts.stop()).
     */
    fun stopSpeaking() {
        pendingSpeechText = null
        activeUtteranceId = null
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.w("AudioEngine", "Error al detener TTS", e)
        }
    }

    /**
     * Detiene inmediatamente tanto el TTS (tts.stop()) como el STT (speechRecognizer.cancel()).
     * Utilizado al pulsar "Inicio" / "Terminar".
     */
    fun stopAllAndCancel() {
        stopSpeaking()
        cancelListening()
    }

    // =========================================================================
    // 4. LIBERACIÓN DE RECURSOS (Anti-Memory Leaks)
    // =========================================================================

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
                activeUtteranceId = null
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
