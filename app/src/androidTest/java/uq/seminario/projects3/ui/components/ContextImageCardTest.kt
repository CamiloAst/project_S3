package uq.seminario.projects3.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uq.seminario.projects3.images.ContextImage
import uq.seminario.projects3.ui.screens.BotResponseScreen
import uq.seminario.projects3.ui.screens.DecisionScreen
import uq.seminario.projects3.ui.screens.EmergencyScreen
import uq.seminario.projects3.ui.theme.ProjectS3Theme

@RunWith(AndroidJUnit4::class)
class ContextImageCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun imageLabel(image: ContextImage) = "Imagen: ${image.description}"

    /** Un pathData inválido en un vector haría fallar painterResource al componer. */
    @Test
    fun todasLasIlustracionesDelBancoSeDibujan() {
        composeRule.setContent {
            ProjectS3Theme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ContextImage.entries.forEach { ContextImageCard(image = it) }
                }
            }
        }

        ContextImage.entries.forEach { image ->
            composeRule.onNodeWithContentDescription(imageLabel(image)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun respuestaMuestraLaImagenDelTemaJuntoAlResumen() {
        composeRule.setContent {
            ProjectS3Theme {
                BotResponseScreen(
                    speechText = "Boleros que alegran la tarde",
                    isPaused = true,
                    image = ContextImage.MUSICA,
                    onTogglePause = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(imageLabel(ContextImage.MUSICA)).assertIsDisplayed()
        composeRule.onNodeWithText("Boleros que alegran la tarde").assertIsDisplayed()
    }

    @Test
    fun decisionMuestraLaImagenSinOcultarLosBotones() {
        composeRule.setContent {
            ProjectS3Theme {
                DecisionScreen(
                    question = "¿Repasamos tu cita de mañana?",
                    image = ContextImage.AGENDA,
                    onYesClick = {},
                    onNoClick = {},
                    onVoiceResponseClick = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(imageLabel(ContextImage.AGENDA)).assertIsDisplayed()
        composeRule.onNodeWithText("Sí").assertIsDisplayed()
        composeRule.onNodeWithText("No").assertIsDisplayed()
        composeRule.onNodeWithText("Responder hablando").assertIsDisplayed()
    }

    @Test
    fun emergenciaMuestraSiempreLaImagenDeAyudaCercana() {
        composeRule.setContent {
            ProjectS3Theme {
                EmergencyScreen(
                    screenText = "Pide ayuda a alguien cercano",
                    onSpeakClick = {},
                    onHomeClick = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(imageLabel(ContextImage.AYUDA_CERCANA))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
