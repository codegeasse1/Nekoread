package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.example.ui.components.LocalAppAccent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private fun darkScheme(accent: AppAccent) = darkColorScheme(
    primary = accent.primary,
    onPrimary = Color.White,
    primaryContainer = accent.container,
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = accent.gradientEnd,
    onSecondary = Color.Black,
    tertiary = SleekGoldBadge,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkBorder,
    outlineVariant = DarkSurfaceHighlight,
    error = Color(0xFFFF6B6B),
    surfaceTint = Color.Transparent,
    scrim = GlassScrim
)

private fun lightScheme(accent: AppAccent) = lightColorScheme(
    primary = SleekVioletPrimary,
    onPrimary = Color.White,
    primaryContainer = accent.primary.copy(alpha = 0.18f),
    onPrimaryContainer = Color(0xFF260067),
    secondary = Color(0xFF00B8D4),
    onSecondary = Color.White,
    tertiary = SleekGoldBadge,
    onTertiary = Color.Black,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightBorder,
    outlineVariant = Color(0xFFCBD5E1)
)

// Tadami-style generous rounding applied to every button/box in the app (Cards, Buttons,
// dialogs, chips...) so nothing looks sharp-cornered.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NekoReadTheme(
    darkTheme: Boolean = true, // Default to sleek dark theme like Mihon
    dynamicColor: Boolean = false,
    accent: AppAccent = AppAccent.VIOLET,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkScheme(accent)
        else -> lightScheme(accent)
    }

    CompositionLocalProvider(LocalAppAccent provides accent) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
    }
}
