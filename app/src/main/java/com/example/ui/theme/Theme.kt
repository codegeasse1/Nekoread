package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SleekVioletPrimary,
    onPrimary = Color.White,
    primaryContainer = SleekVioletContainer,
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = SleekCyanAccent,
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
    outlineVariant = DarkSurfaceHighlight
)

private val LightColorScheme = lightColorScheme(
    primary = SleekVioletPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE6FF),
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

@Composable
fun NekoReadTheme(
    darkTheme: Boolean = true, // Default to sleek dark theme like Mihon
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
