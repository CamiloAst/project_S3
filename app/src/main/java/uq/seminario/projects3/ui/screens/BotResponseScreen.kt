package uq.seminario.projects3.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import uq.seminario.projects3.avatar.AssistantAvatar
import uq.seminario.projects3.avatar.AvatarExpression
import uq.seminario.projects3.images.ContextImage
import uq.seminario.projects3.ui.components.AppNavigationHeader
import uq.seminario.projects3.ui.components.AssistantAvatarBadge
import uq.seminario.projects3.ui.components.BasicControlsBar
import uq.seminario.projects3.ui.components.ContextImageCard
import uq.seminario.projects3.ui.theme.BeigeBackground
import uq.seminario.projects3.ui.theme.CardSurface
import uq.seminario.projects3.ui.theme.ContextImageBorder
import uq.seminario.projects3.ui.theme.ContextImageSurface
import uq.seminario.projects3.ui.theme.DarkText
import uq.seminario.projects3.ui.theme.MutedDarkText
import uq.seminario.projects3.ui.theme.PrimaryDarkGreen
import uq.seminario.projects3.ui.theme.ProjectS3Theme
import uq.seminario.projects3.ui.theme.SereneCalmAccent
import uq.seminario.projects3.ui.theme.SereneRecommendationBorder
import uq.seminario.projects3.ui.theme.SereneRecommendationSurface
import uq.seminario.projects3.ui.theme.SereneWarmBackground
import uq.seminario.projects3.ui.theme.SereneWarmCardSurface

private const val SERENE_RECOMMENDATION_TITLE = "Un lugar sereno para respirar con calma"

/**
 * Pantalla 2: Pantalla de Respuesta Complementaria (BotResponseScreen)
 *
 * Modelo CARE: Complementariedad (la voz lee `spoken_text` y la pantalla muestra un resumen distinto):
 * - Barra superior `AppNavigationHeader` con "← Atrás" e "🏠 Inicio".
 * - El avatar del acompañante "habla": ondas a su alrededor mientras suena la voz.
 * - Una ilustración del banco local (`image`) muestra el tema de la conversación.
 * - Muestra únicamente `screen_text` en pantalla para evitar sobrecarga cognitiva mientras TTS lee `spoken_text`.
 * - Footer limpio acorde a los mockups con "Pausar lectura / Continuar lectura" (y "Repetir") + "Hablar".
 */
@Composable
fun BotResponseScreen(
    speechText: String,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: AssistantAvatar = AssistantAvatar.DEFAULT,
    userEmotion: String = "NEUTRA",
    image: ContextImage = ContextImage.DEFAULT,
    onRepeatClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onSpeakClick: (() -> Unit)? = null
) {
    val normEmotion = userEmotion.uppercase()
    val isAffectiveSupport = normEmotion.contains("TRISTEZA") ||
            normEmotion.contains("SOLEDAD") ||
            normEmotion.contains("ANSIEDAD") ||
            normEmotion.contains("PREOCUPACION")

    val animatedBackgroundColor by animateColorAsState(
        targetValue = if (isAffectiveSupport) SereneWarmBackground else BeigeBackground,
        animationSpec = tween(durationMillis = 600),
        label = "affective_bg_color"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = animatedBackgroundColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Encabezado superior de navegación ("← Atrás" e "🏠 Inicio") + estado del asistente
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

                BotHeaderSection(
                    avatar = avatar,
                    isAffectiveSupport = isAffectiveSupport
                )
            }

            // Contenedor central con scroll para apoyo visual y tarjeta de texto breve (screen_text)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AssistantAvatarBadge(
                        avatar = avatar,
                        avatarSize = 100.dp,
                        isSpeaking = !isPaused,
                        mood = AvatarExpression.forEmotion(userEmotion),
                        borderColor = if (isAffectiveSupport) SereneWarmCardSurface else CardSurface,
                        contentDescription = if (!isPaused) {
                            "${avatar.title} está hablando en voz alta"
                        } else {
                            "La voz de ${avatar.title.lowercase()} está en pausa"
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ContextImageCard(
                        image = image,
                        imageHeight = 140.dp,
                        containerColor = if (isAffectiveSupport) SereneWarmCardSurface else ContextImageSurface,
                        borderColor = if (isAffectiveSupport) SereneRecommendationBorder else ContextImageBorder
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ResponseCard(
                        speechText = speechText,
                        isAffectiveSupport = isAffectiveSupport
                    )

                    if (isAffectiveSupport && !speechText.equals(SERENE_RECOMMENDATION_TITLE, ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(16.dp))
                        SereneRecommendationCard()
                    }
                }
            }

            // Footer limpio con Pausar/Continuar lectura + Repetir y botón "Hablar"
            BotResponseFooterSection(
                isPaused = isPaused,
                onTogglePause = onTogglePause,
                onRepeatClick = onRepeatClick,
                onSpeakClick = onSpeakClick
            )
        }
    }
}

@Composable
private fun BotHeaderSection(
    avatar: AssistantAvatar,
    isAffectiveSupport: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(if (isAffectiveSupport) SereneCalmAccent else PrimaryDarkGreen)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (isAffectiveSupport) "${avatar.title} te acompaña" else avatar.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = MutedDarkText
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResponseCard(
    speechText: String,
    isAffectiveSupport: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAffectiveSupport) SereneWarmCardSurface else CardSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (isAffectiveSupport) SereneRecommendationBorder else Color(0xFFE2DDD3)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = speechText,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SereneRecommendationCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Recomendación de bienestar: $SERENE_RECOMMENDATION_TITLE"
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = SereneRecommendationSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.5.dp, SereneRecommendationBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(SereneWarmCardSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Spa,
                    contentDescription = null,
                    tint = SereneCalmAccent,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = SERENE_RECOMMENDATION_TITLE,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 21.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Inhala despacio y exhala suavemente",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = MutedDarkText
                    )
                )
            }
        }
    }
}

@Composable
private fun BotResponseFooterSection(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onRepeatClick: () -> Unit,
    onSpeakClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicControlsBar(
            isPaused = isPaused,
            onTogglePause = onTogglePause,
            onRepeat = onRepeatClick
        )

        if (onSpeakClick != null) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSpeakClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Hablar ahora con tu asistente"
                    },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryDarkGreen,
                    contentColor = Color.White
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "Hablar",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Preview(name = "BotResponse - Speaking", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BotResponseScreenSpeakingPreview() {
    ProjectS3Theme {
        BotResponseScreen(
            speechText = "Aquí para conversar contigo",
            isPaused = false,
            image = ContextImage.MUSICA,
            onTogglePause = {},
            onSpeakClick = {}
        )
    }
}
