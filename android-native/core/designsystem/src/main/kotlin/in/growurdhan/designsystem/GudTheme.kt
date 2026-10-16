package `in`.growurdhan.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import `in`.growurdhan.model.BrandSourceTokens
import `in`.growurdhan.model.SemanticTokens

private fun color(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

private val LightScheme = lightColorScheme(
    primary = color(SemanticTokens.LIGHT_PRIMARY_CONTAINER),
    onPrimary = color(SemanticTokens.LIGHT_ON_PRIMARY),
    background = color(BrandSourceTokens.LIGHT_BACKGROUND),
    onBackground = color(BrandSourceTokens.LIGHT_TEXT),
    surface = color(BrandSourceTokens.LIGHT_SURFACE),
    onSurface = color(BrandSourceTokens.LIGHT_TEXT),
    surfaceVariant = color(BrandSourceTokens.LIGHT_CHROME),
    onSurfaceVariant = color(BrandSourceTokens.LIGHT_MUTED),
    outline = color(BrandSourceTokens.LIGHT_BORDER),
    secondary = color(BrandSourceTokens.LIGHT_TEAL),
    onSecondary = color(BrandSourceTokens.LIGHT_SURFACE),
)

private val DarkScheme = darkColorScheme(
    primary = color(SemanticTokens.DARK_PRIMARY),
    onPrimary = color(SemanticTokens.DARK_ON_PRIMARY),
    background = color(BrandSourceTokens.DARK_BACKGROUND),
    onBackground = color(BrandSourceTokens.DARK_TEXT),
    surface = color(BrandSourceTokens.DARK_CARD),
    onSurface = color(BrandSourceTokens.DARK_TEXT),
    surfaceVariant = color(BrandSourceTokens.DARK_CHROME),
    onSurfaceVariant = color(BrandSourceTokens.DARK_MUTED),
    outline = color(BrandSourceTokens.DARK_BORDER),
    secondary = color(BrandSourceTokens.MARK_TEAL),
    onSecondary = color(BrandSourceTokens.MARK_NAVY),
)

@Composable
fun GudTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
