package uq.seminario.projects3.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uq.seminario.projects3.avatar.AssistantAvatar
import uq.seminario.projects3.ui.components.AppNavigationHeader
import uq.seminario.projects3.ui.theme.BeigeBackground
import uq.seminario.projects3.ui.theme.CardSurface
import uq.seminario.projects3.ui.theme.DarkText
import uq.seminario.projects3.ui.theme.MutedDarkText
import uq.seminario.projects3.ui.theme.PrimaryDarkGreen
import uq.seminario.projects3.ui.theme.ProjectS3Theme
import kotlin.math.PI
import kotlin.math.sin

// Tras este tiempo de espera el mensaje cambia para tranquilizar al usuario
private const val LONG_WAIT_MS = 8_000L

/**
 * Pantalla de espera "Pensando" (`UiState.Thinking`)
 *
 * Se muestra desde que el usuario termina de hablar hasta que llega la respuesta del asistente
 * (transcripción + backend). Asignación (CARE): el estado del sistema se comunica en pantalla y
 * no por voz, para no solaparse con la respuesta que está por llegar.
 * - Barra superior `AppNavigationHeader` con "← Atrás" e "🏠 Inicio" para cancelar la espera.
 * - Centro con el avatar del acompañante, tres puntos animados y mensaje claro (>=28sp).
 * - Si la espera se alarga, el mensaje cambia para que el usuario sepa que la app sigue trabajando.
 */
@Composable
fun ThinkingScreen(
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: AssistantAvatar = AssistantAvatar.DEFAULT
) {
    var isTakingLong by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LONG_WAIT_MS)
        isTakingLong = true
    }

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
            // Barra superior de navegación + subtítulo sutil
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppNavigationHeader(
                    isIdle = false,
                    showBackButton = true,
                    showHomeButton = true,
                    onBackClick = onBackClick,
                    onHomeClick = onHomeClick
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Ya te escuché",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MutedDarkText
                    ),
                    textAlign = TextAlign.Center
                )
            }

            // Centro: indicador animado + mensaje de espera
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    ThinkingIndicator(avatar = avatar)

                    Spacer(modifier = Modifier.height(28.dp))

                    Crossfade(
                        targetState = isTakingLong,
                        animationSpec = tween(durationMillis = 500),
                        label = "thinking_message"
                    ) { takingLong ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (takingLong) {
                                    "Sigo pensando, gracias por esperar"
                                } else {
                                    "Estoy pensando qué responderte"
                                },
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 28.sp,
                                    lineHeight = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // TalkBack anuncia el mensaje al aparecer y cuando cambia
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = if (takingLong) {
                                    "Esto puede tardar unos segundos más."
                                } else {
                                    "Dame un momento, por favor."
                                },
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 20.sp,
                                    lineHeight = 28.sp,
                                    color = MutedDarkText
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(0.92f)
                            )
                        }
                    }
                }
            }

            // Footer: recuerda cómo cancelar la espera
            Text(
                text = "Si quieres cancelar, toca «Atrás».",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 21.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Normal,
                    color = DarkText
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }
    }
}

/**
 * El avatar del acompañante sobre un halo que "respira" con calma y, debajo, tres puntos
 * que se elevan en ola. Las animaciones se leen en `graphicsLayer` para redibujar sin recomponer.
 */
@Composable
private fun ThinkingIndicator(
    avatar: AssistantAvatar,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "thinking_transition")
    val dotsPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots_phase"
    )
    val haloBreath by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_breath"
    )

    Column(
        modifier = modifier.semantics { contentDescription = "Procesando tu mensaje" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(176.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(156.dp)
                    .graphicsLayer {
                        scaleX = haloBreath
                        scaleY = haloBreath
                    }
                    .clip(CircleShape)
                    .background(PrimaryDarkGreen.copy(alpha = 0.14f))
            )

            Image(
                painter = painterResource(avatar.imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .border(width = 3.dp, color = CardSurface, shape = CircleShape)
            )
        }

        // Margen superior para que los puntos, que suben hasta 12dp, no toquen el halo
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            // Cada punto sube durante la primera mitad de su ciclo, con desfase
                            val local = ((dotsPhase - index * 0.2f) % 1f + 1f) % 1f
                            val lift = if (local < 0.5f) sin(local * 2f * PI).toFloat() else 0f
                            translationY = -lift * 12.dp.toPx()
                            alpha = 0.45f + 0.55f * lift
                        }
                        .clip(CircleShape)
                        .background(PrimaryDarkGreen)
                )
            }
        }
    }
}

@Preview(name = "Thinking Screen", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ThinkingScreenPreview() {
    ProjectS3Theme {
        ThinkingScreen(
            onBackClick = {},
            onHomeClick = {}
        )
    }
}
