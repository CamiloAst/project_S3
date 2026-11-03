package uq.seminario.projects3.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uq.seminario.projects3.avatar.AssistantAvatar
import uq.seminario.projects3.ui.theme.ProjectS3Theme

@RunWith(AndroidJUnit4::class)
class VoiceInputScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enInicioElBotonYElAvatarAbrenLaEleccion() {
        var changeClicks = 0
        composeRule.setContent {
            ProjectS3Theme {
                VoiceInputScreen(
                    isListening = false,
                    avatar = AssistantAvatar.MALE,
                    onMicClick = {},
                    onChangeAvatarClick = { changeClicks++ }
                )
            }
        }

        composeRule.onNodeWithText("Cambiar acompañante").performClick()
        composeRule.onNodeWithContentDescription("Tu compañero").performClick()

        assertEquals(2, changeClicks)
    }

    @Test
    fun alEscucharNoSePuedeCambiarDeAcompanante() {
        var changeClicks = 0
        composeRule.setContent {
            ProjectS3Theme {
                VoiceInputScreen(
                    isListening = true,
                    avatar = AssistantAvatar.FEMALE,
                    onMicClick = {},
                    onChangeAvatarClick = { changeClicks++ }
                )
            }
        }

        composeRule.onNodeWithText("Cambiar acompañante").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Tu compañera te está escuchando").performClick()

        assertEquals(0, changeClicks)
    }
}
