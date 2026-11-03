package uq.seminario.projects3.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uq.seminario.projects3.ui.theme.BorderStrokeColor
import uq.seminario.projects3.ui.theme.CardSurface
import uq.seminario.projects3.ui.theme.ListeningTerracotta
import uq.seminario.projects3.ui.theme.MutedDarkText
import uq.seminario.projects3.ui.theme.PrimaryDarkGreen
import uq.seminario.projects3.ui.theme.ProjectS3Theme

/**
 * Barra Superior de Navegación Accesible (AppNavigationHeader).
 *
 * Cumple con los criterios de agencia y navegación estándar móvil para adultos mayores:
 * - Altura mínima de botones de 56dp y texto de 18sp con retroalimentación háptica suave.
 * - Botón "← Atrás" (Izquierda): Visible en todas las pantallas excepto en `Idle`. Ejecuta `navigateBack()`.
 * - Botón "🏠 Inicio" (Derecha): Visible en todas las pantallas secundarias (respuesta, decisión,
 *   recomendación y reparación/error). Ejecuta `navigateHome()`.
 * - Botón "Salir" (Derecha en pantalla `Idle`): Botón discreto que cierra la actividad de forma segura.
 */
@Composable
fun AppNavigationHeader(
    isIdle: Boolean,
    showBackButton: Boolean = !isIdle,
    showHomeButton: Boolean = !isIdle,
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onExitClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Lado izquierdo: Botón "← Atrás" (oculto en Idle)
        if (showBackButton) {
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBackClick()
                },
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Regresar a la pantalla anterior"
                    },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardSurface,
                    contentColor = PrimaryDarkGreen
                ),
                border = BorderStroke(2.dp, PrimaryDarkGreen),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = PrimaryDarkGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Atrás",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDarkGreen
                    )
                )
            }
        } else {
            // Espaciador para mantener el botón derecho alineado en la esquina superior derecha
            Box(modifier = Modifier.width(1.dp))
        }

        // Lado derecho: "Salir" en Idle, o "🏠 Inicio" en pantallas secundarias
        if (isIdle) {
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onExitClick()
                },
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Salir de la aplicación de forma segura"
                    },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardSurface.copy(alpha = 0.85f),
                    contentColor = MutedDarkText
                ),
                border = BorderStroke(1.5.dp, BorderStrokeColor),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = MutedDarkText,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Salir",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MutedDarkText
                    )
                )
            }
        } else if (showHomeButton) {
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHomeClick()
                },
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Ir a la pantalla de inicio"
                    },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardSurface,
                    contentColor = ListeningTerracotta
                ),
                border = BorderStroke(2.dp, ListeningTerracotta),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = ListeningTerracotta,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Inicio",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ListeningTerracotta
                    )
                )
            }
        }
    }
}

@Preview(name = "Header - Idle (Salir)", showBackground = true, backgroundColor = 0xFFF6F3ED)
@Composable
private fun AppNavigationHeaderIdlePreview() {
    ProjectS3Theme {
        AppNavigationHeader(
            isIdle = true,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "Header - Secondary (Atrás + Inicio)", showBackground = true, backgroundColor = 0xFFF6F3ED)
@Composable
private fun AppNavigationHeaderSecondaryPreview() {
    ProjectS3Theme {
        AppNavigationHeader(
            isIdle = false,
            showBackButton = true,
            showHomeButton = true,
            modifier = Modifier.padding(16.dp)
        )
    }
}
