package uq.seminario.projects3.viewmodel

import android.app.Application
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uq.seminario.projects3.audio.AudioEngine
import uq.seminario.projects3.network.ChatApiClient

/**
 * Modelo de datos para las opciones de la pantalla de decisión.
 */
data class DecisionOption(
    val title: String,
    val subtitle: String
)

/**
 * Estados de UI requeridos para el chatbot de acompañamiento gerontológico.
 */
sealed interface UiState {
    data object Idle : UiState
    data object Listening : UiState
    data class Speaking(
        val text: String = "Claro. Tu cita médica está programada para mañana a las diez y media de la mañana en el centro de salud San José.",
        val isPaused: Boolean = false
    ) : UiState
    data class AwaitingDecision(
        val question: String = "¿Quieres continuar con la conversación?",
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
        val message: String = "No he entendido tu pregunta. ¿Puedes repetirla?"
    ) : UiState
}

/**
 * StateHolder que orquesta la lógica de negocio, los estados de UI y el motor de audio bidireccional.
 * Hereda de AndroidViewModel para acceder de forma segura al contexto de la aplicación sin fugas de memoria.
 */
class ChatbotViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Estado del diálogo accesible de permisos
    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    // Motor de audio bidireccional (STT + TTS a 0.85f + Haptic)
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

    // =========================================================================
    // ACCIONES DE ENTRADA POR VOZ (STT) Y HÁPTICA
    // =========================================================================

    /**
     * Invocado al presionar el botón principal del micrófono en VoiceInputScreen.
     * @param hasPermission Indica si el permiso de RECORD_AUDIO ya está concedido.
     * @param onRequestPermission Callback para invocar el launcher de Compose si falta el permiso.
     */
    fun onMicClicked(
        hasPermission: Boolean,
        onRequestPermission: () -> Unit
    ) {
        if (!hasPermission) {
            _showPermissionDialog.value = true
            return
        }

        when (_uiState.value) {
            is UiState.Idle, is UiState.Repair, is UiState.AwaitingDecision -> {
                startListeningWithHaptics()
            }
            is UiState.Listening -> {
                // Detener escucha manualmente si el usuario lo desea
                audioEngine.stopListening()
                audioEngine.triggerHapticFeedback()
            }
            is UiState.Speaking -> {
                // Si el bot estaba hablando y se presiona hablar, se interrumpe y se escucha
                audioEngine.stopSpeaking()
                startListeningWithHaptics()
            }
        }
    }

    /**
     * Inicia la escucha con confirmación táctil háptica.
     */
    fun startListeningWithHaptics() {
        audioEngine.triggerHapticFeedback()
        _uiState.value = UiState.Listening
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
    // RESPUESTAS Y SÍNTESIS DE VOZ (TTS)
    // =========================================================================

    private fun handleSpeechRecognitionResult(transcribedText: String) {
        viewModelScope.launch {
            val result = ChatApiClient.sendChatMessage(transcribedText)
            result.onSuccess { careData ->
                Log.d("ChatbotViewModel", "Respuesta CARE recibida: [${careData.suggestedAction}] emoción=${careData.userEmotion}")
                
                val textToSpeak = careData.spokenText.ifBlank {
                    generateBotResponse(transcribedText)
                }

                _uiState.value = UiState.Speaking(
                    text = textToSpeak,
                    isPaused = false
                )
                // Síntesis de voz automática de la respuesta generada por Gemini a 0.85f
                audioEngine.speak(textToSpeak)
            }.onFailure { error ->
                Log.w("ChatbotViewModel", "Backend offline o inaccesible (${error.message}). Usando respuesta local de respaldo.")
                val fallbackAnswer = generateBotResponse(transcribedText)
                _uiState.value = UiState.Speaking(
                    text = fallbackAnswer,
                    isPaused = false
                )
                audioEngine.speak(fallbackAnswer)
            }
        }
    }

    /**
     * Manejo de errores silencioso:
     * Si no se detecta voz o hay ruido, no se muestran pop-ups genéricos del sistema.
     * Se transiciona a la pantalla de la lupa (UiState.Repair).
     */
    private fun handleSpeechRecognitionError(errorCode: Int) {
        Log.d("ChatbotViewModel", "Manejo de error de voz silencioso: $errorCode")
        val message = when (errorCode) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                "No he entendido tu pregunta. ¿Puedes repetirla?"
            }
            SpeechRecognizer.ERROR_AUDIO -> {
                "Hubo un problema con el audio. ¿Puedes intentar de nuevo?"
            }
            else -> {
                "No te he escuchado claramente. ¿Puedes repetírmelo?"
            }
        }

        _uiState.value = UiState.Repair(message = message)
        // El asistente lee con voz comprensiva el mensaje de reparación
        audioEngine.speak(message)
    }

    private fun handleTtsPlaybackFinished() {
        // La lectura ha terminado con éxito
        Log.d("ChatbotViewModel", "Lectura del bot completada")
    }

    /**
     * Pausa o reanuda la lectura de la respuesta del bot.
     */
    fun toggleSpeakingPause() {
        val current = _uiState.value
        if (current is UiState.Speaking) {
            if (current.isPaused) {
                // Reanuda la lectura
                audioEngine.speak(current.text)
                _uiState.value = current.copy(isPaused = false)
            } else {
                // Pausa la lectura
                audioEngine.stopSpeaking()
                _uiState.value = current.copy(isPaused = true)
            }
        }
    }

    /**
     * Permite probar manualmente el estado Speaking desde la barra de prueba.
     */
    fun stopListeningAndSpeak(response: String? = null) {
        val textToSpeak = response ?: "Claro. Tu cita médica está programada para mañana a las diez y media de la mañana en el centro de salud San José."
        _uiState.value = UiState.Speaking(
            text = textToSpeak,
            isPaused = false
        )
        audioEngine.speak(textToSpeak)
    }

    /**
     * Muestra la pantalla de decisión.
     */
    fun askDecision(
        question: String = "¿Quieres continuar con la conversación?",
        confirmOption: DecisionOption = DecisionOption("Sí", "Seguir hablando con tu asistente"),
        dismissOption: DecisionOption = DecisionOption("No", "Terminar la conversación por ahora")
    ) {
        audioEngine.stopSpeaking()
        _uiState.value = UiState.AwaitingDecision(
            question = question,
            confirmOption = confirmOption,
            dismissOption = dismissOption
        )
    }

    /**
     * Procesa la decisión tomada por el usuario.
     */
    fun onDecisionMade(confirmed: Boolean) {
        audioEngine.triggerHapticFeedback()
        if (confirmed) {
            startListeningWithHaptics()
        } else {
            resetToIdle()
        }
    }

    /**
     * Restablece al estado Idle.
     */
    fun resetToIdle() {
        audioEngine.stopSpeaking()
        audioEngine.stopListening()
        _uiState.value = UiState.Idle
    }

    /**
     * Muestra manualmente el estado de reparación para pruebas.
     */
    fun showRepairState(message: String = "No he entendido tu pregunta. ¿Puedes repetirla?") {
        _uiState.value = UiState.Repair(message = message)
        audioEngine.speak(message)
    }

    // =========================================================================
    // RESPUESTAS EMPÁTICAS Y CONTEXTUALES PARA ADULTOS MAYORES
    // =========================================================================

    private fun generateBotResponse(userInput: String): String {
        val lower = userInput.lowercase()
        return when {
            lower.contains("cita") || lower.contains("médic") || lower.contains("doctor") -> {
                "Claro. Tu cita médica está programada para mañana a las diez y media de la mañana en el centro de salud San José."
            }
            lower.contains("hola") || lower.contains("buenos días") || lower.contains("buenas tardes") -> {
                "¡Hola! Qué alegría saludarte hoy. Me alegra mucho escucharte. ¿Cómo te encuentras en este momento?"
            }
            lower.contains("pastilla") || lower.contains("medicamento") || lower.contains("medicina") || lower.contains("remedio") -> {
                "Recuerda que tu próxima pastilla para la tensión te corresponde después del almuerzo con un vaso de agua."
            }
            lower.contains("triste") || lower.contains("solo") || lower.contains("sola") || lower.contains("ánimo") -> {
                "Estoy aquí contigo para acompañarte. Siempre que quieras conversar o compartir un momento, cuentas conmigo. No estás solo."
            }
            lower.contains("hora") || lower.contains("tiempo") -> {
                "Hoy es un día agradable. Recuerda tomar un poco de sol en la mañana y mantenerte bien hidratado."
            }
            else -> {
                "Te he escuchado con atención cuando dijiste: \"$userInput\". Estoy aquí contigo para lo que necesites."
            }
        }
    }

    // =========================================================================
    // PREVENCIÓN DE FUGAS DE MEMORIA
    // =========================================================================

    override fun onCleared() {
        super.onCleared()
        audioEngine.destroy()
        Log.d("ChatbotViewModel", "AudioEngine destruido y memoria liberada.")
    }
}
