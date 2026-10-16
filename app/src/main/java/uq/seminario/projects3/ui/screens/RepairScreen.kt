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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.clip
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
import uq.seminario.projects3.ui.theme.CardSurface
import uq.seminario.projects3.ui.theme.DarkText
import uq.seminario.projects3.ui.theme.ListeningTerracotta
import uq.seminario.projects3.ui.theme.MutedDarkText
import uq.seminario.projects3.ui.theme.PrimaryDarkGreen
import uq.seminario.projects3.ui.theme.ProjectS3Theme

/**
 * Pantalla de Reparación Silenciosa ("Pantalla de la Lupa")
 *
 * Se muestra cuando el STT no detecta voz clara o hay ruido de fondo,
 * evitando cuadros de diálogo genéricos o frustrantes del sistema operativo.
 *
 * Características de accesibilidad:
 * - Iconografía grande y amigable (lupa en contenedor cálido).
 * - Mensaje comprensivo y claro (>=28sp): "No he entendido tu pregunta. ¿Puedes repetirla?".
 * - Botón de acción masivo (>=64dp) para reintentar la interacción por voz.
 */
@Composable
fun RepairScreen(
    message: String = "No he entendido tu pregunta. ¿Puedes repetirla?",
    onRetryClick: () -> Unit,
    onBackToHomeClick: () -> Unit,
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
            // Header sutil
            Text(
                text = "Estamos para escucharte",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MutedDarkText
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )

            // Centro: Icono de lupa prominente + Mensaje comprensivo
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
                    // Contenedor circular con icono de Lupa (búsqueda/comprensión)
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(CardSurface)
                            .semantics {
                                contentDescription = "Icono de búsqueda y escucha"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = ListeningTerracotta,
                            modifier = Modifier.size(62.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Mensaje principal grande y claro (>=28sp)
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

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Puedes hablar más despacio o acercarte un poco más al teléfono.",
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

            // Footer: Botón masivo para volver a hablar (>=64dp) + opción de volver
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onRetryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
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

                OutlinedButton(
                    onClick = onBackToHomeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Regresar a la pantalla de inicio"
                        },
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(2.dp, ActionDarkBlue)
                ) {
                    Text(
                        text = "Ir al inicio",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = ActionDarkBlue
                        )
                    )
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
