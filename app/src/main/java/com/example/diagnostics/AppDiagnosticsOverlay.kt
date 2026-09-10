package com.example.diagnostics

import android.widget.Toast
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Optional per-scrollable probe: drop `AppScrollProbe("catalog", gridState)` next to any Compose
 * scroll container (LazyColumn / LazyVerticalGrid / rememberScrollState) and its scroll gestures
 * are reported by name ("scroll catalog start/end …ms frames=… janky=… worst=…ms") instead of the
 * generic gesture label. The app-level frame + message probes catch every screen regardless; this
 * only makes the label more precise. Cheap: the read of [ScrollableState.isScrollInProgress] only
 * recomposes this tiny composable.
 */
@Composable
fun AppScrollProbe(label: String, state: ScrollableState) {
    if (!AppDiagnostics.ENABLED) return
    val scrolling = state.isScrollInProgress
    LaunchedEffect(scrolling) {
        if (scrolling) AppDiagnostics.beginScroll(label) else AppDiagnostics.endScroll(label)
    }
    DisposableEffect(label) {
        onDispose { AppDiagnostics.endScroll(label) }
    }
}

/**
 * Reports how long this node spent measuring/laying out (`"$tag.m"`) and drawing (`"$tag.d"`) into
 * [AppDiagnostics]' per-frame cell digest. Layout- and drawing-neutral: the measure pass simply
 * times the delegate, and the draw pass wraps `drawContent()` — no extra layers, no recomposition.
 * Drop it on a lazy item's root to find out whether the item is expensive to measure or to draw.
 */
fun Modifier.cellCost(tag: String): Modifier = this
    .layout { measurable, constraints ->
        val t0 = System.nanoTime()
        val placeable = measurable.measure(constraints)
        AppDiagnostics.noteCellCost("$tag.m", System.nanoTime() - t0)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    .drawWithContent {
        val t0 = System.nanoTime()
        drawContent()
        AppDiagnostics.noteCellCost("$tag.d", System.nanoTime() - t0)
    }

/** App-wide diagnostics overlay: live scroll/frame history with one-tap copy + clear. */
@Composable
fun BoxScope.AppDiagnosticsOverlay() {
    if (!AppDiagnostics.ENABLED || !AppDiagnostics.overlayVisible) return
    AppDiagnostics.noteCompose("overlay")
    val context = LocalContext.current
    AppDiagnostics.init(context)
    var text by remember { mutableStateOf(AppDiagnostics.text()) }
    DisposableEffect(Unit) {
        val prev = AppDiagnostics.onUpdate
        AppDiagnostics.onUpdate = { text = AppDiagnostics.text() }
        onDispose { AppDiagnostics.onUpdate = prev }
    }
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 8.dp)
            .fillMaxWidth(0.9f),
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                ) {
                    Text(
                        "App dx",
                        color = Color(0xFFFFFFFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row {
                        TextButton(
                            onClick = {
                                AppDiagnostics.copy(context)
                                Toast.makeText(context, "App diagnostics copied", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Text("Copy", color = Color(0xFF4FC3F7), fontSize = 12.sp)
                        }
                        TextButton(onClick = { AppDiagnostics.clear() }) {
                            Text("Clear", color = Color(0xFF90A4AE), fontSize = 12.sp)
                        }
                    }
                }
                Text(
                    text = text.ifEmpty { "no events yet — scroll any list / open an extension catalog" },
                    color = Color(0xFFDDDDDD),
                    fontSize = 9.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
                AppDiagnostics.path()?.let { p ->
                    Text(
                        text = p,
                        color = Color(0xFF90A4AE),
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
