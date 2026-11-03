package uq.seminario.projects3.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uq.seminario.projects3.ui.theme.ProjectS3Theme

@RunWith(AndroidJUnit4::class)
class ThinkingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun muestraMensajeDeEsperaEIndicador() {
        composeRule.setContent {
            ProjectS3Theme { ThinkingScreen(onBackClick = {}, onHomeClick = {}) }
        }

        composeRule.onNodeWithText("Estoy pensando qué responderte").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Procesando tu mensaje").assertIsDisplayed()
    }

    @Test
    fun esperaLargaCambiaElMensaje() {
        composeRule.setContent {
            ProjectS3Theme { ThinkingScreen(onBackClick = {}, onHomeClick = {}) }
        }

        composeRule.mainClock.advanceTimeBy(9_000L)

        composeRule.onNodeWithText("Sigo pensando, gracias por esperar").assertIsDisplayed()
    }

    @Test
    fun atrasYInicioCancelanLaEspera() {
        var backClicks = 0
        var homeClicks = 0
        composeRule.setContent {
            ProjectS3Theme {
                ThinkingScreen(
                    onBackClick = { backClicks++ },
                    onHomeClick = { homeClicks++ }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Regresar a la pantalla anterior").performClick()
        composeRule.onNodeWithContentDescription("Ir a la pantalla de inicio").performClick()

        assertEquals(1, backClicks)
        assertEquals(1, homeClicks)
    }
}
