package uq.seminario.projects3.ui.screens

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
import uq.seminario.projects3.ui.components.AppNavigationHeader
import uq.seminario.projects3.ui.components.AssistantAvatarBadge
import uq.seminario.projects3.ui.components.BasicControlsBar
import uq.seminario.projects3.ui.theme.DarkText
import uq.seminario.projects3.ui.theme.MutedDarkText
import uq.seminario.projects3.ui.theme.PrimaryDarkGreen
import uq.seminario.projects3.ui.theme.ProjectS3Theme
import uq.seminario.projects3.ui.theme.SereneCalmAccent
import uq.seminario.projects3.ui.theme.SereneRecommendationBorder
import uq.seminario.projects3.ui.theme.SereneRecommendationSurface
import uq.seminario.projects3.ui.theme.SereneWarmBackground
import uq.seminario.projects3.ui.theme.SereneWarmCardSurface

private const val DEFAULT_RECOMMENDATION_TITLE = "Un lugar sereno para respirar con calma"
private const val DEFAULT_RECOMMENDATION_SUBTITLE = "Inhala despacio y exhala suavemente"

/**
 * Pantalla de Recomendación y Acompañamiento Afectivo (RecommendationScreen).
 *
 * Modelo CARE: Complementariedad (voz + resumen visual) y Equivalencia (responder por voz o con botones):
 * - Barra superior `AppNavigationHeader` con "← Atrás" e "🏠 Inicio".
 * - El avatar del acompañante muestra ondas mientras habla.
 * - Muestra el apoyo visual corto (`screenText`) junto a la tarjeta de recomendación de bienestar.
 * - Footer limpio con "Pausar lectura / Continuar lectura" (y "Repetir") + botón "Hablar".
 */
@Composable
fun RecommendationScreen(
    speechText: String,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: AssistantAvatar = AssistantAvatar.DEFAULT,
    recommendationTitle: String = DEFAULT_RECOMMENDATION_TITLE,
    recommendationSubtitle: String = DEFAULT_RECOMMENDATION_SUBTITLE,
    onRepeatClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onSpeakClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SereneWarmBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Barra superior de navegación + título de compañía
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(SereneCalmAccent)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${avatar.title} te acompaña",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 23.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MutedDarkText
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Contenido central con texto complementario y tarjeta de recomendación serena
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
                    AssistantAvatarBadge(
                        avatar = avatar,
                        avatarSize = 112.dp,
                        isSpeaking = !isPaused,
                        borderColor = SereneWarmCardSurface,
                        contentDescription = if (!isPaused) {
                            "${avatar.title} te acompaña hablando en voz alta"
                        } else {
                            "La voz de ${avatar.title.lowercase()} está en pausa"
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = SereneWarmCardSurface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        border = BorderStroke(1.5.dp, SereneRecommendationBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = speechText,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 27.sp,
                                    lineHeight = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Recomendación de bienestar: $recommendationTitle"
                            },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = SereneRecommendationSurface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.5.dp, SereneRecommendationBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 18.dp),
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
                                    text = recommendationTitle,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 21.sp,
                                        lineHeight = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkText
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = recommendationSubtitle,
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
            }

            // Sección inferior limpia: Pausar/Continuar lectura + Repetir y botón "Hablar"
            Column(
                modifier = Modifier.fillMaxWidth(),
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
    }
}

@Preview(name = "Recommendation Screen", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RecommendationScreenPreview() {
    ProjectS3Theme {
        RecommendationScreen(
            speechText = "Acompañamiento cálido en todo momento",
            isPaused = false,
            onTogglePause = {},
            onSpeakClick = {}
        )
    }
}
