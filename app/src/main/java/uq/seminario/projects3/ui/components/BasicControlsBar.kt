package uq.seminario.projects3.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uq.seminario.projects3.ui.theme.ActionDarkBlue
import uq.seminario.projects3.ui.theme.CardSurface
import uq.seminario.projects3.ui.theme.PrimaryDarkGreen
import uq.seminario.projects3.ui.theme.ProjectS3Theme

/**
 * Controles básicos de lectura inferiores simplificados para mantener la limpieza visual
 * de los mockups originales:
 * - "Pausar lectura" / "Continuar lectura"
 * - "Repetir"
 */
@Composable
fun BasicControlsBar(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val pauseLabel = if (isPaused) "Continuar lectura" else "Pausar lectura"
    val pauseIcon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause
    val pauseDesc = if (isPaused) {
        "Botón para continuar la reproducción de la lectura en voz alta"
    } else {
        "Botón para pausar la reproducción de la lectura en voz alta"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTogglePause()
            },
            modifier = Modifier
                .weight(1.35f)
                .heightIn(min = 60.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = pauseDesc
                },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = CardSurface,
                contentColor = ActionDarkBlue
            ),
            border = BorderStroke(2.2.dp, ActionDarkBlue),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = pauseIcon,
                contentDescription = null,
                tint = ActionDarkBlue,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = pauseLabel,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = ActionDarkBlue
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRepeat()
            },
            modifier = Modifier
                .weight(0.95f)
                .heightIn(min = 60.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Repetir la última respuesta desde el principio"
                },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = CardSurface,
                contentColor = PrimaryDarkGreen
            ),
            border = BorderStroke(2.2.dp, PrimaryDarkGreen),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Replay,
                contentDescription = null,
                tint = PrimaryDarkGreen,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Repetir",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDarkGreen
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3ED)
@Composable
private fun BasicControlsBarPreview() {
    ProjectS3Theme {
        BasicControlsBar(
            isPaused = false,
            onTogglePause = {},
            onRepeat = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
