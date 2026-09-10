package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.MangaEntity
import com.example.data.source.SourceRegistry
import com.example.diagnostics.AppDiagnostics
import com.example.ui.theme.SleekGoldBadge
import com.example.ui.theme.SleekVioletPrimary
import com.example.ui.theme.GlassCardBorder

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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("manga_card_${manga.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SleekVioletPrimary else GlassCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
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
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                // Bottom scrim only, over the lower ~55% of the cell where the rating / Read pill
                // sit. Previously a full-cell gradient (startY=150) drew an extra full tile every
                // frame while flinging for no visual gain.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.55f)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Top Type Chip (MANHWA / MANGA)
                Surface(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart),
                    color = if (manga.type == "MANHWA") SleekVioletPrimary else Color(0xFF00B8D4),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = manga.type,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    )
                }

                // Unread Count Badge (top right)
                if (manga.unreadCount > 0) {
                    Surface(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd),
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${manga.unreadCount}",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
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
                    Surface(
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onReadClick() },
                        color = SleekVioletPrimary
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Read",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Read",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                // Rating at bottom left
                val ratingText = remember(manga.rating) { "%.1f".format(manga.rating) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.BottomStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rating",
                        tint = SleekGoldBadge,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = ratingText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Title & Source Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    maxLines = 2,
                    // Reserve two lines even for a one-line title: it makes every grid cell the same
                    // height, which lets the lazy grid work out item extents without measuring each.
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = manga.sourceName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Always reserve the progress row (7dp) even when there is no progress to show, so
                // the cell height stays constant in both cases — see the `minLines` note above.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                ) {
                    if (manga.lastReadPage > 1) {
                        LinearProgressIndicator(
                            progress = { 0.6f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.TopStart)
                                .clip(RoundedCornerShape(2.dp)),
                            color = SleekVioletPrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("manga_list_item_${manga.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SleekVioletPrimary else GlassCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${manga.sourceName} • ${manga.author}",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (manga.type == "MANHWA") SleekVioletPrimary else Color(0xFF00B8D4),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = manga.type,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = manga.status,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (manga.status == "COMPLETED") Color(0xFF00E676) else SleekGoldBadge
                        )
                    )
                }
            }

            if (manga.unreadCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "${manga.unreadCount}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}
