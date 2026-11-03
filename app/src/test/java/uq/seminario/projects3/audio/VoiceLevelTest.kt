package uq.seminario.projects3.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceLevelTest {

    @Test
    fun silencioDaNivelCero() {
        assertEquals(0f, AudioEngine.normalizeRmsDb(-2f), 0.0001f)
        assertEquals(0f, AudioEngine.normalizeRmsDb(0f), 0.0001f)
    }

    @Test
    fun vozMediaDaNivelIntermedio() {
        assertEquals(0.5f, AudioEngine.normalizeRmsDb(5f), 0.0001f)
    }

    @Test
    fun vozMuyFuerteSeRecortaAUno() {
        assertEquals(1f, AudioEngine.normalizeRmsDb(10f), 0.0001f)
        assertEquals(1f, AudioEngine.normalizeRmsDb(25f), 0.0001f)
    }

    @Test
    fun valorInvalidoDaNivelCero() {
        assertEquals(0f, AudioEngine.normalizeRmsDb(Float.NaN), 0.0001f)
    }
}
