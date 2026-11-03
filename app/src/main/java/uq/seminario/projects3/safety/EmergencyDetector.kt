package uq.seminario.projects3.safety

import java.text.Normalizer
import java.util.Locale

/**
 * Detector local de posibles emergencias (respaldo cuando el backend no responde).
 *
 * Replica EMERGENCY_PATTERNS de backend/services/emergencyDetector.js para que la app
 * reaccione igual con o sin conexión. Si se modifica una lista, actualizar la otra:
 * ambas se prueban con los mismos casos (backend/test/emergency-cases.json).
 *
 * El texto se normaliza (minúsculas, sin tildes ni puntuación, espacios simples) antes
 * de evaluar, por lo que los patrones se escriben sin acentos.
 */
object EmergencyDetector {

    private val EMERGENCY_PATTERNS = listOf(
        // Ideación suicida o autolesión
        "me quiero morir",
        "quiero morir(?:me)?",
        "no quiero (?:seguir )?viv(?:ir|iendo)",
        "acabar con mi vida",
        "matarme",
        "me (?:voy a|quiero) matar",
        "suicidar(?:me)?",
        "quitarme la vida",
        "desaparecer para siempre",
        "(?:quisiera|quiero|prefiero) estar muert[oa]",
        "no vale la pena (?:seguir )?viviendo",
        "no vale la pena vivir",
        "me tome (?:muchas|todas las) pastillas",
        "me tome (?:un|el|todo el) frasco",
        // Dolor torácico / posible infarto
        "(?:fuerte |mucho |un )?dolor (?:muy )?(?:fuerte )?en el pecho",
        "dolor opresivo",
        "me duele (?:mucho |muy fuerte )?el pecho",
        "me aprieta el pecho",
        "presion en el pecho",
        "(?:me esta dando|creo que tengo|tengo|me dio) un infarto",
        // Dificultad para respirar
        "no puedo respirar",
        "me cuesta (?:mucho )?respirar",
        "me falta (?:el )?aire",
        "no me entra (?:el )?aire",
        "me (?:estoy )?ahog(?:o|ando)",
        // Caídas
        "me cai y no (?:me )?puedo (?:levantar(?:me)?|parar(?:me)?|mover(?:me)?)",
        "me acabo de caer",
        "me cai en el (?:bano|piso|suelo|cuarto|patio)",
        "estoy (?:tirad[oa] )?en el (?:piso|suelo) y no (?:me )?puedo",
        // Posible ACV
        "no siento (?:las piernas|la pierna|el brazo|los brazos|la mitad del cuerpo|un lado)",
        "se me durmio (?:la mitad de la cara|medio lado|la mitad del cuerpo)",
        "no puedo mover (?:el brazo|la pierna|un lado|la mitad)",
        "se me (?:torcio|desvio) la boca",
        // Pérdida de conciencia o sangrado
        "me voy a desmayar",
        "me desmaye",
        "estoy sangrando mucho",
        "(?:sale|tengo|hay) mucha sangre"
    )

    private val CRITICAL_EMERGENCY_REGEX =
        Regex("(?:^|\\s)(?:${EMERGENCY_PATTERNS.joinToString("|")})(?=\\s|$)")

    private val DIACRITICS_REGEX = Regex("\\p{Mn}+")
    private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9\\s]")
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun isCriticalRisk(text: String): Boolean {
        val normalized = normalize(text)
        return normalized.isNotEmpty() && CRITICAL_EMERGENCY_REGEX.containsMatchIn(normalized)
    }

    /** Equivalente a normalizeForEmergency() del backend. */
    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
            .replace(NON_ALPHANUMERIC_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
}
