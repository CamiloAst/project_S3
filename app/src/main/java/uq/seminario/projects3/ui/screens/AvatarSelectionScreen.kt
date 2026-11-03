package uq.seminario.projects3.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uq.seminario.projects3.avatar.AssistantAvatar
import uq.seminario.projects3.ui.components.AppNavigationHeader
import uq.seminario.projects3.ui.theme.BeigeBackground
import uq.seminario.projects3.ui.theme.BorderStrokeColor
import uq.seminario.projects3.ui.theme.CardSurface
import uq.seminario.projects3.ui.theme.DarkText
import uq.seminario.projects3.ui.theme.DecisionPastelGreen
import uq.seminario.projects3.ui.theme.MutedDarkText
import uq.seminario.projects3.ui.theme.PrimaryDarkGreen
import uq.seminario.projects3.ui.theme.ProjectS3Theme

/**
 * Pantalla de elección del acompañante (`UiState.ChoosingAvatar`)
 *
 * El acompañante elegido es quien conversa con el usuario y aparece en todas las pantallas.
 * - Primera vez: no hay "Atrás"; el usuario debe elegir para empezar.
 * - Desde "Cambiar acompañante": "← Atrás" (o el botón físico) vuelve sin cambiar nada.
 * - Dos tarjetas grandes con imagen y nombre; tocar una la marca (borde, fondo y ✓) y
 *   "Empezar a conversar" confirma, para que un toque accidental no cambie al acompañante.
 */
@Composable
fun AvatarSelectionScreen(
    currentAvatar: AssistantAvatar?,
    onAvatarChosen: (AssistantAvatar) -> Unit,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var selectedAvatar by rememberSaveable { mutableStateOf(currentAvatar) }

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
            // Sección superior: "Atrás" solo al cambiar de acompañante + título
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (onBackClick != null) {
                    AppNavigationHeader(
                        isIdle = false,
                        showBackButton = true,
                        showHomeButton = false,
                        onBackClick = onBackClick
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text(
                    text = "¿Con quién quieres conversar?",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 30.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Elige a tu acompañante. Puedes cambiarlo cuando quieras.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        color = MutedDarkText
                    ),
                    textAlign = TextAlign.Center
                )
            }

            // Centro: las dos opciones, una junto a la otra
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AssistantAvatar.entries.forEach { avatar ->
                        AvatarOptionCard(
                            avatar = avatar,
                            isSelected = avatar == selectedAvatar,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedAvatar = avatar
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Inferior: confirmación de la elección y botón para empezar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = selectedAvatar?.let { "Elegiste a ${it.title.lowercase()}." }
                        ?: "Toca la imagen que prefieras.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 21.sp,
                        lineHeight = 30.sp,
                        color = DarkText
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        // TalkBack anuncia la elección al cambiar
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )

                Button(
                    onClick = { selectedAvatar?.let(onAvatarChosen) },
                    enabled = selectedAvatar != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryDarkGreen,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "Empezar a conversar",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarOptionCard(
    avatar: AssistantAvatar,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick
            )
            // TalkBack lee la descripción de la imagen y después el nombre (texto de la tarjeta)
            .semantics { contentDescription = avatar.imageDescription },
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) DecisionPastelGreen else CardSurface,
        border = if (isSelected) {
            BorderStroke(4.dp, PrimaryDarkGreen)
        } else {
            BorderStroke(2.dp, BorderStrokeColor)
        },
        shadowElevation = if (isSelected) 6.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Image(
                    painter = painterResource(avatar.imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PrimaryDarkGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = avatar.label,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(name = "Elección de acompañante - Primera vez", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AvatarSelectionFirstTimePreview() {
    ProjectS3Theme {
        AvatarSelectionScreen(
            currentAvatar = null,
            onAvatarChosen = {}
        )
    }
}

@Preview(name = "Elección de acompañante - Cambiar", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AvatarSelectionChangePreview() {
    ProjectS3Theme {
        AvatarSelectionScreen(
            currentAvatar = AssistantAvatar.FEMALE,
            onAvatarChosen = {},
            onBackClick = {}
        )
    }
}
