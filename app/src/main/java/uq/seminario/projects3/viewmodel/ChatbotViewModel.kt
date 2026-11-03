package uq.seminario.projects3.viewmodel

import android.app.Application
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uq.seminario.projects3.BuildConfig
import uq.seminario.projects3.audio.AudioEngine
import uq.seminario.projects3.network.ChatApiClient
import uq.seminario.projects3.network.ChatData
import uq.seminario.projects3.safety.EmergencyDetector
import uq.seminario.projects3.session.SessionIdProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Modelo de datos para las opciones de la pantalla de decisión.
 */
data class DecisionOption(
    val title: String,
    val subtitle: String
)

/**
 * Estados de UI requeridos para el chatbot de acompañamiento gerontológico (Modelo CARE).
 * Nota estricta de UX: No existe estado intermedio de "Pensando..." o "Procesando...".
 */
sealed interface UiState {
    data object Idle : UiState
    data object Listening : UiState
    data class Speaking(
        val screenText: String = "Listo para conversar contigo",
        val spokenText: String = "Qué gusto saludarte. Estoy aquí para conversar contigo con calma sobre lo que quieras. ¿Cómo va tu día?",
        val userEmotion: String = "NEUTRA",
        val isPaused: Boolean = false
    ) : UiState
    data class AwaitingDecision(
        val question: String = "¿Quieres continuar con la conversación?",
        val spokenText: String = "Aquí estoy atento para acompañarte en lo que necesites. ¿Quieres continuar con nuestra charla en este momento?",
        val userEmotion: String = "NEUTRA",
        val confirmOption: DecisionOption = DecisionOption(
            title = "Sí",
            subtitle = "Seguir hablando con tu asistente"
        ),
        val dismissOption: DecisionOption = DecisionOption(
            title = "No",
            subtitle = "Terminar la conversación por ahora"
        )
    ) : UiState
    data class Repair(
        val message: String = DEFAULT_REPAIR_MESSAGE,
        val hint: String = DEFAULT_REPAIR_HINT
    ) : UiState
    /**
     * Alerta de posible emergencia (`suggested_action = "emergency_alert"`).
     * Ambiente de pruebas: solo informa al usuario; no llama ni avisa a nadie.
     */
    data class Emergency(
        val screenText: String = EMERGENCY_SCREEN_TEXT,
        val spokenText: String = EMERGENCY_SPOKEN_TEXT
    ) : UiState

    companion object {
        const val DEFAULT_REPAIR_MESSAGE = "No he entendido tu pregunta. ¿Puedes repetirla?"
        const val DEFAULT_REPAIR_HINT = "Puedes hablar más despacio o acercarte un poco más al teléfono."

        // Mismo mensaje que EMERGENCY_SPOKEN_TEXT / EMERGENCY_SCREEN_TEXT del backend
        const val EMERGENCY_SPOKEN_TEXT = "Te estoy escuchando, mantén la calma y no hagas esfuerzos. Lo que me cuentas puede ser urgente. Por favor, pide ayuda ahora mismo a una persona cercana o a tu cuidador."
        const val EMERGENCY_SCREEN_TEXT = "Pide ayuda a alguien cercano"
    }
}

/**
 * StateHolder que orquesta la lógica de negocio, la pila de navegación (`ArrayDeque<UiState>`),
 * la sincronización multimodal del Modelo CARE (Reloj Maestro) y los controles de lectura/audio.
 */
class ChatbotViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatbotViewModel"

        // El audio neural ya llega ralentizado por la prosodia del backend (-8 % a -18 %):
        // "Pausado" lo reproduce tal cual y "Normal" lo acelera ligeramente.
        private const val NEURAL_SPEED_SLOW = 1.0f
        private const val NEURAL_SPEED_NORMAL = 1.15f

        private const val RECOGNITION_UNAVAILABLE_MESSAGE =
            "El reconocimiento de voz no está disponible en este teléfono. Puedes usar los botones de la pantalla."
        private const val RECOGNITION_UNAVAILABLE_HINT =
            "Revisa que el teléfono tenga instalado el servicio de voz de Google y vuelve a intentarlo."
    }

    // Identificador único por instalación (evita mezclar historiales entre dispositivos)
    private val sessionId: String = SessionIdProvider.get(application)

    // Historial de navegación de pantallas (Backstack de estados UI)
    private val stateHistory = ArrayDeque<UiState>()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // --- Estados de Controles de Audio y Lectura ---
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isSlowSpeech = MutableStateFlow(true)
    val isSlowSpeech: StateFlow<Boolean> = _isSlowSpeech.asStateFlow()

    private val _isVoiceMuted = MutableStateFlow(false)
    val isVoiceMuted: StateFlow<Boolean> = _isVoiceMuted.asStateFlow()

    // Vacío al iniciar: "Repetir" no debe leer contenido inventado antes de la primera respuesta
    private val _lastSpokenText = MutableStateFlow("")
    val lastSpokenText: StateFlow<String> = _lastSpokenText.asStateFlow()

    // Estado del diálogo accesible de permisos
    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    // Referencia a la petición HTTP en curso para cancelarla si el usuario interrumpe
    private var activeChatJob: Job? = null

    private var localFallbackCounter = 0

    // --- Reproductor de Voz Neural (MediaPlayer) y Reloj Maestro de Sincronía ---
    private var mediaPlayer: MediaPlayer? = null
    private var isNeuralAudioActive: Boolean = false
    private var currentTempAudioFile: File? = null
    private val _lastAudioDurationMs = MutableStateFlow(0L)
    val lastAudioDurationMs: StateFlow<Long> = _lastAudioDurationMs.asStateFlow()

    // Motor de audio bidireccional (STT + TTS Nativo de respaldo + Haptic)
    private val audioEngine = AudioEngine(
        context = application,
        onSpeechRecognized = { transcribedText ->
            handleSpeechRecognitionResult(transcribedText)
        },
        onSpeechError = { errorCode ->
            handleSpeechRecognitionError(errorCode)
        },
        onTtsFinished = {
            handleTtsPlaybackFinished()
        }
    )

    /**
     * Detiene inmediatamente tanto el MediaPlayer neural como el TextToSpeech local
     * antes de cualquier cambio de estado o interrupción del usuario.
     */
    private fun stopMediaPlayerAndTts() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error al detener/liberar MediaPlayer", e)
        } finally {
            mediaPlayer = null
            isNeuralAudioActive = false
        }
        audioEngine.stopSpeaking()
    }

    /**
     * Decodifica el audio neural en Base64 a un archivo temporal .mp3 en context.cacheDir
     * y lo reproduce inmediatamente con MediaPlayer. Si falla, recurre al TTS nativo mejorado.
     */
    private fun playNeuralAudioOrFallback(audioBase64: String?, spokenText: String) {
        stopMediaPlayerAndTts()

        if (audioBase64.isNullOrBlank()) {
            isNeuralAudioActive = false
            audioEngine.speak(spokenText)
            return
        }

        try {
            val context = getApplication<Application>()
            val cleanBase64 = audioBase64.substringAfter("base64,", audioBase64).trim()
            val audioBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            if (audioBytes.isEmpty()) {
                throw IllegalArgumentException("El buffer decodificado desde Base64 está vacío.")
            }

            currentTempAudioFile?.delete()
            val tempMp3 = File.createTempFile("neural_voice_", ".mp3", context.cacheDir)
            FileOutputStream(tempMp3).use { fos ->
                fos.write(audioBytes)
                fos.flush()
            }
            currentTempAudioFile = tempMp3

            val player = MediaPlayer()
            mediaPlayer = player
            player.apply {
                setDataSource(tempMp3.absolutePath)
                setOnCompletionListener {
                    handleTtsPlaybackFinished()
                }
                setOnErrorListener { failedPlayer, what, extra ->
                    Log.w(TAG, "Error en MediaPlayer neural (what=$what, extra=$extra). Activando TTS local.")
                    releasePlayer(failedPlayer)
                    audioEngine.speak(spokenText)
                    true
                }
                prepare()
            }
            isNeuralAudioActive = true

            if (player.duration > 0) {
                _lastAudioDurationMs.value = player.duration.toLong()
            }

            if (!_isVoiceMuted.value) {
                startNeuralPlayback(player)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al reproducir voz neural MP3 (${e.message}). Usando TTS local de respaldo.")
            mediaPlayer?.let { releasePlayer(it) }
            audioEngine.speak(spokenText)
        }
    }

    /** Libera un MediaPlayer fallido para no retener el decodificador del sistema. */
    private fun releasePlayer(player: MediaPlayer) {
        try {
            player.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error al liberar MediaPlayer", e)
        }
        if (mediaPlayer === player) {
            mediaPlayer = null
            isNeuralAudioActive = false
        }
    }

    /**
     * Inicia (o reanuda) el audio neural aplicando la velocidad elegida por el usuario.
     * La velocidad se fija después de start(): en Android, setPlaybackParams con velocidad
     * > 0 arranca la reproducción, lo que ignoraría el silencio (mute) si se hiciera antes.
     */
    private fun startNeuralPlayback(player: MediaPlayer) {
        player.start()
        try {
            player.playbackParams = player.playbackParams.setSpeed(currentNeuralSpeed())
        } catch (e: Exception) {
            Log.w(TAG, "El dispositivo no permite cambiar la velocidad del audio neural", e)
        }
    }

    private fun currentNeuralSpeed(): Float =
        if (_isSlowSpeech.value) NEURAL_SPEED_SLOW else NEURAL_SPEED_NORMAL

    // =========================================================================
    // 1. PILA DE NAVEGACIÓN (HISTORIAL ArrayDeque<UiState>, ATRÁS E INICIO)
    // =========================================================================

    /**
     * Cambia hacia un nuevo `UiState` registrando la pantalla anterior en `stateHistory`.
     */
    private fun transitionToState(newState: UiState) {
        val current = _uiState.value
        if (newState is UiState.Idle) {
            stateHistory.clear()
            _uiState.value = UiState.Idle
            return
        }

        if (current != newState) {
            // Se guarda el estado anterior en la pila (evitando apilar el estado transitorio Listening
            // cuando el micrófono pasa automáticamente a la respuesta del bot).
            if (current !is UiState.Listening) {
                val stateToPush = if (current is UiState.Speaking) {
                    current.copy(isPaused = true)
                } else {
                    current
                }
                if (stateHistory.lastOrNull() != stateToPush) {
                    stateHistory.addLast(stateToPush)
                }
            }
            _uiState.value = newState
        }
    }

    /**
     * Navega hacia la pantalla anterior del historial (`stateHistory`):
     * - Detiene inmediatamente el MediaPlayer, el TTS (`tts.stop()`) y el micrófono (`speechRecognizer.cancel()`).
     * - Restaura el estado anterior de la pila o regresa a `Idle` si está vacía.
     */
    fun navigateBack() {
        activeChatJob?.cancel()
        audioEngine.triggerHapticFeedback()
        stopMediaPlayerAndTts()
        audioEngine.stopAllAndCancel()
        _isPaused.value = true

        val previousState = if (stateHistory.isNotEmpty()) {
            stateHistory.removeLast()
        } else {
            UiState.Idle
        }

        when (previousState) {
            is UiState.Speaking -> {
                _lastSpokenText.value = previousState.spokenText
                _uiState.value = previousState.copy(isPaused = true)
            }
            is UiState.AwaitingDecision -> {
                if (previousState.spokenText.isNotBlank()) {
                    _lastSpokenText.value = previousState.spokenText
                }
                _uiState.value = previousState
            }
            is UiState.Repair -> {
                _lastSpokenText.value = previousState.message
                _uiState.value = previousState
            }
            is UiState.Emergency -> {
                _lastSpokenText.value = previousState.spokenText
                _uiState.value = previousState
            }
            else -> {
                _uiState.value = UiState.Idle
            }
        }
    }

    /**
     * Limpia el historial de navegación, detiene inmediatamente MediaPlayer, TTS y STT
     * y regresa directamente al estado inicial `Idle` (pantalla principal del botón "Hablar").
     */
    fun navigateHome() {
        activeChatJob?.cancel()
        audioEngine.triggerHapticFeedback()
        stateHistory.clear()
        stopMediaPlayerAndTts()
        audioEngine.stopAllAndCancel()
        _isPaused.value = true
        _uiState.value = UiState.Idle
    }

    // =========================================================================
    // 2. ACCIONES DE ENTRADA POR VOZ (STT), HÁPTICA Y GANCHOS DE INTERRUPCIÓN
    // =========================================================================

    fun onMicClicked(
        hasPermission: Boolean,
        onRequestPermission: () -> Unit
    ) {
        activeChatJob?.cancel()
        stopMediaPlayerAndTts()

        if (!hasPermission) {
            _showPermissionDialog.value = true
            return
        }

        when (_uiState.value) {
            is UiState.Idle, is UiState.Repair, is UiState.AwaitingDecision, is UiState.Speaking,
            is UiState.Emergency -> {
                startListeningWithHaptics()
            }
            is UiState.Listening -> {
                audioEngine.stopListening()
                audioEngine.triggerHapticFeedback()
            }
        }
    }

    fun startListeningWithHaptics() {
        stopMediaPlayerAndTts()
        audioEngine.triggerHapticFeedback()
        _isPaused.value = true
        transitionToState(UiState.Listening)
        audioEngine.startListening()
    }

    fun dismissPermissionDialog() {
        _showPermissionDialog.value = false
    }

    fun onPermissionGranted() {
        _showPermissionDialog.value = false
        startListeningWithHaptics()
    }

    // =========================================================================
    // 3. ORQUESTACIÓN STT -> BACKEND -> DISPARO SÍNCRONO (RELOJ MAESTRO FASE 1)
    // =========================================================================

    private fun handleSpeechRecognitionResult(transcribedText: String) {
        activeChatJob?.cancel()
        activeChatJob = viewModelScope.launch {
            val result = ChatApiClient.sendChatMessage(
                message = transcribedText,
                sessionId = sessionId
            )

            result.onSuccess { response ->
                val data = response.data
                // screen_text resume el tema de la conversación: solo se registra en debug
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "Respuesta CARE recibida [sessionId=${response.sessionId}]: " +
                                "action=${data.suggested_action}, emotion=${data.user_emotion}, screen='${data.screen_text}'"
                    )
                }
                executeMasterClockSync(data)
            }.onFailure { error ->
                Log.w(TAG, "Error o latencia al conectar con el backend (${error.message}). Aplicando respaldo CARE local.")
                val fallbackData = generateLocalFallbackChatData(transcribedText)
                executeMasterClockSync(fallbackData)
            }
        }
    }

    private fun executeMasterClockSync(data: ChatData) {
        val action = data.suggested_action.trim().lowercase()
        val emotion = data.user_emotion.trim().ifBlank { "NEUTRA" }
        val normEmotion = emotion.uppercase()

        data.audio_duration_ms?.let { duration ->
            if (duration > 0) {
                _lastAudioDurationMs.value = duration
            }
        }

        if (action == "emergency_alert") {
            // Redundancia (CARE): la alerta se lee en voz alta y se muestra en pantalla a la vez.
            // Ambiente de pruebas: no se realiza ninguna llamada ni aviso automático.
            val emergencySpoken = data.spoken_text.ifBlank { UiState.EMERGENCY_SPOKEN_TEXT }
            val emergencyScreen = data.screen_text.ifBlank { UiState.EMERGENCY_SCREEN_TEXT }
            _lastSpokenText.value = emergencySpoken
            _isPaused.value = _isVoiceMuted.value
            audioEngine.triggerHapticFeedback()
            playNeuralAudioOrFallback(data.audio_base64, emergencySpoken)
            transitionToState(
                UiState.Emergency(screenText = emergencyScreen, spokenText = emergencySpoken)
            )
            return
        }

        if (action == "retry") {
            val repairMessage = UiState.DEFAULT_REPAIR_MESSAGE
            val spokenHelp = data.spoken_text.ifBlank { repairMessage }
            _lastSpokenText.value = spokenHelp
            _isPaused.value = _isVoiceMuted.value
            playNeuralAudioOrFallback(data.audio_base64, spokenHelp)
            transitionToState(UiState.Repair(message = repairMessage))
            return
        }

        val spokenText = data.spoken_text.ifBlank {
            "Estoy aquí contigo para escucharte y acompañarte."
        }
        _lastSpokenText.value = spokenText

        val isSadOrLonely = normEmotion.contains("TRISTEZA") || normEmotion.contains("SOLEDAD")
        val defaultScreenText = if (isSadOrLonely) {
            "Un lugar sereno para respirar"
        } else {
            "Estamos aquí contigo para acompañarte"
        }
        val screenText = data.screen_text.ifBlank { defaultScreenText }

        val pausedState = _isVoiceMuted.value
        _isPaused.value = pausedState

        // A) Iniciar inmediatamente voz neural con MediaPlayer (si audio_base64 != null) o TTS de respaldo
        playNeuralAudioOrFallback(data.audio_base64, spokenText)

        // B) Actualizar el estado de Compose con data.screen_text según suggested_action
        when (action) {
            "show_decision_buttons" -> {
                transitionToState(
                    UiState.AwaitingDecision(
                        question = screenText.ifBlank { "¿Quieres continuar con la conversación?" },
                        spokenText = spokenText,
                        userEmotion = emotion
                    )
                )
            }
            else -> {
                transitionToState(
                    UiState.Speaking(
                        screenText = screenText,
                        spokenText = spokenText,
                        userEmotion = emotion,
                        isPaused = pausedState
                    )
                )
            }
        }
    }

    private fun handleSpeechRecognitionError(errorCode: Int) {
        Log.d(TAG, "Fallo en STT (código $errorCode). Navegando a pantalla de reparación.")
        stopMediaPlayerAndTts()
        val recognitionUnavailable = errorCode == AudioEngine.ERROR_RECOGNITION_UNAVAILABLE
        val repairMessage = if (recognitionUnavailable) {
            RECOGNITION_UNAVAILABLE_MESSAGE
        } else {
            UiState.DEFAULT_REPAIR_MESSAGE
        }
        val repairHint = if (recognitionUnavailable) {
            RECOGNITION_UNAVAILABLE_HINT
        } else {
            UiState.DEFAULT_REPAIR_HINT
        }
        _lastSpokenText.value = repairMessage
        _isPaused.value = _isVoiceMuted.value
        transitionToState(UiState.Repair(message = repairMessage, hint = repairHint))
        audioEngine.speak(repairMessage)
    }

    private fun handleTtsPlaybackFinished() {
        Log.d(TAG, "Lectura de voz completada")
        _isPaused.value = true
        val current = _uiState.value
        if (current is UiState.Speaking && !current.isPaused) {
            _uiState.value = current.copy(isPaused = true)
        }
    }

    // =========================================================================
    // 4. CONTROLES DE LECTURA Y AUDIO (MediaPlayer Neural + TTS Fallback)
    // =========================================================================

    private fun resolveCurrentSpokenText(): String {
        return when (val state = _uiState.value) {
            is UiState.Speaking -> state.spokenText.ifBlank { _lastSpokenText.value }
            is UiState.AwaitingDecision -> state.spokenText.ifBlank { _lastSpokenText.value }
            is UiState.Repair -> state.message.ifBlank { _lastSpokenText.value }
            is UiState.Emergency -> state.spokenText.ifBlank { _lastSpokenText.value }
            else -> _lastSpokenText.value
        }
    }

    fun toggleSpeakingPause() {
        audioEngine.triggerHapticFeedback()
        val currentlyPaused = _isPaused.value
        val textToSpeak = resolveCurrentSpokenText()

        if (currentlyPaused) {
            // Continuar lectura: mediaPlayer.start() desde donde quedó (o TTS de respaldo)
            if (_isVoiceMuted.value) {
                _isVoiceMuted.value = false
                audioEngine.setMuted(false)
            }
            _isPaused.value = false
            val current = _uiState.value
            if (current is UiState.Speaking) {
                _uiState.value = current.copy(isPaused = false)
            }

            val player = mediaPlayer
            if (isNeuralAudioActive && player != null) {
                try {
                    startNeuralPlayback(player)
                } catch (e: Exception) {
                    Log.w(TAG, "Error al reanudar MediaPlayer; usando TTS de respaldo", e)
                    isNeuralAudioActive = false
                    audioEngine.speak(textToSpeak)
                }
            } else {
                audioEngine.speak(textToSpeak)
            }
        } else {
            // Pausar lectura: mediaPlayer.pause() y actualiza el estado a pausado
            val player = mediaPlayer
            if (isNeuralAudioActive && player != null) {
                try {
                    if (player.isPlaying) {
                        player.pause()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error al pausar MediaPlayer", e)
                }
            }
            audioEngine.stopSpeaking()
            _isPaused.value = true
            val current = _uiState.value
            if (current is UiState.Speaking) {
                _uiState.value = current.copy(isPaused = true)
            }
        }
    }

    fun repeatLastResponse() {
        audioEngine.triggerHapticFeedback()
        val textToRepeat = resolveCurrentSpokenText()
        _lastSpokenText.value = textToRepeat

        if (_isVoiceMuted.value) {
            _isVoiceMuted.value = false
            audioEngine.setMuted(false)
        }

        _isPaused.value = false
        val current = _uiState.value
        if (current is UiState.Speaking) {
            _uiState.value = current.copy(isPaused = false)
        }

        val player = mediaPlayer
        if (isNeuralAudioActive && player != null) {
            try {
                player.seekTo(0)
                startNeuralPlayback(player)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Error al repetir MediaPlayer; usando TTS de respaldo", e)
                isNeuralAudioActive = false
            }
        }

        audioEngine.repeatLastSpoken(textToRepeat)
    }

    fun toggleSpeechSpeed() {
        audioEngine.triggerHapticFeedback()
        val newSlowState = !_isSlowSpeech.value
        _isSlowSpeech.value = newSlowState

        val targetRate = if (newSlowState) {
            AudioEngine.SPEECH_RATE_SLOW
        } else {
            AudioEngine.SPEECH_RATE_NORMAL
        }

        val textToSpeak = resolveCurrentSpokenText()
        if (!_isVoiceMuted.value) {
            _isPaused.value = false
            val current = _uiState.value
            if (current is UiState.Speaking) {
                _uiState.value = current.copy(isPaused = false)
            }
        }

        val player = mediaPlayer
        if (isNeuralAudioActive && player != null) {
            try {
                // Cambia la velocidad sin volver al inicio (start() sobre un audio terminado lo reinicia)
                if (!_isVoiceMuted.value) {
                    startNeuralPlayback(player)
                }
                return
            } catch (e: Exception) {
                isNeuralAudioActive = false
            }
        }

        audioEngine.setSpeechRateAndRestart(targetRate, textToSpeak)
    }

    fun toggleVoiceMute() {
        audioEngine.triggerHapticFeedback()
        val newMutedState = !_isVoiceMuted.value
        _isVoiceMuted.value = newMutedState

        val textToSpeak = resolveCurrentSpokenText()
        if (newMutedState) {
            val player = mediaPlayer
            if (isNeuralAudioActive && player != null) {
                try {
                    if (player.isPlaying) {
                        player.pause()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error al silenciar MediaPlayer", e)
                }
            }
            audioEngine.setMuted(true, textToSpeak)
            _isPaused.value = true
            val current = _uiState.value
            if (current is UiState.Speaking) {
                _uiState.value = current.copy(isPaused = true)
            }
        } else {
            _isPaused.value = false
            val current = _uiState.value
            if (current is UiState.Speaking) {
                _uiState.value = current.copy(isPaused = false)
            }
            val player = mediaPlayer
            if (isNeuralAudioActive && player != null) {
                try {
                    startNeuralPlayback(player)
                    return
                } catch (e: Exception) {
                    isNeuralAudioActive = false
                }
            }
            audioEngine.setMuted(false, textToSpeak)
        }
    }

    fun returnToHome() {
        navigateHome()
    }

    fun onDecisionMade(confirmed: Boolean) {
        activeChatJob?.cancel()
        stopMediaPlayerAndTts()

        if (confirmed) {
            startListeningWithHaptics()
        } else {
            navigateHome()
        }
    }

    fun resetToIdle() {
        navigateHome()
    }

    // =========================================================================
    // 5. MÉTODOS AUXILIARES PARA BARRA DE DEMOSTRACIÓN
    // =========================================================================

    fun stopListeningAndSpeak(
        screenText: String = "Listo para conversar contigo",
        spokenText: String = "Qué gusto saludarte. Estoy aquí para conversar contigo con calma sobre lo que quieras. ¿Cómo va tu día?",
        userEmotion: String = "NEUTRA"
    ) {
        activeChatJob?.cancel()
        stopMediaPlayerAndTts()
        audioEngine.cancelListening()
        _lastSpokenText.value = spokenText
        val pausedState = _isVoiceMuted.value
        _isPaused.value = pausedState
        audioEngine.speak(spokenText)
        transitionToState(
            UiState.Speaking(
                screenText = screenText,
                spokenText = spokenText,
                userEmotion = userEmotion,
                isPaused = pausedState
            )
        )
    }

    fun askDecision(
        question: String = "¿Quieres continuar con la conversación?",
        spokenText: String = "Aquí estoy atento para acompañarte en lo que necesites. ¿Quieres continuar con nuestra charla en este momento?",
        confirmOption: DecisionOption = DecisionOption("Sí", "Seguir hablando con tu asistente"),
        dismissOption: DecisionOption = DecisionOption("No", "Terminar la conversación por ahora")
    ) {
        activeChatJob?.cancel()
        stopMediaPlayerAndTts()
        audioEngine.cancelListening()
        _lastSpokenText.value = spokenText
        _isPaused.value = _isVoiceMuted.value
        audioEngine.speak(spokenText)
        transitionToState(
            UiState.AwaitingDecision(
                question = question,
                spokenText = spokenText,
                confirmOption = confirmOption,
                dismissOption = dismissOption
            )
        )
    }

    fun showEmergencyState() {
        activeChatJob?.cancel()
        stopMediaPlayerAndTts()
        audioEngine.cancelListening()
        _lastSpokenText.value = UiState.EMERGENCY_SPOKEN_TEXT
        _isPaused.value = _isVoiceMuted.value
        transitionToState(UiState.Emergency())
        audioEngine.speak(UiState.EMERGENCY_SPOKEN_TEXT)
    }

    fun showRepairState(message: String = UiState.DEFAULT_REPAIR_MESSAGE) {
        activeChatJob?.cancel()
        stopMediaPlayerAndTts()
        audioEngine.cancelListening()
        _lastSpokenText.value = message
        _isPaused.value = _isVoiceMuted.value
        transitionToState(UiState.Repair(message = message))
        audioEngine.speak(message)
    }

    // =========================================================================
    // 6. RESPALDO LOCAL DE CONTINGENCIA CON ESQUEMA MULTIMODAL C.A.R.E.
    // =========================================================================

    private fun generateLocalFallbackChatData(userInput: String): ChatData {
        val lower = userInput.lowercase()
        localFallbackCounter++

        return when {
            // Prioridad máxima: posibles emergencias también deben detectarse sin conexión
            EmergencyDetector.isCriticalRisk(userInput) -> {
                ChatData(
                    spoken_text = UiState.EMERGENCY_SPOKEN_TEXT,
                    screen_text = UiState.EMERGENCY_SCREEN_TEXT,
                    user_emotion = "NEGATIVA_ANSIEDAD_PREOCUPACION",
                    suggested_action = "emergency_alert"
                )
            }
            lower.contains("preocupad") || lower.contains("ansios") || lower.contains("olvid") ||
                    lower.contains("cita") || lower.contains("médic") || lower.contains("pastilla") ||
                    lower.contains("medicamento") || lower.contains("remedio") -> {
                if (localFallbackCounter % 2 == 0) {
                    ChatData(
                        spoken_text = "Vamos a tomar las cosas con mucha calma, paso a paso, sin ninguna prisa. Juntos podemos organizar tus citas y medicinas del día con tranquilidad. ¿Quieres que las revisemos ahora?",
                        screen_text = "Organizando tus pendientes paso a paso",
                        user_emotion = "NEGATIVA_ANSIEDAD_PREOCUPACION",
                        suggested_action = "show_decision_buttons"
                    )
                } else {
                    ChatData(
                        spoken_text = "Respira profundo y suelta el aire despacio; todo está bajo control. Para tus medicinas y citas médicas siempre seguimos el orden que dejó indicado tu doctor. ¿Te gustaría verificar tu agenda?",
                        screen_text = "Tranquilidad y orden en tu agenda",
                        user_emotion = "NEGATIVA_ANSIEDAD_PREOCUPACION",
                        suggested_action = "show_decision_buttons"
                    )
                }
            }
            lower.contains("triste") || lower.contains("solo") || lower.contains("sola") ||
                    lower.contains("aburr") || lower.contains("desanim") || lower.contains("nadie") -> {
                if (localFallbackCounter % 2 == 0) {
                    ChatData(
                        spoken_text = "Comprendo que hoy el día se sienta silencioso para ti. Quiero que tengas la certeza de que estoy aquí a tu lado para escucharte sin prisa. ¿Te gustaría recordar algún momento bonito?",
                        screen_text = "Un lugar sereno para respirar",
                        user_emotion = "NEGATIVA_TRISTEZA_SOLEDAD",
                        suggested_action = "continue"
                    )
                } else {
                    ChatData(
                        spoken_text = "Lamento mucho que estés pasando por este rato de nostalgia. Es natural extrañar la compañía, pero cuentas conmigo para charlar con calma. Respiremos juntos despacio.",
                        screen_text = "Acompañamiento cálido en todo momento",
                        user_emotion = "NEGATIVA_TRISTEZA_SOLEDAD",
                        suggested_action = "continue"
                    )
                }
            }
            lower.contains("feliz") || lower.contains("content") || lower.contains("alegr") ||
                    lower.contains("bien") || lower.contains("niet") || lower.contains("familia") -> {
                ChatData(
                    spoken_text = "Qué alegría escuchar que estás pasando un momento tan agradable hoy. Disfrutar de estas cosas buenas llena el corazón de energía. ¿Qué ha sido lo que más te ha gustado de tu día?",
                    screen_text = "Celebrando tu bienestar y alegría hoy",
                    user_emotion = "POSITIVA",
                    suggested_action = "continue"
                )
            }
            lower.contains("no sé") || lower.contains("no se") || lower.contains("tal vez") ||
                    userInput.trim().split(Regex("\\s+")).size < 2 -> {
                ChatData(
                    spoken_text = "Disculpa, creo que tu mensaje fue muy breve y quiero asegurarme de comprenderte bien. Tómate todo el tiempo que necesites para contármelo con más detalle. ¿A qué tema te refieres?",
                    screen_text = "Cuéntame un poco más de detalle",
                    user_emotion = "AMBIGUA",
                    suggested_action = "clarify"
                )
            }
            else -> {
                ChatData(
                    spoken_text = "Qué gusto saludarte y compartir este momento de conversación contigo. Estoy listo para escucharte con toda calma sobre lo que quieras platicar. ¿Cómo ha estado tu jornada?",
                    screen_text = "Listo para conversar contigo con calma",
                    user_emotion = "NEUTRA",
                    suggested_action = "continue"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeChatJob?.cancel()
        stopMediaPlayerAndTts()
        currentTempAudioFile?.delete()
        audioEngine.destroy()
        Log.d(TAG, "MediaPlayer y AudioEngine destruidos y memoria liberada.")
    }
}
