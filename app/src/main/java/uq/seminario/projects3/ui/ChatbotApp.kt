package uq.seminario.projects3.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uq.seminario.projects3.ui.components.AudioPermissionDialog
import uq.seminario.projects3.ui.screens.BotResponseScreen
import uq.seminario.projects3.ui.screens.DecisionScreen
import uq.seminario.projects3.ui.screens.RepairScreen
import uq.seminario.projects3.ui.screens.VoiceInputScreen
import uq.seminario.projects3.ui.theme.ActionDarkBlue
import uq.seminario.projects3.ui.theme.BeigeBackground
import uq.seminario.projects3.ui.theme.DarkText
import uq.seminario.projects3.ui.theme.ListeningTerracotta
import uq.seminario.projects3.ui.theme.PrimaryDarkGreen
import uq.seminario.projects3.viewmodel.ChatbotViewModel
import uq.seminario.projects3.viewmodel.UiState

/**
 * Contenedor orquestador del Chatbot.
 * Conecta el StateHolder (ChatbotViewModel) con las vistas mediante StateFlow.
 * Gestiona de forma moderna los permisos de audio en tiempo de ejecución con rememberLauncherForActivityResult.
 */
@Composable
fun ChatbotApp(
    viewModel: ChatbotViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showPermissionDialog by viewModel.showPermissionDialog.collectAsStateWithLifecycle()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Launcher nativo moderno de Compose para solicitar permisos
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.dismissPermissionDialog()
        }
    }

    // Diálogo accesible de solicitud de permiso
    if (showPermissionDialog) {
        AudioPermissionDialog(
            onGrantPermissionClick = {
                viewModel.dismissPermissionDialog()
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismissRequest = {
                viewModel.dismissPermissionDialog()
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BeigeBackground,
        bottomBar = {
            // Barra auxiliar para demostración y evaluación rápida de todos los estados
            DemoStateSwitcherBar(
                currentState = uiState,
                onSelectIdle = { viewModel.resetToIdle() },
                onSelectListening = {
                    viewModel.onMicClicked(
                        hasPermission = hasAudioPermission,
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                },
                onSelectSpeaking = { viewModel.stopListeningAndSpeak() },
                onSelectDecision = { viewModel.askDecision() },
                onSelectRepair = { viewModel.showRepairState() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 400)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 400))
                },
                label = "screen_fade_transition"
            ) { state ->
                when (state) {
                    is UiState.Idle -> {
                        VoiceInputScreen(
                            isListening = false,
                            onMicClick = {
                                viewModel.onMicClicked(
                                    hasPermission = hasAudioPermission,
                                    onRequestPermission = {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                )
                            }
                        )
                    }

                    is UiState.Listening -> {
                        VoiceInputScreen(
                            isListening = true,
                            onMicClick = {
                                viewModel.onMicClicked(
                                    hasPermission = hasAudioPermission,
                                    onRequestPermission = {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                )
                            }
                        )
                    }

                    is UiState.Speaking -> {
                        BotResponseScreen(
                            speechText = state.text,
                            isPaused = state.isPaused,
                            onTogglePause = { viewModel.toggleSpeakingPause() }
                        )
                    }

                    is UiState.AwaitingDecision -> {
                        DecisionScreen(
                            question = state.question,
                            yesText = state.confirmOption.title,
                            yesSubtitle = state.confirmOption.subtitle,
                            noText = state.dismissOption.title,
                            noSubtitle = state.dismissOption.subtitle,
                            onYesClick = { viewModel.onDecisionMade(true) },
                            onNoClick = { viewModel.onDecisionMade(false) },
                            onVoiceResponseClick = {
                                viewModel.onMicClicked(
                                    hasPermission = hasAudioPermission,
                                    onRequestPermission = {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                )
                            }
                        )
                    }

                    is UiState.Repair -> {
                        RepairScreen(
                            message = state.message,
                            onRetryClick = {
                                viewModel.onMicClicked(
                                    hasPermission = hasAudioPermission,
                                    onRequestPermission = {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                )
                            },
                            onBackToHomeClick = {
                                viewModel.resetToIdle()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Selector accesible para alternar o evaluar rápidamente cada estado solicitado en la rúbrica.
 */
@Composable
private fun DemoStateSwitcherBar(
    currentState: UiState,
    onSelectIdle: () -> Unit,
    onSelectListening: () -> Unit,
    onSelectSpeaking: () -> Unit,
    onSelectDecision: () -> Unit,
    onSelectRepair: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFEBE6DC),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Modo de prueba (Estados del Chatbot):",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText
                ),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = currentState is UiState.Idle,
                    onClick = onSelectIdle,
                    label = { Text("1. Reposo", fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryDarkGreen,
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = currentState is UiState.Listening,
                    onClick = onSelectListening,
                    label = { Text("2. Escuchando", fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ListeningTerracotta,
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = currentState is UiState.Speaking,
                    onClick = onSelectSpeaking,
                    label = { Text("3. Bot Habla", fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ActionDarkBlue,
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = currentState is UiState.AwaitingDecision,
                    onClick = onSelectDecision,
                    label = { Text("4. Decisión", fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ActionDarkBlue,
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = currentState is UiState.Repair,
                    onClick = onSelectRepair,
                    label = { Text("5. Lupa (Reparación)", fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ListeningTerracotta,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}
