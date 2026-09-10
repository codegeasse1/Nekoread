package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.MangaEntity
import com.example.data.source.SourceRegistry
import com.example.diagnostics.AppDiagnostics
import com.example.diagnostics.cellCost
import com.example.ui.theme.SleekGoldBadge
import com.example.ui.theme.SleekVioletPrimary
import com.example.ui.theme.GlassCardBorder

// -------------------------------------------------------------------------------------------------
// Hoisted, allocation-free card decoration.
//
// Everything here used to be rebuilt inside every card on every composition: `Card`/`Surface`
// composables (each one a CompositionLocalProvider + `Modifier.surface` + a per-card
// `pointerInput`), `MaterialTheme.typography.x.copy(...)` TextStyles, `Brush.verticalGradient(...)`
// shaders, `BorderStroke`s and a Material3 `LinearProgressIndicator`. The app-level frame logs
// (dx18) show where that lands: the lazy grid's prefetch scheduler composing **3 cells** held the
// main thread for 75ms, and the long `[scroll library]` frames are `anim`-phase (recomposition)
// with `layout=0` — i.e. ~25ms of composition per cell, which is one dropped frame per row
// entering the viewport. A card is now a plain surface plus Text/Box, with every constant shared
// at file level (see CHANGELOG "take sixteen").
// -------------------------------------------------------------------------------------------------

private val CardShape = RoundedCornerShape(16.dp)
private val ChipShape = RoundedCornerShape(6.dp)
private val BadgeShape = RoundedCornerShape(10.dp)
private val PillShape = RoundedCornerShape(20.dp)
private val TypeCyan = Color(0xFF00B8D4)
private val CompletedGreen = Color(0xFF00E676)

// The cover scrim, hoisted: an inline `Brush.verticalGradient` allocated (and re-created its
// Shader) for every cell on every composition. The gradient carries explicit stops — fully
// transparent over the top 58% of the cover, then a gentle ramp to 50% black at the bottom edge —
// so the artwork stays visible behind the rating star and the Read pill. (It was a
// `Transparent -> 85% black` ramp over the lower 55%, which left the bottom third of every
// thumbnail essentially black: ~33/255 luminance against ~118 at the top of the same cover.)
private val CoverScrim = Brush.verticalGradient(
    0.00f to Color.Transparent,
    0.58f to Color.Transparent,
    1.00f to Color.Black.copy(alpha = 0.5f)
)

// Exactly Type.kt's `titleMedium.copy(fontWeight = Bold, fontSize = 13.sp)` for the grid title and
// `titleMedium.copy(fontWeight = Bold)` for the list title.
private val GridTitleStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 13.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.15.sp
)
private val ListTitleStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.15.sp
)
// `bodySmall` (the theme does not override it, so it keeps Material's default metrics/font).
private val CardBodySmall = TextStyle(
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp
)
private val CardSourceSmall = TextStyle(
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp
)
// Type.kt's `labelSmall` (SansSerif / Bold / 10sp / 14sp / 0.5sp).
private val LabelSmall = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.5.sp
)
private val LabelSmallWhite = LabelSmall.copy(color = Color.White)

// One node instead of two: `border(...) + background(color, shape)` each build a modifier node per
// card and each re-create their paint/shader whenever the size changes. `drawWithCache` builds the
// fill + inset stroke once per size and draws them *behind* the content; the `.clip(CardShape)`
// that follows a call site still clips children to the rounded rect.
private fun Modifier.cardSurface(
    fill: Color,
    stroke: Color,
    strokeWidth: Dp,
    cornerRadius: Dp = 16.dp
): Modifier = this.drawWithCache {
    val r = cornerRadius.toPx()
    val sw = strokeWidth.toPx()
    val fillRadius = CornerRadius(r, r)
    val inset = sw / 2f
    val strokeRadius = CornerRadius((r - inset).coerceAtLeast(0f), (r - inset).coerceAtLeast(0f))
    val strokeSize = Size(size.width - sw, size.height - sw)
    onDrawBehind {
        drawRoundRect(color = fill, size = Size(size.width, size.height), cornerRadius = fillRadius)
        drawRoundRect(
            color = stroke,
            topLeft = Offset(inset, inset),
            size = strokeSize,
            cornerRadius = strokeRadius,
            style = Stroke(width = sw)
        )
    }
}

@Composable
internal fun coverModelFor(manga: MangaEntity): Any? {
    // Extension-sourced covers load through the extension's own client + headers (Referer/Origin),
    // so hotlink-protected CDNs accept them; plain URLs fall back to Coil's default loader.
    return remember(manga.id, manga.sourceId, manga.coverUrl) {
        val url = manga.coverUrl
        if (url.isBlank()) {
            url
        } else if (manga.sourceId.startsWith("ext_")) {
            runCatching { SourceRegistry.source(manga.sourceId).coverImageModel(url) }.getOrNull() ?: url
        } else {
            url
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaGridCard(
    manga: MangaEntity,
    onClick: () -> Unit,
    onReadClick: (() -> Unit)? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AppDiagnostics.noteCompose("gridCard")
    // Plain surface (border + background + clip) instead of Material3 `Card`: see the note at the
    // top of the file. Same look — the border/background/clip order matches `Modifier.surface`,
    // and every text colour that used to be inherited from the `Card`'s content colour is now set
    // explicitly.
    Column(
        modifier = modifier
            .cellCost("card")
            .fillMaxWidth()
            .cardSurface(
                fill = MaterialTheme.colorScheme.surfaceVariant,
                stroke = if (selected) SleekVioletPrimary else GlassCardBorder,
                strokeWidth = if (selected) 2.dp else 1.dp
            )
            .clip(CardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("manga_card_${manga.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
        ) {
            // Static placeholder while the thumbnail loads. Deliberately a plain `AsyncImage` over
            // the card's own tinted container, NOT `SubcomposeAsyncImage`: a SubcomposeLayout per
            // cell is one of the most expensive layouts in Compose, and with one per
            // library/catalog grid cell it dominated the grid's composition cost during a fling —
            // the app-level diagnostics measured 500-900ms frames on the library grid, one hit per
            // row entering the viewport. `AsyncImage` draws nothing while loading (and on failure),
            // so the tinted tile shows through as the placeholder; on success the cover covers it.
            val ctx = LocalContext.current
            val coverModel = coverModelFor(manga)
            // The request is remembered so a recomposition (selection toggle, scroll recycling)
            // reuses the identical object instead of allocating a new one. Deliberately NO
            // `crossfade(true)` here: the fade-in is a Choreographer animation started for every
            // thumbnail that lands, and during a fling that is one per row entering the viewport —
            // the app-level frame logs from the Library/catalog grid showed the `anim` phase
            // eating 20-200ms per janky frame with `layout`/`draw` near zero, which is exactly
            // that pattern. Cards now pop in; the hero keeps its fade.
            val coverRequest = remember(manga.id, coverModel) {
                ImageRequest.Builder(ctx)
                    .data(coverModel)
                    .size(360, 500)
                    .memoryCacheKey("cover:${manga.id}")
                    .diskCacheKey("cover:${manga.id}:${manga.coverUrl}")
                    .build()
            }
            AsyncImage(
                model = coverRequest,
                contentDescription = manga.title,
                modifier = Modifier
                    .fillMaxSize()
                    // The bottom scrim is folded into the image's own draw instead of being a
                    // sibling Box: one fewer layout node per cell, and the gradient shader is the
                    // hoisted, reused [CoverScrim] rather than a per-cell `background(brush)`.
                    // Drawn over the WHOLE node — [CoverScrim]'s own stops make the top 58%
                    // transparent, so there is no offset/size arithmetic to get wrong.
                    .drawWithContent {
                        drawContent()
                        drawRect(brush = CoverScrim)
                    },
                contentScale = ContentScale.Crop,
            )

            // Top Type Chip (MANHWA / MANGA)
            Text(
                text = manga.type,
                style = LabelSmallWhite,
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .background(if (manga.type == "MANHWA") SleekVioletPrimary else TypeCyan, ChipShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            // Unread Count Badge (top right)
            if (manga.unreadCount > 0) {
                Text(
                    text = "${manga.unreadCount}",
                    style = LabelSmallWhite,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.primary, BadgeShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }

            // Selection indicator (long-press multi-select mode)
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = SleekVioletPrimary,
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopEnd)
                        .size(26.dp)
                )
            }

            // Bottom Quick Resume Button
            if (onReadClick != null && manga.lastReadChapterName != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.BottomEnd)
                        .background(SleekVioletPrimary, PillShape)
                        .clip(PillShape)
                        .clickable { onReadClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "▶ Read", style = LabelSmallWhite)
                }
            }

            // Rating at bottom left: one Text with the star as a styled span instead of
            // Row(Icon(Star)) + Spacer + Text — an `Icon` builds a VectorPainter and two layout
            // nodes, on every cell in the grid, and the annotated string is remembered so it is
            // built once per rating rather than per composition.
            val ratingText = remember(manga.rating) { "%.1f".format(manga.rating) }
            val ratingSpanned = remember(ratingText) {
                buildAnnotatedString {
                    withStyle(SpanStyle(color = SleekGoldBadge)) { append("★") }
                    append(' ')
                    append(ratingText)
                }
            }
            Text(
                text = ratingSpanned,
                color = Color.White,
                style = LabelSmall,
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomStart)
            )
        }

        // Title & Source Info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = manga.title,
                style = GridTitleStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                // Reserve two lines even for a one-line title: it makes every grid cell the same
                // height, which lets the lazy grid work out item extents without measuring each.
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = manga.sourceName,
                style = CardSourceSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Always reserve the progress row (7dp) even when there is no progress to show, so the
            // cell height stays constant in both cases. The bar is now drawn by a single node's
            // `drawBehind` (two rounded rects) instead of a track Box wrapping a clipped inner Box:
            // three layout nodes + a clip layer per cell before, zero extra nodes now.
            val progressTrack = MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .drawBehind {
                        if (manga.lastReadPage > 1) {
                            val barHeight = 3.dp.toPx()
                            val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            drawRoundRect(
                                color = progressTrack,
                                size = Size(size.width, barHeight),
                                cornerRadius = radius
                            )
                            drawRoundRect(
                                color = SleekVioletPrimary,
                                size = Size(size.width * 0.6f, barHeight),
                                cornerRadius = radius
                            )
                        }
                    }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaListCard(
    manga: MangaEntity,
    onClick: () -> Unit,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AppDiagnostics.noteCompose("listCard")
    Row(
        modifier = modifier
            .cellCost("listCard")
            .fillMaxWidth()
            .cardSurface(
                fill = MaterialTheme.colorScheme.surface,
                stroke = if (selected) SleekVioletPrimary else GlassCardBorder,
                strokeWidth = if (selected) 2.dp else 1.dp
            )
            .clip(CardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("manga_list_item_${manga.id}")
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = SleekVioletPrimary,
                modifier = Modifier.padding(start = 4.dp).size(26.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        val ctx = LocalContext.current
        val coverModel = coverModelFor(manga)
        // Plain `AsyncImage` over a tinted Box (see MangaGridCard): no per-row SubcomposeLayout,
        // no crossfade (its per-image animation was part of the `anim` cost in the logs), and no
        // indeterminate `CircularProgressIndicator` — that one spins on the main thread forever
        // for every composed row, even once the cover has covered it, so it was a continuous
        // animation source during a list fling. The tinted tile is the placeholder.
        val coverRequest = remember(manga.id, coverModel) {
            ImageRequest.Builder(ctx)
                .data(coverModel)
                .size(180, 240)
                .memoryCacheKey("cover:${manga.id}")
                .diskCacheKey("cover:${manga.id}:${manga.coverUrl}")
                .build()
        }
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = coverRequest,
                contentDescription = manga.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = manga.title,
                style = ListTitleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${manga.sourceName} • ${manga.author}",
                style = CardBodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = manga.type,
                    style = LabelSmallWhite,
                    modifier = Modifier
                        .background(if (manga.type == "MANHWA") SleekVioletPrimary else TypeCyan, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = manga.status,
                    style = LabelSmall,
                    color = if (manga.status == "COMPLETED") CompletedGreen else SleekGoldBadge
                )
            }
        }

        if (manga.unreadCount > 0) {
            Text(
                text = "${manga.unreadCount}",
                style = LabelSmallWhite,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
