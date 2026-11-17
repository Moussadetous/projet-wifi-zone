package com.wifizone.mikhmon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifizone.mikhmon.print.AndroidCanvas2D
import com.wifizone.mikhmon.tickets.gfx.Icon
import com.wifizone.mikhmon.tickets.gfx.Icons

/** Icône vectorielle partagée avec les tickets (mêmes tracés). */
@Composable
fun VIcon(icon: Icon, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val ctx = LocalContext.current
    Canvas(modifier.size(size)) {
        drawIntoCanvas { c -> Icons.draw(AndroidCanvas2D(c.nativeCanvas, ctx), icon, 0f, 0f, this.size.minDimension, tint.toArgb()) }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 16.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .shadow(1.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x14000000))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Brand.Muted, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        if (action != null && onAction != null) Text(action, color = Brand.Indigo, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable(onClick = onAction))
    }
}

@Composable
fun IconBadge(icon: Icon, color: Color, size: Dp = 40.dp, brush: Brush? = null) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f))
            .background(brush ?: Brush.linearGradient(listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0.10f)))),
        contentAlignment = Alignment.Center,
    ) { VIcon(icon, if (brush != null) Color.White else color, size = size * 0.52f) }
}

/** Raccourci rapide (tuile carrée avec icône en dégradé). */
@Composable
fun Shortcut(icon: Icon, label: String, colors: List<Color>, modifier: Modifier = Modifier, badge: String? = null, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                Modifier.size(58.dp).shadow(6.dp, RoundedCornerShape(19.dp), spotColor = colors.first().copy(alpha = 0.5f))
                    .clip(RoundedCornerShape(19.dp)).background(Brush.linearGradient(colors)),
                contentAlignment = Alignment.Center,
            ) { VIcon(icon, Color.White, size = 28.dp) }
            if (badge != null) Text(
                badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).offsetBadge().clip(CircleShape).background(Brand.Red).padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, fontWeight = FontWeight.SemiBold, color = Brand.Ink)
    }
}

private fun Modifier.offsetBadge() = this.padding(0.dp)

@Composable
fun StatTile(label: String, value: String, icon: Icon, color: Color, modifier: Modifier = Modifier, sub: String? = null, onClick: (() -> Unit)? = null) {
    Panel(modifier, padding = 14.dp, onClick = onClick) {
        IconBadge(icon, color, 34.dp)
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 1)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Text(
        text, color = if (filled) Color.White else color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(if (filled) color else color.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, brush: Brush = Brand.Hero, enabled: Boolean = true, loading: Boolean = false, icon: Icon? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled && !loading, contentPadding = ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        modifier = modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
            .background(if (enabled) brush else Brush.linearGradient(listOf(Color(0xFFB8BECB), Color(0xFFB8BECB)))),
        shape = RoundedCornerShape(18.dp),
    ) {
        if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
        else {
            if (icon != null) { VIcon(icon, Color.White, size = 20.dp); Spacer(Modifier.width(10.dp)) }
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFE6E9F2)).padding(3.dp)) {
        options.forEachIndexed { i, s ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(11.dp)).background(Color.White) else Modifier)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(s, style = MaterialTheme.typography.labelLarge, color = if (on) Brand.Ink else Brand.Muted, maxLines = 1) }
        }
    }
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun SelectChip(text: String, selected: Boolean, modifier: Modifier = Modifier, color: Color = Brand.Indigo, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Brand.Ink, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(if (selected) color else Color.White)
            .border(1.dp, if (selected) color else Brand.Line, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun EmptyState(icon: Icon, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, Brand.Muted, 56.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Brand.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorNote(text: String, modifier: Modifier = Modifier) {
    Surface(color = Brand.Red.copy(alpha = 0.08f), shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth()) {
        Text(text, color = Brand.Red, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

@Composable
fun rememberMono() = LocalContext.current.let { ctx -> remember { monoFamily(ctx.assets) } }

object Fmt {
    fun rate(bps: Long): String = when {
        bps >= 1_000_000_000 -> String.format("%.1f Gb/s", bps / 1e9)
        bps >= 1_000_000 -> String.format("%.1f Mb/s", bps / 1e6)
        bps >= 1_000 -> String.format("%.0f kb/s", bps / 1e3)
        else -> "$bps b/s"
    }.replace('.', ',')

    fun bytes(n: Long): String = when {
        n >= 1L shl 30 -> String.format("%.2f Go", n / (1L shl 30).toDouble())
        n >= 1L shl 20 -> String.format("%.0f Mo", n / (1L shl 20).toDouble())
        n >= 1L shl 10 -> String.format("%.0f Ko", n / (1L shl 10).toDouble())
        else -> "$n o"
    }.replace('.', ',')

    fun money(n: Long, cur: String = "F") = n.toString().reversed().chunked(3).joinToString(" ").reversed() + " " + cur

    /** Durée RouterOS « 1d3h12m » → « 1 j 3 h ». */
    fun ros(d: String): String {
        if (d.isBlank()) return "—"
        val m = Regex("(\\d+)([wdhms])").findAll(d).associate { it.groupValues[2] to it.groupValues[1].toLong() }
        val w = m["w"] ?: 0; val dd = (m["d"] ?: 0) + w * 7; val h = m["h"] ?: 0; val mi = m["m"] ?: 0; val s = m["s"] ?: 0
        return when {
            dd > 0 -> "$dd j ${h} h"
            h > 0 -> "$h h ${mi.toString().padStart(2, '0')}"
            mi > 0 -> "$mi min"
            else -> "$s s"
        }
    }
}
