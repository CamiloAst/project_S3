package uq.seminario.projects3.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uq.seminario.projects3.avatar.AssistantAvatar
import uq.seminario.projects3.ui.theme.ProjectS3Theme

@RunWith(AndroidJUnit4::class)
class AvatarSelectionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primeraVezSinEleccionNoDejaEmpezarNiVolver() {
        var chosen: AssistantAvatar? = null
        composeRule.setContent {
            ProjectS3Theme {
                AvatarSelectionScreen(currentAvatar = null, onAvatarChosen = { chosen = it })
            }
        }

        composeRule.onNodeWithText("Empezar a conversar").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Regresar a la pantalla anterior").assertDoesNotExist()
        assertNull(chosen)
    }

    @Test
    fun tocarUnaOpcionLaMarcaYEmpezarLaConfirma() {
        var chosen: AssistantAvatar? = null
        composeRule.setContent {
            ProjectS3Theme {
                AvatarSelectionScreen(currentAvatar = null, onAvatarChosen = { chosen = it })
            }
        }

        composeRule.onNodeWithText(AssistantAvatar.MALE.label).performClick()
        composeRule.onNodeWithText(AssistantAvatar.MALE.label).assertIsSelected()
        composeRule.onNodeWithText("Elegiste a tu compañero.").assertExists()
        composeRule.onNodeWithText("Empezar a conversar").assertIsEnabled().performClick()

        assertEquals(AssistantAvatar.MALE, chosen)
    }

    @Test
    fun alCambiarMuestraElActualYPermiteVolver() {
        var backClicks = 0
        composeRule.setContent {
            ProjectS3Theme {
                AvatarSelectionScreen(
                    currentAvatar = AssistantAvatar.FEMALE,
                    onAvatarChosen = {},
                    onBackClick = { backClicks++ }
                )
            }
        }

        composeRule.onNodeWithText(AssistantAvatar.FEMALE.label).assertIsSelected()
        composeRule.onNodeWithContentDescription("Regresar a la pantalla anterior").performClick()

        assertEquals(1, backClicks)
    }
}
