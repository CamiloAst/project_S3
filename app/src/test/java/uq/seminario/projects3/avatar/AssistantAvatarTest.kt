package uq.seminario.projects3.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantAvatarTest {

    @Test
    fun valorGuardadoRecuperaElAcompanante() {
        AssistantAvatar.entries.forEach { avatar ->
            assertEquals(avatar, AssistantAvatar.fromStoredName(avatar.name))
        }
    }

    @Test
    fun sinValorGuardadoNoHayAcompanante() {
        assertNull(AssistantAvatar.fromStoredName(null))
    }

    @Test
    fun valorDesconocidoNoHayAcompanante() {
        assertNull(AssistantAvatar.fromStoredName("ROBOT"))
        assertNull(AssistantAvatar.fromStoredName("female"))
    }

    @Test
    fun valorDeApiCoincideConElBackend() {
        // Valores que acepta `companion` en POST /api/chat (VOICE_PROFILES en ttsService.js)
        assertEquals("male", AssistantAvatar.MALE.apiValue)
        assertEquals("female", AssistantAvatar.FEMALE.apiValue)
    }

    @Test
    fun tituloConPosesivo() {
        assertEquals("Tu compañero", AssistantAvatar.MALE.title)
        assertEquals("Tu compañera", AssistantAvatar.FEMALE.title)
    }
}
