package uq.seminario.projects3.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import uq.seminario.projects3.ui.theme.ActionDarkBlue
import uq.seminario.projects3.ui.theme.BeigeBackground
import uq.seminario.projects3.ui.theme.DarkText
import uq.seminario.projects3.ui.theme.DecisionPastelGreen
import uq.seminario.projects3.ui.theme.DecisionPastelGreenBorder
import uq.seminario.projects3.ui.theme.ProjectS3Theme

/**
 * Pantalla 3: Pantalla de Decisión Multimodal (DecisionScreen)
 *
 * Aplica los principios de accesibilidad gerontológica:
 * - Pregunta grande, directa y visible (>=28sp).
 * - Dos botones rectangulares masivos (mínimo 64dp, configurados con 80dp para albergar título + subtítulo).
 * - Alto contraste:
 *     * "Sí": Fondo azul oscuro (#1B3B5C), texto blanco.
 *     * "No": Fondo verde pastel (#D5E7DA), texto oscuro (#1A1A1A).
 * - Principio de Equivalencia: Botón footer "Responder hablando" para usuarios que prefieren
 *   interacción por voz sobre toque de pantalla.
 */
@Composable
fun DecisionScreen(
    question: String = "¿Quieres continuar con la conversación?",
    yesText: String = "Sí",
    yesSubtitle: String = "Seguir hablando con tu asistente",
    noText: String = "No",
    noSubtitle: String = "Terminar la conversación por ahora",
    onYesClick: () -> Unit,
    onNoClick: () -> Unit,
    onVoiceResponseClick: () -> Unit,
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
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Sección Superior: Título con pregunta grande y directa
            DecisionTitleSection(question = question)

            // Sección Central: Dos botones rectangulares masivos apilados verticalmente
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Botón masivo "Sí" (Azul oscuro, texto blanco)
                    MassiveDecisionButton(
                        title = yesText,
                        subtitle = yesSubtitle,
                        icon = Icons.Default.Check,
                        containerColor = ActionDarkBlue,
                        contentColor = Color.White,
                        subtitleColor = Color.White.copy(alpha = 0.9f),
                        borderColor = null,
                        accessibilityDescription = "Opción sí: $yesSubtitle",
                        onClick = onYesClick
                    )

                    // Botón masivo "No" (Verde pastel, texto oscuro)
                    MassiveDecisionButton(
                        title = noText,
                        subtitle = noSubtitle,
                        icon = Icons.Default.Close,
                        containerColor = DecisionPastelGreen,
                        contentColor = DarkText,
                        subtitleColor = DarkText.copy(alpha = 0.85f),
                        borderColor = DecisionPastelGreenBorder,
                        accessibilityDescription = "Opción no: $noSubtitle",
                        onClick = onNoClick
                    )
                }
            }

            // Sección Inferior (Footer): Principio de Equivalencia Multimodal
            EquivalenceFooterSection(onClick = onVoiceResponseClick)
        }
    }
}

@Composable
private fun DecisionTitleSection(
    question: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 29.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MassiveDecisionButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    subtitleColor: Color,
    borderColor: Color?,
    accessibilityDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .semantics {
                role = Role.Button
                contentDescription = accessibilityDescription
            },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = borderColor?.let { BorderStroke(2.dp, it) },
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = subtitleColor
                    )
                )
            }
        }
    }
}

@Composable
private fun EquivalenceFooterSection(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Responder usando tu voz en lugar de tocar la pantalla"
            },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, ActionDarkBlue)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = ActionDarkBlue,
                modifier = Modifier.size(30.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Responder hablando",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = ActionDarkBlue
                )
            )
        }
    }
}

@Preview(name = "Decision Screen", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DecisionScreenPreview() {
    ProjectS3Theme {
        DecisionScreen(
            question = "¿Quieres continuar con la conversación?",
            yesText = "Sí",
            yesSubtitle = "Seguir hablando con tu asistente",
            noText = "No",
            noSubtitle = "Terminar la conversación por ahora",
            onYesClick = {},
            onNoClick = {},
            onVoiceResponseClick = {}
        )
    }
}
