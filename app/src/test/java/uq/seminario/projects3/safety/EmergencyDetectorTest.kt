package uq.seminario.projects3.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyDetectorTest {

    @Test
    fun detectaFrasesDeRiesgoConYSinTildes() {
        assertTrue(EmergencyDetector.isCriticalRisk("Me duele el pecho"))
        assertTrue(EmergencyDetector.isCriticalRisk("¡Me caí y no puedo levantarme!"))
        assertTrue(EmergencyDetector.isCriticalRisk("me cai y no puedo levantarme"))
        assertTrue(EmergencyDetector.isCriticalRisk("Siento que NO PUEDO RESPIRAR"))
        assertTrue(EmergencyDetector.isCriticalRisk("Se me durmió la mitad de la cara"))
    }

    @Test
    fun noMarcaConversacionCotidiana() {
        assertFalse(EmergencyDetector.isCriticalRisk("Hola, buenos días"))
        assertFalse(EmergencyDetector.isCriticalRisk("Hoy vinieron mis nietos y estoy feliz"))
        assertFalse(EmergencyDetector.isCriticalRisk("Tengo cita con el médico mañana"))
        assertFalse(EmergencyDetector.isCriticalRisk(""))
    }
}
