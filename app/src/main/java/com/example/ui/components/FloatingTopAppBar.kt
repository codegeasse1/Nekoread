package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassCardBorder

/**
 * Floating rounded glass pill (Hikari/taskbar style) — the exact same look as the app's bottom
 * nav pill: translucent fill, hairline border, soft shadow. Used for the top app bars so the
 * chrome reads as a compact capsule hovering in the air instead of a full-width block.
 */
@Composable
fun FloatingGlassPill(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(30.dp),
    content: @Composable () -> Unit
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, GlassCardBorder),
        shadowElevation = 10.dp,
        modifier = modifier
    ) {
        content()
    }
}

/**
 * Scaffold topBar wrapper: the pill sits directly BELOW the status bar (no gap, no wasted space)
 * and is only as tall as its content — the "glass box" stays small instead of stretching up behind
 * the status bar. Compact: small side margins, ~2dp of vertical breathing room.
 */
@Composable
fun FloatingTopAppBar(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp)
    ) {
        FloatingGlassPill(shape = shape) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                content()
            }
        }
    }
}
