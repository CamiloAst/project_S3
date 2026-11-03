package uq.seminario.projects3.safety

import java.text.Normalizer
import java.util.Locale

/**
 * Detector local de posibles emergencias (respaldo cuando el backend no responde).
 *
 * Replica las expresiones de CRITICAL_EMERGENCY_REGEX de backend/controllers/chatController.js
 * para que la app reaccione igual con o sin conexión. Si se modifica una lista, actualizar la otra.
 *
 * El texto se normaliza (minúsculas, sin tildes) antes de evaluar, por lo que los patrones
 * se escriben sin acentos.
 */
object EmergencyDetector {

    private val CRITICAL_EMERGENCY_REGEX = Regex(
        "me quiero morir|quiero morir|no quiero vivir|acabar con mi vida|matarme|" +
                "quitarme la vida|desaparecer para siempre|me tome muchas pastillas|" +
                "fuerte dolor en el pecho|dolor opresivo|me duele el pecho|no puedo respirar|" +
                "me falta el aire|me cai y no puedo levantarme|no siento las piernas|" +
                "se me durmio la mitad de la cara"
    )

    private val DIACRITICS_REGEX = Regex("\\p{Mn}+")

    fun isCriticalRisk(text: String): Boolean {
        if (text.isBlank()) return false
        return CRITICAL_EMERGENCY_REGEX.containsMatchIn(normalize(text))
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
}
