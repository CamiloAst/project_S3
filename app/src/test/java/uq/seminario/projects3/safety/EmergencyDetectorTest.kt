package uq.seminario.projects3.safety

import com.google.gson.JsonParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Usa los mismos casos que backend/test/emergencyDetector.test.js para garantizar que
 * el detector local y el del backend den el mismo resultado.
 */
class EmergencyDetectorTest {

    // Gradle ejecuta las pruebas unitarias con el directorio del módulo (app/) como raíz
    private val cases = JsonParser.parseString(
        File("../backend/test/emergency-cases.json").readText(Charsets.UTF_8)
    ).asJsonObject

    private fun casesFor(key: String): List<String> =
        cases.getAsJsonArray(key).map { it.asString }

    @Test
    fun detectaFrasesDePosibleEmergencia() {
        casesFor("critical").forEach { text ->
            assertTrue("Debió detectarse: \"$text\"", EmergencyDetector.isCriticalRisk(text))
        }
    }

    @Test
    fun noMarcaConversacionCotidiana() {
        casesFor("safe").forEach { text ->
            assertFalse("Falso positivo: \"$text\"", EmergencyDetector.isCriticalRisk(text))
        }
    }
}
