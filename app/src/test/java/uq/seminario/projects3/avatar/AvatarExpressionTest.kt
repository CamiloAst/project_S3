package uq.seminario.projects3.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uq.seminario.projects3.R
import uq.seminario.projects3.avatar.AvatarExpression.Companion.MOOD_POSE_MS
import uq.seminario.projects3.avatar.AvatarExpression.Companion.SPEAKING_CYCLE_MS

class AvatarExpressionTest {

    @Test
    fun cadaEmocionDelBackendTieneSuGesto() {
        // Las 5 categorías de `user_emotion` (careResponseSchema en chatController.js)
        assertEquals(AvatarExpression.HAPPY, AvatarExpression.forEmotion("POSITIVA"))
        assertEquals(AvatarExpression.EMPATHETIC, AvatarExpression.forEmotion("NEGATIVA_TRISTEZA_SOLEDAD"))
        assertEquals(AvatarExpression.EMPATHETIC, AvatarExpression.forEmotion("NEGATIVA_ANSIEDAD_PREOCUPACION"))
        assertEquals(AvatarExpression.NEUTRAL, AvatarExpression.forEmotion("NEUTRA"))
        assertEquals(AvatarExpression.THOUGHTFUL, AvatarExpression.forEmotion("AMBIGUA"))
    }

    @Test
    fun emocionDesconocidaOVaciaUsaGestoNeutral() {
        assertEquals(AvatarExpression.NEUTRAL, AvatarExpression.forEmotion(null))
        assertEquals(AvatarExpression.NEUTRAL, AvatarExpression.forEmotion(""))
        assertEquals(AvatarExpression.NEUTRAL, AvatarExpression.forEmotion("EUFORICA"))
        // Tolera espacios y minúsculas, como el resto del mapeo de la respuesta
        assertEquals(AvatarExpression.HAPPY, AvatarExpression.forEmotion(" positiva "))
    }

    @Test
    fun alHablarReaccionaConSuGestoYLuegoHabla() {
        val mood = AvatarExpression.EMPATHETIC
        assertEquals(mood, AvatarExpression.speakingPose(mood, 0))
        assertEquals(mood, AvatarExpression.speakingPose(mood, MOOD_POSE_MS - 1))
        assertEquals(AvatarExpression.TALKING, AvatarExpression.speakingPose(mood, MOOD_POSE_MS))
        assertEquals(AvatarExpression.TALKING, AvatarExpression.speakingPose(mood, SPEAKING_CYCLE_MS - 1))
        // En mensajes largos el gesto vuelve en cada ciclo
        assertEquals(mood, AvatarExpression.speakingPose(mood, SPEAKING_CYCLE_MS))
    }

    @Test
    fun conGestoNeutralHablaTodoElTiempo() {
        listOf(0L, MOOD_POSE_MS - 1, MOOD_POSE_MS, SPEAKING_CYCLE_MS).forEach { elapsed ->
            assertEquals(
                AvatarExpression.TALKING,
                AvatarExpression.speakingPose(AvatarExpression.NEUTRAL, elapsed)
            )
        }
    }

    @Test
    fun cadaAcompananteTieneUnaImagenDistintaPorGesto() {
        AssistantAvatar.entries.forEach { avatar ->
            val images = AvatarExpression.entries.map { avatar.imageFor(it) }
            assertEquals("Imágenes repetidas en ${avatar.name}", images.size, images.toSet().size)
            assertEquals(avatar.imageRes, avatar.imageFor(AvatarExpression.NEUTRAL))
        }
    }

    @Test
    fun imagenesDeLosGestos() {
        assertEquals(R.drawable.avatar_companera_alegre, AssistantAvatar.FEMALE.imageFor(AvatarExpression.HAPPY))
        assertEquals(R.drawable.avatar_companera_triste, AssistantAvatar.FEMALE.imageFor(AvatarExpression.EMPATHETIC))
        assertEquals(R.drawable.avatar_companera_pensativa, AssistantAvatar.FEMALE.imageFor(AvatarExpression.THOUGHTFUL))
        assertEquals(R.drawable.avatar_companera_hablando, AssistantAvatar.FEMALE.imageFor(AvatarExpression.TALKING))
        assertEquals(R.drawable.avatar_companero_alegre, AssistantAvatar.MALE.imageFor(AvatarExpression.HAPPY))
        assertEquals(R.drawable.avatar_companero_triste, AssistantAvatar.MALE.imageFor(AvatarExpression.EMPATHETIC))
        assertEquals(R.drawable.avatar_companero_pensativo, AssistantAvatar.MALE.imageFor(AvatarExpression.THOUGHTFUL))
        assertEquals(R.drawable.avatar_companero_hablando, AssistantAvatar.MALE.imageFor(AvatarExpression.TALKING))
    }

    @Test
    fun cadaGestoTieneDescripcionParaTalkBack() {
        AvatarExpression.entries.forEach { expression ->
            assertTrue("Sin descripción: ${expression.name}", expression.spokenLabel.isNotBlank())
        }
    }
}
