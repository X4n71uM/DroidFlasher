package net.xantium.droidflasher.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val WorkshopDarkColorScheme = darkColorScheme(
    primary = CyanTelemetry,
    onPrimary = Color(0xFF002026),
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanTelemetry,
    secondary = AmberCaution,
    onSecondary = Color(0xFF261900),
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberCaution,
    tertiary = SignalGreen,
    onTertiary = Color(0xFF002411),
    tertiaryContainer = SignalGreenContainer,
    onTertiaryContainer = SignalGreen,
    error = HazardCrimson,
    onError = Color.White,
    errorContainer = HazardCrimsonContainer,
    onErrorContainer = Color(0xFFFFB3BC),
    background = BenchCarbonBg,
    onBackground = TextPrimary,
    surface = BenchSurface,
    onSurface = TextPrimary,
    surfaceVariant = BenchSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BenchCardBorder,
    outlineVariant = Color(0xFF1E293B)
)

@Composable
fun DroidFlasherTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = WorkshopDarkColorScheme,
        typography = Typography,
        content = content
    )
}
