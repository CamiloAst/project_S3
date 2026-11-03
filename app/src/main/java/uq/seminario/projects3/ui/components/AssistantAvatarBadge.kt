package uq.seminario.projects3.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uq.seminario.projects3.avatar.AssistantAvatar
import uq.seminario.projects3.ui.theme.AvatarSpeakingWave
import uq.seminario.projects3.ui.theme.CardSurface
import uq.seminario.projects3.ui.theme.EmergencyRed
import uq.seminario.projects3.ui.theme.EmergencySurface
import uq.seminario.projects3.ui.theme.ListeningTerracotta
import uq.seminario.projects3.ui.theme.ProjectS3Theme

// Cuánto se expanden las ondas alrededor del avatar mientras habla (+25 %)
private const val SPEAKING_WAVE_MAX_GROWTH = 0.25f

/**
 * Imagen circular del acompañante elegido, presente en todas las pantallas de la conversación.
 *
 * Mientras `isSpeaking` es true, dos ondas lavanda salen del avatar para indicar que es él
 * quien habla (complementa la voz con una señal visual). El espacio de las ondas se reserva
 * siempre, así la pantalla no salta al empezar o terminar de hablar.
 * `badge` permite añadir un distintivo en la esquina inferior (por ejemplo, la alerta de emergencia).
 * Con `onClick` el avatar se puede tocar (TalkBack anuncia `onClickLabel`).
 */
@Composable
fun AssistantAvatarBadge(
    avatar: AssistantAvatar,
    contentDescription: String,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 120.dp,
    isSpeaking: Boolean = false,
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
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        if (isSpeaking) {
            SpeakingWaves(color = waveColor, diameter = avatarSize)
        }

        Box(modifier = Modifier.size(avatarSize)) {
            Image(
                painter = painterResource(avatar.imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .border(width = 3.dp, color = borderColor, shape = CircleShape)
            )

            badge?.invoke(this)
        }
    }
}

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

@Preview(name = "Avatares (hablando / en silencio)", showBackground = true, backgroundColor = 0xFFF6F3ED)
@Composable
private fun AssistantAvatarBadgePreview() {
    ProjectS3Theme {
        Row {
            AssistantAvatarBadge(
                avatar = AssistantAvatar.MALE,
                contentDescription = "",
                isSpeaking = true
            )
            AssistantAvatarBadge(
                avatar = AssistantAvatar.FEMALE,
                contentDescription = "",
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
