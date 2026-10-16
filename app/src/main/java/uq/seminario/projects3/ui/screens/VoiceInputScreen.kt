package uq.seminario.projects3.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import uq.seminario.projects3.ui.theme.BeigeBackground
import uq.seminario.projects3.ui.theme.DarkText
import uq.seminario.projects3.ui.theme.ListeningTerracotta
import uq.seminario.projects3.ui.theme.MutedDarkText
import uq.seminario.projects3.ui.theme.PrimaryDarkGreen
import uq.seminario.projects3.ui.theme.ProjectS3Theme

/**
 * Pantalla 1: Pantalla de Inicio / Escucha (VoiceInputScreen)
 *
 * Cumple estrictamente con accesibilidad gerontológica:
 * - Tipografía amplia (>=28sp títulos, >=20sp texto secundario/cuerpo).
 * - Botón central circular masivo (170dp) con touch target accesible.
 * - Alto contraste lumínico (#F6F3ED fondo, #1A1A1A texto).
 * - Transición suave sin parpadeos ni animaciones abruptas.
 */
@Composable
fun VoiceInputScreen(
    isListening: Boolean,
    onMicClick: () -> Unit,
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
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Sección Superior: Saludo y título
            HeaderGreetingSection()

            // Sección Central: Botón circular prominente del micrófono con onda sutil
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                ProminentMicButton(
                    isListening = isListening,
                    onClick = onMicClick
                )
            }

            // Sección Inferior: Indicaciones de uso claras y directas
            InstructionFooterSection(isListening = isListening)
        }
    }
}

@Composable
private fun HeaderGreetingSection(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Buenos días",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = MutedDarkText
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Estamos aquí para ayudarte",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 30.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProminentMicButton(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Configuración de animación sutil de expansión para el estado Listening (onda calmada)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_scale"
    )
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_alpha"
    )

    // Color animado suavemente para evitar transiciones bruscas
    val buttonColor by animateColorAsState(
        targetValue = if (isListening) ListeningTerracotta else PrimaryDarkGreen,
        animationSpec = tween(durationMillis = 400),
        label = "button_color"
    )

    val buttonText = if (isListening) "Te escucho" else "Hablar"
    val accessibilityDescription = if (isListening) {
        "Micrófono activo, te estamos escuchando. Toca para pausar o enviar tu mensaje."
    } else {
        "Botón de micrófono. Toca para empezar a hablar."
    }

    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // Onda de expansión sutil detrás del botón (solo visible en estado Listening)
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .scale(waveScale)
                    .clip(CircleShape)
                    .background(ListeningTerracotta.copy(alpha = waveAlpha))
            )
        }

        // Botón circular masivo principal (170dp, superando ampliamente los 150dp requeridos)
        Surface(
            modifier = Modifier
                .size(170.dp)
                .clip(CircleShape)
                .semantics {
                    role = Role.Button
                    contentDescription = accessibilityDescription
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Color.White),
                    onClick = onClick
                ),
            shape = CircleShape,
            color = buttonColor,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                    contentDescription = null, // Ya descrito en el contenedor semantics
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun InstructionFooterSection(
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val helperText = if (isListening) {
        "Habla cuando quieras, te estamos escuchando atentamente."
    } else {
        "Toca el botón verde para comenzar a hablar."
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(400)),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = helperText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 21.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Normal,
                color = DarkText
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(name = "VoiceInput - Idle", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VoiceInputScreenIdlePreview() {
    ProjectS3Theme {
        VoiceInputScreen(
            isListening = false,
            onMicClick = {}
        )
    }
}

@Preview(name = "VoiceInput - Listening", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VoiceInputScreenListeningPreview() {
    ProjectS3Theme {
        VoiceInputScreen(
            isListening = true,
            onMicClick = {}
        )
    }
}
