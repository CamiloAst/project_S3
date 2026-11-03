package uq.seminario.projects3.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uq.seminario.projects3.ui.components.AudioPermissionDialog
import uq.seminario.projects3.ui.screens.BotResponseScreen
import uq.seminario.projects3.ui.screens.DecisionScreen
import uq.seminario.projects3.ui.screens.EmergencyScreen
import uq.seminario.projects3.ui.screens.RecommendationScreen
import uq.seminario.projects3.ui.screens.RepairScreen
import uq.seminario.projects3.ui.screens.ThinkingScreen
import uq.seminario.projects3.ui.screens.VoiceInputScreen
import uq.seminario.projects3.ui.theme.BeigeBackground
import uq.seminario.projects3.ui.theme.SereneWarmBackground
import uq.seminario.projects3.viewmodel.ChatbotViewModel
import uq.seminario.projects3.viewmodel.UiState

private fun isAffectiveEmotion(emotion: String): Boolean {
    val norm = emotion.uppercase()
    return norm.contains("TRISTEZA") ||
            norm.contains("SOLEDAD") ||
            norm.contains("ANSIEDAD") ||
            norm.contains("PREOCUPACION")
}

/**
 * Contenedor orquestador del Chatbot (Modelo CARE: Complementariedad, Asignación,
 * Redundancia y Equivalencia entre voz, pantalla, botones y vibración).
 * Conecta el StateHolder (ChatbotViewModel) con las vistas mediante StateFlow,
 * gestiona el botón físico/sistema "Atrás" (`BackHandler`) y conecta los controles
 * de navegación (`navigateBack`, `navigateHome`, `Salir`).
 */
@Composable
fun ChatbotApp(
    viewModel: ChatbotViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val showPermissionDialog by viewModel.showPermissionDialog.collectAsStateWithLifecycle()
    // Se pasa como State (sin delegar) para que el volumen, que cambia muchas veces por
    // segundo, solo recomponga el botón del micrófono y no toda la app
    val voiceLevel = viewModel.voiceLevel.collectAsStateWithLifecycle()

    // Soporte del botón físico/sistema "Atrás" de Android en todas las pantallas excepto Idle
    BackHandler(enabled = uiState != UiState.Idle) {
        viewModel.navigateBack()
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // true cuando Android ya no muestra su diálogo ("No volver a preguntar" o dos rechazos):
    // la única vía para conceder el micrófono es la pantalla de Ajustes de la app.
    // Se exigen dos rechazos porque cerrar el primer diálogo sin elegir también devuelve
    // shouldShowRequestPermissionRationale=false. rememberSaveable sobrevive a la rotación.
    var micPermissionDenials by rememberSaveable { mutableIntStateOf(0) }
    var isMicPermissionBlocked by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            micPermissionDenials = 0
            isMicPermissionBlocked = false
            viewModel.onPermissionGranted()
        } else {
            micPermissionDenials++
            val activity = context as? Activity
            isMicPermissionBlocked = activity != null && micPermissionDenials >= 2 &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            viewModel.dismissPermissionDialog()
        }
    }

    // Al salir de la app (ON_STOP) se pausa la lectura y se cancela el micrófono; al volver
    // (ON_START) se relee el permiso por si el usuario lo concedió desde Ajustes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // Una rotación también emite ON_STOP: solo se pausa si la app deja de verse
                Lifecycle.Event.ON_STOP -> if ((context as? Activity)?.isChangingConfigurations != true) {
                    viewModel.onAppBackgrounded()
                }
                Lifecycle.Event.ON_START -> {
                    hasAudioPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasAudioPermission) {
                        micPermissionDenials = 0
                        isMicPermissionBlocked = false
                    }
                    viewModel.onAppForegrounded()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showPermissionDialog) {
        AudioPermissionDialog(
            isPermanentlyDenied = isMicPermissionBlocked,
            onGrantPermissionClick = {
                viewModel.dismissPermissionDialog()
                if (isMicPermissionBlocked) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onDismissRequest = {
                viewModel.dismissPermissionDialog()
            }
        )
    }

    val currentEmotion = when (val state = uiState) {
        is UiState.Speaking -> state.userEmotion
        is UiState.AwaitingDecision -> state.userEmotion
        else -> "NEUTRA"
    }
    val isAffectiveSupport = isAffectiveEmotion(currentEmotion)

    val containerBackgroundColor by animateColorAsState(
        targetValue = if (isAffectiveSupport) SereneWarmBackground else BeigeBackground,
        animationSpec = tween(durationMillis = 600),
        label = "scaffold_bg_color"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = containerBackgroundColor
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
                            },
                            onBackClick = { viewModel.navigateBack() },
                            onHomeClick = { viewModel.navigateHome() },
                            onExitClick = { (context as? Activity)?.finish() }
                        )
                    }

                    is UiState.Listening -> {
                        VoiceInputScreen(
                            isListening = true,
                            voiceLevel = { voiceLevel.value },
                            onMicClick = {
                                viewModel.onMicClicked(
                                    hasPermission = hasAudioPermission,
                                    onRequestPermission = {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                )
                            },
                            onBackClick = { viewModel.navigateBack() },
                            onHomeClick = { viewModel.navigateHome() },
                            onExitClick = { (context as? Activity)?.finish() }
                        )
                    }

                    is UiState.Thinking -> {
                        ThinkingScreen(
                            onBackClick = { viewModel.navigateBack() },
                            onHomeClick = { viewModel.navigateHome() }
                        )
                    }

                    is UiState.Speaking -> {
                        if (isAffectiveEmotion(state.userEmotion)) {
                            RecommendationScreen(
                                speechText = state.screenText,
                                isPaused = state.isPaused,
                                onTogglePause = { viewModel.toggleSpeakingPause() },
                                onRepeatClick = { viewModel.repeatLastResponse() },
                                onBackClick = { viewModel.navigateBack() },
                                onHomeClick = { viewModel.navigateHome() },
                                onSpeakClick = {
                                    viewModel.onMicClicked(
                                        hasPermission = hasAudioPermission,
                                        onRequestPermission = {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    )
                                }
                            )
                        } else {
                            BotResponseScreen(
                                speechText = state.screenText,
                                isPaused = state.isPaused,
                                userEmotion = state.userEmotion,
                                onTogglePause = { viewModel.toggleSpeakingPause() },
                                onRepeatClick = { viewModel.repeatLastResponse() },
                                onBackClick = { viewModel.navigateBack() },
                                onHomeClick = { viewModel.navigateHome() },
                                onSpeakClick = {
                                    viewModel.onMicClicked(
                                        hasPermission = hasAudioPermission,
                                        onRequestPermission = {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    )
                                }
                            )
                        }
                    }

                    is UiState.AwaitingDecision -> {
                        DecisionScreen(
                            question = state.question,
                            yesText = state.confirmOption.title,
                            yesSubtitle = state.confirmOption.subtitle,
                            noText = state.dismissOption.title,
                            noSubtitle = state.dismissOption.subtitle,
                            userEmotion = state.userEmotion,
                            onYesClick = { viewModel.onDecisionMade(true) },
                            onNoClick = { viewModel.onDecisionMade(false) },
                            onVoiceResponseClick = {
                                viewModel.onMicClicked(
                                    hasPermission = hasAudioPermission,
                                    onRequestPermission = {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                )
                            },
                            onBackClick = { viewModel.navigateBack() },
                            onHomeClick = { viewModel.navigateHome() }
                        )
                    }

                    is UiState.Emergency -> {
                        EmergencyScreen(
                            screenText = state.screenText,
                            isPaused = isPaused,
                            onTogglePause = { viewModel.toggleSpeakingPause() },
                            onRepeatClick = { viewModel.repeatLastResponse() },
                            onBackClick = { viewModel.navigateBack() },
                            onHomeClick = { viewModel.navigateHome() },
                            onSpeakClick = {
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
                            hint = state.hint,
                            isPaused = isPaused,
                            onRetryClick = {
                                viewModel.onMicClicked(
                                    hasPermission = hasAudioPermission,
                                    onRequestPermission = {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                )
                            },
                            onBackClick = { viewModel.navigateBack() },
                            onBackToHomeClick = { viewModel.navigateHome() },
                            onTogglePauseClick = { viewModel.toggleSpeakingPause() },
                            onRepeatClick = { viewModel.repeatLastResponse() }
                        )
                    }
                }
            }
        }
    }
}
