package id.azkura.auth.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Azkura Auth always uses dark theme — matching the extension's design.
 */
private val AzkuraDarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = TextOnAccent,
    primaryContainer = AccentDim,
    onPrimaryContainer = Accent,
    secondary = TextSecondary,
    onSecondary = TextPrimary,
    secondaryContainer = BgElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = TotpWarning,
    onTertiary = TextOnAccent,
    background = BgBase,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    surfaceVariant = BgElevated,
    onSurfaceVariant = TextSecondary,
    error = Error,
    onError = TextPrimary,
    errorContainer = Color(0x33FF3B3B),
    onErrorContainer = Error,
    outline = BorderMedium,
    outlineVariant = BorderSubtle,
    inverseSurface = TextPrimary,
    inverseOnSurface = BgBase,
    inversePrimary = Color(0xFF006874),
    surfaceTint = Accent,
    scrim = Color(0xB3000000),
)

@Composable
fun AzkuraAuthTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = BgBase.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = BgBase.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = AzkuraDarkColorScheme,
        typography = AzkuraTypography,
        content = content,
    )
}
