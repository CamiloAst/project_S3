package uq.seminario.projects3.images

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uq.seminario.projects3.R
import java.io.File

/**
 * Comprueba que el banco de imágenes de la app tenga las mismas claves que el backend
 * (backend/test/image-keys.json, que también lee backend/test/imageCatalog.test.js).
 */
class ContextImageTest {

    // Gradle ejecuta las pruebas unitarias con el directorio del módulo (app/) como raíz
    private val shared = JsonParser.parseString(
        File("../backend/test/image-keys.json").readText(Charsets.UTF_8)
    ).asJsonObject

    @Test
    fun clavesCoincidenConElBackend() {
        val selectable = shared.getAsJsonArray("selectable").map { it.asString }
        val emergency = shared.get("emergency").asString

        assertEquals(emergency, ContextImage.AYUDA_CERCANA.apiValue)
        assertEquals(
            (selectable + emergency).toSet(),
            ContextImage.entries.map { it.apiValue }.toSet()
        )
        assertEquals(ContextImage.entries.size, selectable.size + 1)
    }

    @Test
    fun imagenPorDefectoDeCadaEmocionCoincideConElBackend() {
        shared.getAsJsonObject("defaultsByEmotion").entrySet().forEach { (emotion, key) ->
            assertEquals(emotion, key.asString, ContextImage.defaultFor(emotion).apiValue)
        }
        assertEquals(ContextImage.DEFAULT, ContextImage.defaultFor(""))
    }

    @Test
    fun cadaImagenUsaSuRecursoYTieneDescripcion() {
        ContextImage.entries.forEach { image ->
            val expectedRes = R.drawable::class.java.getField("img_${image.apiValue}").getInt(null)
            assertEquals("Recurso de ${image.name}", expectedRes, image.imageRes)
            assertTrue("Descripción vacía en ${image.name}", image.description.isNotBlank())
        }
        val descriptions = ContextImage.entries.map { it.description }
        assertEquals("Descripciones repetidas", descriptions.size, descriptions.toSet().size)
    }

    @Test
    fun claveValidaSeRespeta() {
        assertEquals(ContextImage.MUSICA, ContextImage.resolve("musica", "NEUTRA", "continue"))
        assertEquals(ContextImage.MUSICA, ContextImage.resolve(" MUSICA ", "NEUTRA", "continue"))
        assertEquals(ContextImage.FAMILIA, ContextImage.resolve("familia", "POSITIVA", "show_decision_buttons"))
    }

    @Test
    fun claveAusenteODesconocidaUsaLaEmocion() {
        assertNull(ContextImage.fromApiValue("pastillas"))
        assertEquals(ContextImage.ACOMPANAMIENTO, ContextImage.resolve("", "NEGATIVA_TRISTEZA_SOLEDAD", "continue"))
        assertEquals(ContextImage.CALMA, ContextImage.resolve(null, "NEGATIVA_ANSIEDAD_PREOCUPACION", "continue"))
        assertEquals(ContextImage.ESCUCHA, ContextImage.resolve("imagen_nueva", "AMBIGUA", "clarify"))
        assertEquals(ContextImage.SALUDO, ContextImage.resolve(null, "", "continue"))
    }

    @Test
    fun emergenciaSiempreMuestraAyudaCercanaYSoloEnEmergencias() {
        assertEquals(ContextImage.AYUDA_CERCANA, ContextImage.resolve("alegria", "POSITIVA", "emergency_alert"))
        assertEquals(ContextImage.AYUDA_CERCANA, ContextImage.resolve(null, "", " EMERGENCY_ALERT "))
        assertEquals(ContextImage.SALUDO, ContextImage.resolve("ayuda_cercana", "NEUTRA", "continue"))
    }
}
