package cat.rumb.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandGreen = Color(0xFF0B6E4F)
private val BrandGreenDark = Color(0xFF08543C)
private val Accent = Color(0xFFFFD166)

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    secondary = BrandGreenDark,
    tertiary = Accent,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5FD0A6),
    secondary = Color(0xFF3FB88C),
    tertiary = Accent,
)

@Composable
fun RumbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
