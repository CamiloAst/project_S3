package uq.seminario.projects3.ui.screens

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import uq.seminario.projects3.ui.components.AvatarCornerBadge
import uq.seminario.projects3.ui.components.BasicControlsBar
import uq.seminario.projects3.ui.theme.BeigeBackground
import uq.seminario.projects3.ui.theme.CardSurface
import uq.seminario.projects3.ui.theme.DarkText
import uq.seminario.projects3.ui.theme.ListeningTerracotta
import uq.seminario.projects3.ui.theme.MutedDarkText
import uq.seminario.projects3.ui.theme.PrimaryDarkGreen
import uq.seminario.projects3.ui.theme.ProjectS3Theme

/**
 * Pantalla de Reparación Silenciosa ("Pantalla de la Lupa")
 *
 * Se muestra cuando el STT no detecta voz clara o hay ruido de fondo.
 * - Barra superior `AppNavigationHeader` con "← Atrás" e "🏠 Inicio".
 * - Centro con el avatar del acompañante (con distintivo de lupa) y mensaje claro (>=28sp).
 * - Footer limpio con controles de lectura ("Pausar lectura / Continuar lectura" y "Repetir") + "Volver a hablar".
 */
@Composable
fun RepairScreen(
    message: String = "No he entendido tu pregunta. ¿Puedes repetirla?",
    onRetryClick: () -> Unit,
    onBackToHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: AssistantAvatar = AssistantAvatar.DEFAULT,
    isPaused: Boolean = false,
    onBackClick: () -> Unit = onBackToHomeClick,
    onTogglePauseClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    hint: String = "Puedes hablar más despacio o acercarte un poco más al teléfono."
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
                    onHomeClick = onBackToHomeClick
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Estamos para escucharte",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MutedDarkText
                    ),
                    textAlign = TextAlign.Center
                )
            }

            // Centro: Icono de lupa prominente + Mensaje comprensivo
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
                    // El acompañante pide repetir; la lupa recuerda que no entendió bien
                    AssistantAvatarBadge(
                        avatar = avatar,
                        avatarSize = 112.dp,
                        isSpeaking = !isPaused,
                        contentDescription = "${avatar.title} no te entendió bien",
                        badge = {
                            AvatarCornerBadge(
                                icon = Icons.Default.Search,
                                iconTint = ListeningTerracotta,
                                backgroundColor = CardSurface
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = message,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 28.sp,
                            lineHeight = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = hint,
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

            // Footer limpio: Pausar/Continuar lectura + Repetir y botón masivo "Volver a hablar"
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BasicControlsBar(
                    isPaused = isPaused,
                    onTogglePause = onTogglePauseClick,
                    onRepeat = onRepeatClick
                )

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRetryClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Volver a hablar ahora"
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

@Preview(name = "Repair Screen", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RepairScreenPreview() {
    ProjectS3Theme {
        RepairScreen(
            onRetryClick = {},
            onBackToHomeClick = {}
        )
    }
}
