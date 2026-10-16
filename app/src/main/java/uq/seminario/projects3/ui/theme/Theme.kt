package uq.seminario.projects3.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GerontologicalColorScheme = lightColorScheme(
    primary = PrimaryDarkGreen,
    onPrimary = Color.White,
    secondary = ActionDarkBlue,
    onSecondary = Color.White,
    tertiary = ListeningTerracotta,
    onTertiary = Color.White,
    background = BeigeBackground,
    onBackground = DarkText,
    surface = CardSurface,
    onSurface = DarkText,
    outline = BorderStrokeColor,
    surfaceVariant = BeigeBackground,
    onSurfaceVariant = MutedDarkText
)

@Composable
fun ProjectS3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = GerontologicalColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}