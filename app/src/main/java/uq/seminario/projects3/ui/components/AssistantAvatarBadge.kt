package uq.seminario.projects3.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uq.seminario.projects3.avatar.AssistantAvatar
import uq.seminario.projects3.avatar.AvatarExpression
import uq.seminario.projects3.ui.theme.AvatarSpeakingWave
import uq.seminario.projects3.ui.theme.CardSurface
import uq.seminario.projects3.ui.theme.EmergencyRed
import uq.seminario.projects3.ui.theme.EmergencySurface
import uq.seminario.projects3.ui.theme.ListeningTerracotta
import uq.seminario.projects3.ui.theme.ProjectS3Theme

// Cuánto se expanden las ondas alrededor del avatar mientras habla (+25 %)
private const val SPEAKING_WAVE_MAX_GROWTH = 0.25f

// Cada gesto emocional nuevo entra con un pequeño rebote, como reacción a lo que le dijeron
private const val REACTION_START_SCALE = 0.92f

// Leve pulso de la pose de hablar (+3 %), a un ritmo tranquilo parecido al de las sílabas
private const val TALKING_PULSE_SCALE = 1.03f
private const val TALKING_PULSE_MS = 520

// Fundido entre gestos: lo bastante lento para no dar saltos bruscos a personas mayores
private const val POSE_CROSSFADE_MS = 450

/** Etiqueta de prueba de la imagen de cada pose (pruebas de UI). */
fun avatarPoseTestTag(pose: AvatarExpression): String = "avatar_pose_${pose.name}"

/**
 * Imagen circular del acompañante elegido, presente en todas las pantallas de la conversación.
 *
 * `mood` es el gesto que corresponde a lo que dice (ver [AvatarExpression.forEmotion]).
 * Mientras `isSpeaking` es true, el avatar alterna ese gesto con la pose de hablar y dos ondas
 * lavanda salen de él para indicar que es quien habla. El espacio de las ondas se reserva
 * siempre, así la pantalla no salta al empezar o terminar de hablar.
 * `badge` permite añadir un distintivo en la esquina inferior (por ejemplo, la alerta de emergencia).
 * Con `onClick` el avatar se puede tocar (TalkBack anuncia `onClickLabel`).
 * TalkBack lee `contentDescription` y, si no es neutral, el gesto como estado.
 */
@Composable
fun AssistantAvatarBadge(
    avatar: AssistantAvatar,
    contentDescription: String,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 120.dp,
    isSpeaking: Boolean = false,
    mood: AvatarExpression = AvatarExpression.NEUTRAL,
    waveColor: Color = AvatarSpeakingWave,
    borderColor: Color = CardSurface,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    badge: (@Composable BoxScope.() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .size(avatarSize * (1f + SPEAKING_WAVE_MAX_GROWTH))
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics {
                this.contentDescription = contentDescription
                if (mood != AvatarExpression.NEUTRAL) {
                    stateDescription = mood.spokenLabel
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isSpeaking) {
            SpeakingWaves(color = waveColor, diameter = avatarSize)
        }

        Box(modifier = Modifier.size(avatarSize)) {
            AnimatedAvatarImage(
                avatar = avatar,
                mood = mood,
                isSpeaking = isSpeaking,
                borderColor = borderColor,
                modifier = Modifier.size(avatarSize)
            )

            badge?.invoke(this)
        }
    }
}

/**
 * Imagen del acompañante animada según su gesto, sin semántica propia (la pone quien la usa).
 *
 * - Cambio de gesto: fundido entre imágenes y, si el gesto es emocional, un rebote suave.
 * - Hablando: gesto emocional al empezar y luego la pose de hablar con un leve pulso; el ciclo
 *   se repite en mensajes largos ([AvatarExpression.speakingPose]).
 * Las animaciones se leen en `graphicsLayer` para redibujar sin recomponer.
 */
@Composable
fun AnimatedAvatarImage(
    avatar: AssistantAvatar,
    modifier: Modifier = Modifier,
    mood: AvatarExpression = AvatarExpression.NEUTRAL,
    isSpeaking: Boolean = false,
    borderColor: Color = CardSurface
) {
    val pose = when {
        !isSpeaking -> mood
        mood == AvatarExpression.NEUTRAL -> AvatarExpression.TALKING
        // key: un gesto nuevo reinicia el ciclo desde la reacción
        else -> key(mood) { rememberSpeakingPose(mood) }
    }

    val reaction = remember { Animatable(1f) }
    LaunchedEffect(mood) {
        if (mood != AvatarExpression.NEUTRAL) {
            reaction.snapTo(REACTION_START_SCALE)
            reaction.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // El pulso se atenúa al dejar la pose de hablar en vez de cortarse de golpe
    val talkingAmount = animateFloatAsState(
        targetValue = if (pose == AvatarExpression.TALKING) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "avatar_talking_amount"
    )
    val talkingPulse = if (isSpeaking) rememberTalkingPulse() else null

    Box(
        modifier = modifier
            .graphicsLayer {
                val pulse = talkingPulse?.value ?: 1f
                val scale = reaction.value * (1f + (pulse - 1f) * talkingAmount.value)
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .border(width = 3.dp, color = borderColor, shape = CircleShape)
    ) {
        Crossfade(
            targetState = pose,
            animationSpec = tween(durationMillis = POSE_CROSSFADE_MS),
            label = "avatar_pose"
        ) { shownPose ->
            Image(
                painter = painterResource(avatar.imageFor(shownPose)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(avatarPoseTestTag(shownPose))
            )
        }
    }
}

/**
 * Pose actual mientras habla, según el tiempo transcurrido del ciclo. `derivedStateOf` hace
 * que solo se recomponga al cambiar de pose, no en cada fotograma.
 */
@Composable
private fun rememberSpeakingPose(mood: AvatarExpression): AvatarExpression {
    val elapsedMs = rememberInfiniteTransition(label = "avatar_speaking_cycle").animateFloat(
        initialValue = 0f,
        targetValue = AvatarExpression.SPEAKING_CYCLE_MS.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AvatarExpression.SPEAKING_CYCLE_MS.toInt(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar_speaking_elapsed"
    )
    val pose by remember(mood) {
        derivedStateOf { AvatarExpression.speakingPose(mood, elapsedMs.value.toLong()) }
    }
    return pose
}

@Composable
private fun rememberTalkingPulse(): State<Float> =
    rememberInfiniteTransition(label = "avatar_talking_pulse").animateFloat(
        initialValue = 1f,
        targetValue = TALKING_PULSE_SCALE,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = TALKING_PULSE_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar_talking_scale"
    )

/**
 * Distintivo circular para la esquina inferior derecha del avatar (alerta, lupa de reparación).
 */
@Composable
fun BoxScope.AvatarCornerBadge(
    icon: ImageVector,
    iconTint: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier
            .align(Alignment.BottomEnd)
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(width = 2.dp, color = iconTint, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

/**
 * Dos ondas desfasadas que crecen y se desvanecen. Se leen en `graphicsLayer`
 * para redibujar sin recomponer.
 */
@Composable
private fun SpeakingWaves(
    color: Color,
    diameter: Dp
) {
    val progress by rememberInfiniteTransition(label = "avatar_waves").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar_wave_progress"
    )

    repeat(2) { index ->
        Box(
            modifier = Modifier
                .size(diameter)
                .graphicsLayer {
                    val local = (progress + index * 0.5f) % 1f
                    val growth = 1f + SPEAKING_WAVE_MAX_GROWTH * local
                    scaleX = growth
                    scaleY = growth
                    alpha = 0.55f * (1f - local)
                }
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Preview(name = "Gestos de la compañera", showBackground = true, backgroundColor = 0xFFF6F3ED)
@Composable
private fun AvatarExpressionsFemalePreview() {
    ProjectS3Theme {
        Row {
            AvatarExpression.entries.forEach { expression ->
                AnimatedAvatarImage(
                    avatar = AssistantAvatar.FEMALE,
                    mood = expression,
                    modifier = Modifier.size(72.dp)
                )
            }
        }
    }
}

@Preview(name = "Gestos del compañero", showBackground = true, backgroundColor = 0xFFF6F3ED)
@Composable
private fun AvatarExpressionsMalePreview() {
    ProjectS3Theme {
        Row {
            AvatarExpression.entries.forEach { expression ->
                AnimatedAvatarImage(
                    avatar = AssistantAvatar.MALE,
                    mood = expression,
                    modifier = Modifier.size(72.dp)
                )
            }
        }
    }
}

@Preview(name = "Avatares (hablando / en silencio)", showBackground = true, backgroundColor = 0xFFF6F3ED)
@Composable
private fun AssistantAvatarBadgePreview() {
    ProjectS3Theme {
        Row {
            AssistantAvatarBadge(
                avatar = AssistantAvatar.MALE,
                contentDescription = "",
                isSpeaking = true,
                mood = AvatarExpression.HAPPY
            )
            AssistantAvatarBadge(
                avatar = AssistantAvatar.FEMALE,
                contentDescription = "",
                mood = AvatarExpression.EMPATHETIC,
                badge = {
                    AvatarCornerBadge(
                        icon = Icons.Default.Warning,
                        iconTint = EmergencyRed,
                        backgroundColor = EmergencySurface
                    )
                }
            )
            AssistantAvatarBadge(
                avatar = AssistantAvatar.FEMALE,
                contentDescription = "",
                avatarSize = 96.dp,
                mood = AvatarExpression.THOUGHTFUL,
                badge = {
                    AvatarCornerBadge(
                        icon = Icons.Default.Search,
                        iconTint = ListeningTerracotta,
                        backgroundColor = CardSurface
                    )
                }
            )
        }
    }
}
