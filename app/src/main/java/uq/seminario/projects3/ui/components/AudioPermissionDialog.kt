package uq.seminario.projects3.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uq.seminario.projects3.ui.theme.ActionDarkBlue
import uq.seminario.projects3.ui.theme.CardSurface
import uq.seminario.projects3.ui.theme.DarkText
import uq.seminario.projects3.ui.theme.MutedDarkText
import uq.seminario.projects3.ui.theme.PrimaryDarkGreen

/**
 * Diálogo accesible para solicitar el permiso de micrófono.
 * Explica con lenguaje claro y tipografía grande la razón del permiso al adulto mayor.
 */
@Composable
fun AudioPermissionDialog(
    onGrantPermissionClick: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.fillMaxWidth(0.95f),
        shape = RoundedCornerShape(24.dp),
        containerColor = CardSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = PrimaryDarkGreen,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Permiso de micrófono",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(
                    text = "Para que tu asistente pueda escucharte y responderte, necesitamos permiso para usar el micrófono de tu teléfono.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        color = DarkText
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Tu voz solo se utilizará para conversar en esta aplicación.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        color = MutedDarkText
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGrantPermissionClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Conceder permiso de micrófono"
                    },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryDarkGreen,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Permitir micrófono",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Cancelar solicitud de micrófono"
                    },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, ActionDarkBlue)
            ) {
                Text(
                    text = "Ahora no",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ActionDarkBlue
                    )
                )
            }
        }
    )
}
