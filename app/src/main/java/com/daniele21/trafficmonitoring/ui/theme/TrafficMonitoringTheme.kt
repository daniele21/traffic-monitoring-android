package com.daniele21.trafficmonitoring.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

// Traffic Monitoring brand palette, normalized from the approved shield brand kit.
val Midnight = Color(0xFF020D2C)
val DeepNavy = Color(0xFF0E2345)
val RoyalBlue = Color(0xFF002996)
val NetworkBlue = Color(0xFF207CCE)
val SignalCyan = Color(0xFF0DC1F9)
val ProductSurface = Color(0xFFF2F7FD)
val DarkUi = Color(0xFF10141A)
val Healthy = Color(0xFF22C55E)
val Warning = Color(0xFFF59E0B)
val Critical = Color(0xFFEF4444)

private val LightColors = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Midnight,
    secondary = NetworkBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEEFF),
    onSecondaryContainer = DeepNavy,
    tertiary = SignalCyan,
    onTertiary = Midnight,
    background = Color.White,
    onBackground = Midnight,
    surface = ProductSurface,
    onSurface = Midnight,
    surfaceVariant = Color(0xFFE4EDF8),
    onSurfaceVariant = Color(0xFF52627A),
    outline = Color(0xFF9AACC4),
    error = Critical
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CB1FF),
    onPrimary = Midnight,
    primaryContainer = RoyalBlue,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF78B9F3),
    onSecondary = Midnight,
    secondaryContainer = DeepNavy,
    onSecondaryContainer = Color.White,
    tertiary = SignalCyan,
    onTertiary = Midnight,
    background = DarkUi,
    onBackground = Color(0xFFF3F7FF),
    surface = Color(0xFF161D27),
    onSurface = Color(0xFFF3F7FF),
    surfaceVariant = DeepNavy,
    onSurfaceVariant = Color(0xFFB9C7DC),
    outline = Color(0xFF657895),
    error = Color(0xFFFF8A80)
)

@Composable
fun TrafficMonitoringTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (darkTheme) 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
