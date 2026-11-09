package uq.seminario.projects3.network

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChatResponseMappingTest {

    private val gson = Gson()

    private fun parse(json: String): ChatResponse? =
        gson.fromJson(json, ChatResponseDto::class.java).toChatResponse("sesion_local")

    @Test
    fun respuestaCompletaSeConviertePorCampo() {
        val response = parse(
            """{"success":true,"sessionId":"s1","data":{"spoken_text":" Hola ","screen_text":"Saludo",
               "user_emotion":"NEUTRA","suggested_action":"continue","image_key":" musica ",
               "audio_base64":null,"audio_duration_ms":1200}}"""
        )
        assertNotNull(response)
        assertEquals("s1", response!!.sessionId)
        assertEquals("Hola", response.data.spoken_text)
        assertEquals("continue", response.data.suggested_action)
        assertEquals("musica", response.data.image_key)
        assertNull(response.data.audio_base64)
        assertEquals(1200L, response.data.audio_duration_ms)
    }

    @Test
    fun camposAusentesNoProducenNulos() {
        val response = parse("""{"success":true,"data":{"spoken_text":"Te escucho"}}""")
        assertNotNull(response)
        assertEquals("sesion_local", response!!.sessionId)
        assertEquals("", response.data.screen_text)
        assertEquals("", response.data.user_emotion)
        assertEquals("", response.data.suggested_action)
        // Un backend anterior no envía image_key: la app usará la imagen de la emoción
        assertEquals("", response.data.image_key)
    }

    @Test
    fun respuestasInutilizablesSeRechazan() {
        assertNull(parse("""{"success":false,"data":{"spoken_text":"x"}}"""))
        assertNull(parse("""{"success":true}"""))
        assertNull(parse("""{"success":true,"data":{}}"""))
        assertNull(parse("""{"error":"Unauthorized"}"""))
    }
}
