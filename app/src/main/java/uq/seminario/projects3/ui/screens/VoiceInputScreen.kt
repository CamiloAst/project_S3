package uq.seminario.projects3.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import uq.seminario.projects3.avatar.AssistantAvatar
import uq.seminario.projects3.ui.components.AppNavigationHeader
import uq.seminario.projects3.ui.components.AssistantAvatarBadge
import uq.seminario.projects3.ui.theme.BeigeBackground
import uq.seminario.projects3.ui.theme.CardSurface
import uq.seminario.projects3.ui.theme.DarkText
import uq.seminario.projects3.ui.theme.ListeningTerracotta
import uq.seminario.projects3.ui.theme.MutedDarkText
import uq.seminario.projects3.ui.theme.PrimaryDarkGreen
import uq.seminario.projects3.ui.theme.ProjectS3Theme
import kotlin.math.PI
import kotlin.math.sin

// Altura relativa de cada barra del ecualizador (la central reacciona más a la voz)
private val VOICE_BAR_WEIGHTS = floatArrayOf(0.55f, 0.85f, 1f, 0.85f, 0.55f)
// Altura mínima de las barras en silencio, para que el icono siga reconociéndose
private const val VOICE_BAR_MIN_FRACTION = 0.2f
// Cuánto crece el halo detrás del botón con la voz más fuerte (+30 %)
private const val VOICE_HALO_MAX_GROWTH = 0.3f
// Alto del botón "Cambiar acompañante" (y del hueco que lo sustituye al escuchar)
private val CHANGE_AVATAR_BUTTON_HEIGHT = 56.dp

/**
 * Pantalla 1: Pantalla de Inicio / Escucha (VoiceInputScreen)
 *
 * Cumple estrictamente con accesibilidad gerontológica y el diseño original de los mockups:
 * - Barra superior `AppNavigationHeader`:
 *     * En `Idle`: muestra botón discreto "Salir" en la esquina superior derecha.
 *     * En `Listening`: muestra "← Atrás" e "Inicio" para cancelar o regresar con seguridad.
 * - Avatar del acompañante elegido bajo la barra; en `Idle`, el botón "Cambiar acompañante"
 *   (o tocar el avatar) abre su elección.
 * - Tipografía amplia (>=28sp títulos, >=20sp cuerpo).
 * - Botón central circular masivo (170dp) con confirmación háptica.
 * - En `Listening`, las barras del icono y el halo del botón se mueven con el volumen de la voz
 *   (`voiceLevel`, de 0 a 1) para que el usuario vea que se le está escuchando.
 */
@Composable
fun VoiceInputScreen(
    isListening: Boolean,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: AssistantAvatar = AssistantAvatar.DEFAULT,
    voiceLevel: () -> Float = { 0f },
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onExitClick: () -> Unit = {},
    onChangeAvatarClick: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BeigeBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Sección Superior: Barra de navegación + Saludo y título
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppNavigationHeader(
                    isIdle = !isListening,
                    showBackButton = isListening,
                    showHomeButton = isListening,
                    onBackClick = onBackClick,
                    onHomeClick = onHomeClick,
                    onExitClick = onExitClick
                )

                Spacer(modifier = Modifier.height(4.dp))

                val changeAvatar = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onChangeAvatarClick()
                }

                // En inicio, tocar el avatar también abre la elección (Equivalencia con el botón)
                AssistantAvatarBadge(
                    avatar = avatar,
                    avatarSize = 104.dp,
                    contentDescription = if (isListening) {
                        "${avatar.title} te está escuchando"
                    } else {
                        avatar.title
                    },
                    onClick = if (isListening) null else changeAvatar,
                    onClickLabel = "cambiar de acompañante"
                )

                // "Cambiar acompañante" solo en inicio; al escuchar se reserva su alto para que
                // el botón del micrófono no salte durante la transición entre ambas pantallas
                if (isListening) {
                    Spacer(modifier = Modifier.height(CHANGE_AVATAR_BUTTON_HEIGHT))
                } else {
                    OutlinedButton(
                        onClick = changeAvatar,
                        modifier = Modifier.heightIn(min = CHANGE_AVATAR_BUTTON_HEIGHT),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = PrimaryDarkGreen
                        ),
                        border = BorderStroke(2.dp, PrimaryDarkGreen),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = PrimaryDarkGreen,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cambiar acompañante",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryDarkGreen
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                HeaderGreetingSection()
            }

            // Sección Central: Botón circular prominente del micrófono con onda sutil
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                ProminentMicButton(
                    isListening = isListening,
                    voiceLevel = voiceLevel,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMicClick()
                    }
                )
            }

            // Sección Inferior: Indicaciones de uso claras y limpias como en el mockup original
            InstructionFooterSection(isListening = isListening)
        }
    }
}

@Composable
private fun HeaderGreetingSection(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
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

        Spacer(modifier = Modifier.height(8.dp))

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
    voiceLevel: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Nivel de voz suavizado con un resorte: sube y baja sin saltos bruscos. Se lee solo en
    // graphicsLayer/Canvas, así que los cambios de volumen redibujan sin recomponer
    val smoothedVoiceLevel = remember { Animatable(0f) }
    val currentVoiceLevel by rememberUpdatedState(voiceLevel)
    LaunchedEffect(Unit) {
        snapshotFlow { currentVoiceLevel() }.collectLatest { level ->
            smoothedVoiceLevel.animateTo(
                targetValue = level,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

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
        if (isListening) {
            // Onda constante: indica que el micrófono sigue abierto aunque haya silencio
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .scale(waveScale)
                    .clip(CircleShape)
                    .background(ListeningTerracotta.copy(alpha = waveAlpha))
            )

            // Halo que asoma detrás del botón y crece con el volumen de la voz
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .graphicsLayer {
                        val level = smoothedVoiceLevel.value
                        val growth = 1f + VOICE_HALO_MAX_GROWTH * level
                        scaleX = growth
                        scaleY = growth
                        alpha = 0.4f + 0.6f * level
                    }
                    .clip(CircleShape)
                    .background(ListeningTerracotta.copy(alpha = 0.35f))
            )
        }

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
                if (isListening) {
                    VoiceLevelBars(
                        level = { smoothedVoiceLevel.value },
                        color = Color.White,
                        modifier = Modifier.size(width = 60.dp, height = 54.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                }

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

/**
 * Icono de escucha en forma de ecualizador: la altura de las barras sigue el volumen de la voz.
 * En silencio quedan bajas y quietas; con voz se mueven con una ligera ondulación desfasada.
 */
@Composable
private fun VoiceLevelBars(
    level: () -> Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val wobblePhase by rememberInfiniteTransition(label = "bars_transition").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bars_wobble"
    )

    Canvas(modifier = modifier) {
        val barCount = VOICE_BAR_WEIGHTS.size
        val gap = size.width * 0.08f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val currentLevel = level()

        VOICE_BAR_WEIGHTS.forEachIndexed { index, weight ->
            val wobble = 0.8f + 0.2f * sin(wobblePhase + index * 1.3f)
            val fraction = VOICE_BAR_MIN_FRACTION +
                    (1f - VOICE_BAR_MIN_FRACTION) * currentLevel * weight * wobble
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
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
            .padding(bottom = 16.dp)
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
            onMicClick = {},
            voiceLevel = { 0.6f }
        )
    }
}
