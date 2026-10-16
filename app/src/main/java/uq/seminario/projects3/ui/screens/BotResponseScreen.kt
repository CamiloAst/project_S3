package uq.seminario.projects3.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uq.seminario.projects3.ui.theme.ActionDarkBlue
import uq.seminario.projects3.ui.theme.BeigeBackground
import uq.seminario.projects3.ui.theme.CardSurface
import uq.seminario.projects3.ui.theme.DarkText
import uq.seminario.projects3.ui.theme.MutedDarkText
import uq.seminario.projects3.ui.theme.PrimaryDarkGreen
import uq.seminario.projects3.ui.theme.ProjectS3Theme

/**
 * Pantalla 2: Pantalla de Respuesta Complementaria (BotResponseScreen)
 *
 * Muestra el contenido que el asistente de voz está comunicando:
 * - Header claro: "Tu asistente".
 * - Tarjeta central con texto grande, en negrita y de alto contraste para facilitar la lectura.
 * - Iconografía amigable de apoyo emocional.
 * - Footer accesible con botón delineado (mínimo 64dp de altura) para pausar/continuar la lectura TTS.
 */
@Composable
fun BotResponseScreen(
    speechText: String,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BeigeBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header superior simple
            BotHeaderSection()

            // Contenedor central con scroll para textos largos y apoyo visual
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Elemento visual de apoyo: Avatar cálido del acompañante
                    SupportCompanionBadge(isSpeaking = !isPaused)

                    Spacer(modifier = Modifier.height(24.dp))

                    // Tarjeta con el texto grande y en negrita de la respuesta
                    ResponseCard(speechText = speechText)
                }
            }

            // Footer con botón delineado accesible de mínimo 64dp
            BotResponseFooterSection(
                isPaused = isPaused,
                onTogglePause = onTogglePause
            )
        }
    }
}

@Composable
private fun BotHeaderSection(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(PrimaryDarkGreen)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Tu asistente",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MutedDarkText
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SupportCompanionBadge(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(CardSurface)
            .semantics {
                contentDescription = if (isSpeaking) {
                    "El asistente está hablando en voz alta"
                } else {
                    "La lectura del asistente está en pausa"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.RecordVoiceOver,
            contentDescription = null,
            tint = PrimaryDarkGreen,
            modifier = Modifier.size(44.dp)
        )
    }
}

@Composable
private fun ResponseCard(
    speechText: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.5.dp, Color(0xFFE2DDD3))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = speechText,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 26.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                ),
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun BotResponseFooterSection(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonText = if (isPaused) "Continuar lectura" else "Pausar lectura"
    val icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause
    val accessibilityLabel = if (isPaused) {
        "Botón para continuar la reproducción de la lectura en voz alta"
    } else {
        "Botón para pausar la reproducción de la lectura en voz alta"
    }

    OutlinedButton(
        onClick = onTogglePause,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics {
                role = Role.Button
                contentDescription = accessibilityLabel
            },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.5.dp, ActionDarkBlue)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ActionDarkBlue,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = buttonText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = ActionDarkBlue
                )
            )
        }
    }
}

@Preview(name = "BotResponse - Speaking", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BotResponseScreenSpeakingPreview() {
    ProjectS3Theme {
        BotResponseScreen(
            speechText = "Claro. Tu cita médica está programada para mañana a las diez y media de la mañana en el centro médico.",
            isPaused = false,
            onTogglePause = {}
        )
    }
}

@Preview(name = "BotResponse - Paused", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BotResponseScreenPausedPreview() {
    ProjectS3Theme {
        BotResponseScreen(
            speechText = "Claro. Tu cita médica está programada para mañana a las diez y media de la mañana en el centro médico.",
            isPaused = true,
            onTogglePause = {}
        )
    }
}
