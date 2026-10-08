package `in`.growurdhan.app.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import `in`.growurdhan.core.model.AppearanceSettings
import `in`.growurdhan.core.model.ThemeMode

private val Dark = darkColorScheme(
    primary = Color(0xFF94E9B7),
    onPrimary = Color(0xFF053823),
    primaryContainer = Color(0xFF18573C),
    onPrimaryContainer = Color(0xFFB9F5D1),
    background = Color(0xFF101813),
    onBackground = Color(0xFFEDF5EF),
    surface = Color(0xFF141D17),
    onSurface = Color(0xFFE5EEE7),
    surfaceVariant = Color(0xFF2D3E33),
    onSurfaceVariant = Color(0xFFCDD9D0),
)

private val Light = lightColorScheme(
    primary = Color(0xFF176A4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F5D1),
    onPrimaryContainer = Color(0xFF06432C),
    background = Color(0xFFF8FBF8),
    onBackground = Color(0xFF15261B),
    surface = Color.White,
    onSurface = Color(0xFF15261B),
    surfaceVariant = Color(0xFFE1EEE6),
    onSurfaceVariant = Color(0xFF3B5343),
)

@Composable
fun GrowUrDhanTheme(
    preferences: AppearanceSettings,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (preferences.theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (preferences.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) Dark else Light

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        content = content,
    )
}
