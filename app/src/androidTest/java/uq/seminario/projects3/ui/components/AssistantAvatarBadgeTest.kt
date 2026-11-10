package uq.seminario.projects3.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uq.seminario.projects3.avatar.AssistantAvatar
import uq.seminario.projects3.avatar.AvatarExpression
import uq.seminario.projects3.ui.screens.BotResponseScreen
import uq.seminario.projects3.ui.screens.DecisionScreen
import uq.seminario.projects3.ui.screens.EmergencyScreen
import uq.seminario.projects3.ui.theme.ProjectS3Theme

@RunWith(AndroidJUnit4::class)
class AssistantAvatarBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun hasGesture(label: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label)

    /** Una imagen que no se pudiera decodificar haría fallar painterResource al componer. */
    @Test
    fun todosLosGestosDeAmbosAcompanantesSeDibujan() {
        composeRule.setContent {
            ProjectS3Theme {
                Column {
                    AssistantAvatar.entries.forEach { avatar ->
                        Row {
                            AvatarExpression.entries.forEach { expression ->
                                AnimatedAvatarImage(
                                    avatar = avatar,
                                    mood = expression,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        AvatarExpression.entries.forEach { expression ->
            val nodes = composeRule.onAllNodesWithTag(avatarPoseTestTag(expression), useUnmergedTree = true)
            nodes.assertCountEquals(AssistantAvatar.entries.size)
            repeat(AssistantAvatar.entries.size) { nodes[it].assertIsDisplayed() }
        }
    }

    @Test
    fun alHablarReaccionaConSuGestoYLuegoHabla() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ProjectS3Theme {
                AnimatedAvatarImage(
                    avatar = AssistantAvatar.MALE,
                    mood = AvatarExpression.HAPPY,
                    isSpeaking = true,
                    modifier = Modifier.size(120.dp)
                )
            }
        }
        val happy = avatarPoseTestTag(AvatarExpression.HAPPY)
        val talking = avatarPoseTestTag(AvatarExpression.TALKING)

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onAllNodesWithTag(happy, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithTag(talking, useUnmergedTree = true).assertCountEquals(0)

        // Pasado el gesto (1,6 s) y el fundido (0,45 s) solo queda la pose de hablar
        composeRule.mainClock.advanceTimeBy(1_600)
        composeRule.onAllNodesWithTag(talking, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithTag(happy, useUnmergedTree = true).assertCountEquals(0)

        // En el ciclo siguiente vuelve el gesto alegre
        composeRule.mainClock.advanceTimeBy(AvatarExpression.SPEAKING_CYCLE_MS - 2_600 + 1_000)
        composeRule.onAllNodesWithTag(happy, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun enSilencioMantieneSuGesto() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ProjectS3Theme {
                AnimatedAvatarImage(
                    avatar = AssistantAvatar.FEMALE,
                    mood = AvatarExpression.THOUGHTFUL,
                    modifier = Modifier.size(120.dp)
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(AvatarExpression.SPEAKING_CYCLE_MS)
        composeRule.onAllNodesWithTag(avatarPoseTestTag(AvatarExpression.THOUGHTFUL), useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onAllNodesWithTag(avatarPoseTestTag(AvatarExpression.TALKING), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun respuestaPositivaMuestraGestoAlegre() {
        composeRule.setContent {
            ProjectS3Theme {
                BotResponseScreen(
                    speechText = "Qué alegría la visita de tus nietos",
                    isPaused = true,
                    userEmotion = "POSITIVA",
                    onTogglePause = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("La voz de tu compañera está en pausa")
            .assert(hasGesture("gesto alegre"))
        composeRule.onNodeWithTag(avatarPoseTestTag(AvatarExpression.HAPPY), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun decisionConTristezaMuestraGestoComprensivo() {
        composeRule.setContent {
            ProjectS3Theme {
                DecisionScreen(
                    avatar = AssistantAvatar.MALE,
                    userEmotion = "NEGATIVA_TRISTEZA_SOLEDAD",
                    onYesClick = {},
                    onNoClick = {},
                    onVoiceResponseClick = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("Tu compañero espera tu respuesta")
            .assert(hasGesture("gesto comprensivo"))
    }

    @Test
    fun emergenciaMuestraGestoComprensivo() {
        composeRule.setContent {
            ProjectS3Theme {
                EmergencyScreen(
                    screenText = "Pide ayuda a alguien cercano",
                    isPaused = true,
                    onSpeakClick = {},
                    onHomeClick = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("Alerta. Tu compañera te pide buscar ayuda")
            .assert(hasGesture("gesto comprensivo"))
    }

    @Test
    fun gestoNeutralNoAnadeEstado() {
        composeRule.setContent {
            ProjectS3Theme {
                AssistantAvatarBadge(
                    avatar = AssistantAvatar.FEMALE,
                    contentDescription = "Tu compañera"
                )
            }
        }
        composeRule.onNodeWithContentDescription("Tu compañera")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }
}
