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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
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
import uq.seminario.projects3.ui.components.AvatarCornerBadge
import uq.seminario.projects3.ui.components.BasicControlsBar
import uq.seminario.projects3.ui.components.ContextImageCard
import uq.seminario.projects3.ui.theme.BeigeBackground
import uq.seminario.projects3.ui.theme.CardSurface
import uq.seminario.projects3.ui.theme.DarkText
import uq.seminario.projects3.ui.theme.EmergencyBorder
import uq.seminario.projects3.ui.theme.EmergencyRed
import uq.seminario.projects3.ui.theme.EmergencySurface
import uq.seminario.projects3.ui.theme.MutedDarkText
import uq.seminario.projects3.ui.theme.PrimaryDarkGreen
import uq.seminario.projects3.ui.theme.ProjectS3Theme

/**
 * Pantalla de Alerta de Posible Emergencia (`suggested_action = "emergency_alert"`).
 *
 * Redundancia (CARE): el mismo mensaje se lee en voz alta, se muestra en pantalla y se ilustra
 * con una imagen fija (`ContextImage.AYUDA_CERCANA`: una persona ayuda a otra), útil también
 * para quien lee con dificultad. Ninguna otra respuesta puede mostrar esa imagen.
 * Asignación (CARE): la alerta se presenta de forma visual destacada, con botones grandes.
 *
 * Ambiente de pruebas: esta pantalla NO llama ni avisa a cuidadores ni a servicios de
 * emergencia; solo pide al usuario que busque ayuda de una persona cercana.
 */
@Composable
fun EmergencyScreen(
    screenText: String,
    onSpeakClick: () -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: AssistantAvatar = AssistantAvatar.DEFAULT,
    isPaused: Boolean = false,
    onBackClick: () -> Unit = onHomeClick,
    onTogglePause: () -> Unit = {},
    onRepeatClick: () -> Unit = {}
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
            AppNavigationHeader(
                isIdle = false,
                showBackButton = true,
                showHomeButton = true,
                onBackClick = onBackClick,
                onHomeClick = onHomeClick
            )

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
                    // El acompañante da el aviso: borde, ondas y distintivo de alerta en rojo
                    AssistantAvatarBadge(
                        avatar = avatar,
                        avatarSize = 112.dp,
                        isSpeaking = !isPaused,
                        mood = AvatarExpression.EMPATHETIC,
                        waveColor = EmergencyRed,
                        borderColor = EmergencyRed,
                        contentDescription = "Alerta. ${avatar.title} te pide buscar ayuda",
                        badge = {
                            AvatarCornerBadge(
                                icon = Icons.Default.Warning,
                                iconTint = EmergencyRed,
                                backgroundColor = EmergencySurface,
                                size = 48.dp
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Posible emergencia",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EmergencyRed
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = screenText,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 30.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Assertive }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ContextImageCard(
                        image = ContextImage.AYUDA_CERCANA,
                        imageHeight = 120.dp,
                        containerColor = CardSurface,
                        borderColor = EmergencyBorder
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Busca ahora a un familiar, vecino o a tu cuidador. No hagas esfuerzos mientras llega la ayuda.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 20.sp,
                            lineHeight = 28.sp,
                            color = MutedDarkText
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.92f)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Aviso explícito del ambiente de pruebas: la app no contacta a nadie
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, EmergencyBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Modo de prueba: esta aplicación no llama ni avisa a nadie automáticamente.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                color = MutedDarkText
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }

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

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSpeakClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Volver a hablar con el asistente"
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryDarkGreen,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Volver a hablar",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 22.sp,
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

@Preview(name = "Emergency Screen", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun EmergencyScreenPreview() {
    ProjectS3Theme {
        EmergencyScreen(
            screenText = "Pide ayuda a alguien cercano",
            onSpeakClick = {},
            onHomeClick = {}
        )
    }
}
