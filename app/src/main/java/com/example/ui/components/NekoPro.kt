package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.example.ui.theme.AppAccent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.SleekGoldBadge

val ProViolet = Color(0xFF8B5CF6)
val proPrimary()Deep = Color(0xFF6D28D9)
val ProCyan = Color(0xFF22D3EE)
val ProGold = Color(0xFFFFB300)
val ProNsfwRed = Color(0xFFFF5252)
val ProCardFill = Color(0xB3222A3E)

val LocalAppAccent = compositionLocalOf { AppAccent.VIOLET }

@Composable
fun proPrimary(): Color = LocalAppAccent.current.primary

@Composable
fun proButtonGradient(): Brush = LocalAppAccent.current.buttonGradient

@Composable
fun proGradient(): Brush = LocalAppAccent.current.gradient

val ProGradient = Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFFA855F7), Color(0xFFD946EF)))
val ProButtonGradient = Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFA855F7)))

@Composable
fun ProTitle(text: String, accent: String? = null, modifier: Modifier = Modifier) {
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    Text(text = text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (accent != null) {
      Spacer(Modifier.width(6.dp))
      Text(text = accent, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, brush = proGradient()), maxLines = 1)
    }
  }
}

@Composable
fun ProSubtitle(text: String, modifier: Modifier = Modifier) {
  Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
fun ProCountBadge(count: Int, modifier: Modifier = Modifier) {
  Box(modifier = modifier.clip(CircleShape).background(proPrimary()).padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
    Text(text = "$count", style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
  }
}

@Composable
fun ProModeChip(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val bg = if (selected) proButtonGradient() else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
  val border = if (selected) null else BorderStroke(1.dp, GlassCardBorder)
  Surface(shape = RoundedCornerShape(20.dp), border = border, color = Color.Transparent, modifier = modifier.height(34.dp)) {
    Box(modifier = Modifier.background(bg, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
          Icon(icon, null, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(5.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
      }
    }
  }
}

@Composable
fun ProModeRow(mode: String, onModeChange: (String) -> Unit, modifier: Modifier = Modifier) {
  Row(modifier = modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    ProModeChip("Popular", Icons.Default.LocalFireDepartment, mode == "popular", { onModeChange("popular") })
    ProModeChip("Latest", Icons.Default.Refresh, mode == "latest", { onModeChange("latest") })
    ProModeChip("Top rated", Icons.Default.Star, mode == "top_rated", { onModeChange("top_rated") })
    ProModeChip("Filter", Icons.Default.FilterList, mode == "filter", { onModeChange("filter") })
  }
}

@Composable
fun ProGenreRow(genres: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
  Row(modifier = modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    genres.forEach { g ->
      val sel = g == selected
      Surface(shape = RoundedCornerShape(16.dp), color = if (sel) proPrimary() else Color.Transparent, border = if (sel) null else BorderStroke(1.dp, GlassCardBorder), modifier = Modifier.height(30.dp)) {
        Box(Modifier.clickable { onSelect(g) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
          Text(g, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal), color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
fun ProPillTabs(tabs: List<Pair<String, ImageVector?>>, selected: Int, badgeCount: Int = 0, badgeTab: Int = -1, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  Row(modifier = modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    tabs.forEachIndexed { i, (label, icon) ->
      val sel = i == selected
      Surface(shape = RoundedCornerShape(18.dp), color = if (sel) Color.Transparent else Color.Transparent, border = if (sel) null else BorderStroke(1.dp, GlassCardBorder), modifier = Modifier.height(34.dp)) {
        Box(Modifier.background(if (sel) proButtonGradient() else Brush.horizontalGradient(listOf(ProCardFill, ProCardFill)), RoundedCornerShape(18.dp)).clickable { onSelect(i) }.padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
              Icon(icon, null, tint = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
              Spacer(Modifier.width(5.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            if (i == badgeTab && badgeCount > 0) {
              Spacer(Modifier.width(6.dp))
              Box(Modifier.clip(CircleShape).background(ProGold).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text("$badgeCount", style = MaterialTheme.typography.labelSmall.copy(color = Color.Black, fontWeight = FontWeight.Bold))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ProSegmented(options: List<Pair<String, ImageVector?>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  Surface(shape = RoundedCornerShape(24.dp), color = ProCardFill, border = BorderStroke(1.dp, GlassCardBorder), modifier = modifier.fillMaxWidth().height(46.dp)) {
    Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      options.forEachIndexed { i, (label, icon) ->
        val sel = i == selected
        Box(Modifier.weight(1f).background(if (sel) proButtonGradient() else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)), RoundedCornerShape(20.dp)).clickable { onSelect(i) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
              Icon(icon, null, tint = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

@Composable
fun ProEmptyCard(title: String, titleAccent: String, body: String, primaryLabel: String, primaryIcon: ImageVector, onPrimary: () -> Unit, art: @Composable () -> Unit, modifier: Modifier = Modifier) {
  GlassCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) { art() }
      Spacer(Modifier.height(14.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title + " ", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurface)
        Text(titleAccent, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, brush = proGradient()))
      }
      Spacer(Modifier.height(6.dp))
      Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
      Spacer(Modifier.height(20.dp))
      Box(Modifier.background(proButtonGradient(), RoundedCornerShape(24.dp)).clickable(onClick = onPrimary).padding(horizontal = 22.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(primaryIcon, null, tint = Color.White, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(primaryLabel, style = MaterialTheme.typography.labelLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
          Spacer(Modifier.width(8.dp))
          Icon(Icons.Default.ArrowForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}

@Composable
fun ProEmptyBookArt(modifier: Modifier = Modifier) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF1B2140), border = BorderStroke(1.dp, proPrimary().copy(alpha = 0.5f)), modifier = Modifier.size(96.dp)) {
      Box(contentAlignment = Alignment.Center) {
        Icon(Icons.Default.MenuBook, null, tint = proPrimary(), modifier = Modifier.size(48.dp))
      }
    }
    Icon(Icons.Default.AutoAwesome, null, tint = proPrimary().copy(alpha = 0.9f), modifier = Modifier.align(Alignment.TopEnd).size(22.dp))
    Icon(Icons.Default.AutoAwesome, null, tint = ProCyan.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.BottomStart).size(16.dp))
  }
}

@Composable
fun ProEmptyHistoryArt(modifier: Modifier = Modifier) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Icon(Icons.Default.MenuBook, null, tint = proPrimary().copy(alpha = 0.85f), modifier = Modifier.size(72.dp))
    Box(Modifier.align(Alignment.TopEnd).clip(CircleShape).background(proPrimary().copy(alpha = 0.2f)).padding(6.dp)) {
      Icon(Icons.Default.History, null, tint = proPrimary(), modifier = Modifier.size(26.dp))
    }
    Icon(Icons.Default.AutoAwesome, null, tint = proPrimary().copy(alpha = 0.6f), modifier = Modifier.align(Alignment.BottomStart).size(16.dp))
    Icon(Icons.Default.Star, null, tint = ProGold.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.TopStart).size(14.dp))
  }
}

@Composable
fun ProSettingsSection(icon: ImageVector, title: String, subtitle: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  GlassCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), borderWidth = if (expanded) 1.dp else 1.dp) {
    Column(Modifier.fillMaxWidth()) {
      Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(proButtonGradient()).padding(9.dp)) {
          Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
          Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (expanded) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) { content() }
      }
    }
  }
}

@Composable
fun ProSettingRow(icon: ImageVector, title: String, subtitle: String, trailing: @Composable () -> Unit, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
  val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
  Row(m.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, tint = proPrimary(), modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(8.dp))
    trailing()
  }
}

@Composable
fun ProDropdownPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, GlassCardBorder), color = ProCardFill, modifier = modifier) {
    Row(Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.width(4.dp))
      Icon(Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
    }
  }
}

@Composable
fun ProPrimaryButton(label: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(modifier.background(proButtonGradient(), RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (icon != null) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
      }
      Text(label, style = MaterialTheme.typography.labelLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
    }
  }
}

@Composable
fun ProCheckRow(label: String, checked: Boolean, modifier: Modifier = Modifier) {
  Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    if (checked) {
      Box(Modifier.clip(CircleShape).background(proPrimary()).padding(3.dp)) {
        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
      }
    } else {
      Box(Modifier.size(19.dp).clip(CircleShape).background(Color.Transparent))
    }
    Spacer(Modifier.width(8.dp))
    Text(label, style = MaterialTheme.typography.bodyMedium, color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    if (checked) {
      Spacer(Modifier.weight(1f))
      Icon(Icons.Default.ChevronRight, null, tint = proPrimary(), modifier = Modifier.size(16.dp))
    }
  }
}

@Composable
fun ProTypeBadge(text: String, modifier: Modifier = Modifier) {
  Box(modifier.clip(RoundedCornerShape(8.dp)).background(proButtonGradient()).padding(horizontal = 8.dp, vertical = 3.dp)) {
    Text(text, style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.ExtraBold))
  }
}

@Composable
fun ProNsfwBadge(modifier: Modifier = Modifier) {
  Box(modifier.clip(RoundedCornerShape(6.dp)).background(ProNsfwRed).padding(horizontal = 6.dp, vertical = 2.dp)) {
    Text("NSFW", style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
  }
}

@Composable
fun ProLangBadge(text: String, modifier: Modifier = Modifier) {
  Surface(shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, GlassCardBorder), color = Color(0x40222938), modifier = modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
  }
}

@Composable
fun ProGridIconButton(icon: ImageVector, desc: String, onClick: () -> Unit, active: Boolean = false, modifier: Modifier = Modifier) {
  Surface(shape = CircleShape, color = if (active) proPrimary() else ProCardFill, border = if (active) null else BorderStroke(1.dp, GlassCardBorder), modifier = modifier.size(40.dp)) {
    Box(Modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
      Icon(icon, desc, tint = if (active) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
    }
  }
}
