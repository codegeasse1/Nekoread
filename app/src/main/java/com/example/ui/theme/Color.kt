package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Dark Theme Colors - Sleek Dark Aesthetic
val DarkBackground = Color(0xFF0D0F17)
val DarkSurface = Color(0xE6161924)
val DarkSurfaceVariant = Color(0xC4232A3C)
val DarkSurfaceHighlight = Color(0xFF2A3147)
val DarkBorder = Color(0xFF2E354F)

// Light Theme Colors - Sleek Clean Aesthetic
val LightBackground = Color(0xFFF7F9FF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEEF2FC)
val LightBorder = Color(0xFFE2E8F5)

// Sleek Color Tokens
val SleekVioletPrimary = Color(0xFF8B5CF6)
val SleekVioletContainer = Color(0x592B1C5C)
val SleekCyanAccent = Color(0xFF22D3EE)
val SleekCoralAccent = Color(0xFFFF4081)
val SleekGoldBadge = Color(0xFFFFB300)
val SleekGreenSuccess = Color(0xFF00E676)

// Compatibility Aliases
val NekoVioletPrimary = SleekVioletPrimary
val NekoVioletContainer = SleekVioletContainer
val NekoCoralAccent = SleekCoralAccent
val NekoGoldBadge = SleekGoldBadge
val NekoTealSuccess = SleekGreenSuccess

// Glassmorphism palette (frosted-glass translucent surfaces + ambient glow)
val GlassSurface = Color(0xE6161924)       // translucent app bars / nav bar
val GlassCard = Color(0xB3222A3E)          // translucent cards
val GlassCardBorder = Color(0x1AFFFFFF)    // hairline white border (10% alpha)
val GlassField = Color(0x40222938)         // translucent input fields
val GlassScrim = Color(0x66000000)         // dim scrim behind dialogs/HUD

// Ambient background gradient (Tadami-style calm deep navy, so glass is visible but every
// element stays readable — the old bright purple/teal blobs washed out text near the edges)
val BgGradientTop = Color(0xFF0E131F)
val BgGradientMid = Color(0xFF090C15)
val BgGradientBottom = Color(0xFF04060C)
val GlowViolet = Color(0x1A2E2A5C)   // faint indigo tint, just enough for depth
val GlowCyan = Color(0x10173A5C)     // faint steel-teal tint

// Typography Colors
val TextPrimaryDark = Color(0xFFF3F5FA)
val TextSecondaryDark = Color(0xFF9EA6C1)
val TextMutedDark = Color(0xFF6B7390)

val TextPrimaryLight = Color(0xFF141724)
val TextSecondaryLight = Color(0xFF5A627B)
val TextMutedLight = Color(0xFF8C95B0)

enum class AppAccent(
    val label: String,
    val primary: Color,
    val container: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val highlight: Color,
) {
    VIOLET("Violet", Color(0xFF8B5CF6), Color(0xFF4C1D95), Color(0xFF7C3AED), Color(0xFFA855F7), Color(0xFFD946EF)),
    BLUE("Blue", Color(0xFF3B82F6), Color(0xFF1E3A8A), Color(0xFF2563EB), Color(0xFF38BDF8), Color(0xFF60A5FA)),
    RED("Red", Color(0xFFEF4444), Color(0xFF7F1D1D), Color(0xFFDC2626), Color(0xFFF87171), Color(0xFFFB7185)),
    GREEN("Green", Color(0xFF22C55E), Color(0xFF14532D), Color(0xFF16A34A), Color(0xFF4ADE80), Color(0xFF86EFAC)),
    ORANGE("Orange", Color(0xFFF97316), Color(0xFF7C2D12), Color(0xFFEA580C), Color(0xFFFBBF24), Color(0xFFFB923C)),
    PINK("Pink", Color(0xFFEC4899), Color(0xFF831843), Color(0xFFDB2777), Color(0xFFF472B6), Color(0xFFF9A8D4)),
    TEAL("Teal", Color(0xFF14B8A6), Color(0xFF134E4A), Color(0xFF0D9488), Color(0xFF2DD4BF), Color(0xFF5EEAD4)),
    GOLD("Gold", Color(0xFFFFB300), Color(0xFF78350F), Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFFFDE68A));
    val buttonGradient: Brush get() = Brush.horizontalGradient(listOf(gradientStart, gradientEnd))
    val gradient: Brush get() = Brush.horizontalGradient(listOf(primary, gradientEnd, highlight))
}
