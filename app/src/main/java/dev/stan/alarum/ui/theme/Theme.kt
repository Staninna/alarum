package dev.stan.alarum.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val Amber = Color(0xFFFFB951)
private val DeepAmber = Color(0xFF7A5900)
private val Ember = Color(0xFFFF5449)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF422C00),
    primaryContainer = DeepAmber,
    onPrimaryContainer = Color(0xFFFFDEA8),
    error = Ember,
    background = Color(0xFF14120E),
    onBackground = Color(0xFFEAE1D9),
    surface = Color(0xFF14120E),
    surfaceContainer = Color(0xFF211E1A),
    surfaceContainerHigh = Color(0xFF2C2925),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5900),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDEA8),
    onPrimaryContainer = Color(0xFF261A00),
    error = Color(0xFFBA1A1A),
)

/**
 * Expressive shapes and a display face big enough to read from the pillow.
 * Dynamic colour when the platform offers it, an amber fallback when it does
 * not, because an alarm that greets you at 07:00 should not be blue.
 */
@Composable
fun AlarumTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamic: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp),
        ),
        typography = Typography().let { t ->
            t.copy(
                displayLarge = t.displayLarge.copy(
                    fontSize = 76.sp,
                    lineHeight = 80.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-2).sp,
                ),
                headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        },
        content = content,
    )
}
