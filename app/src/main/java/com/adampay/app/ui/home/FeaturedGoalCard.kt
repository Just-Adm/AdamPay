package com.adampay.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adampay.app.R
import com.adampay.app.data.db.GoalEntity
import com.adampay.app.ui.GoalView
import com.adampay.app.ui.components.Amount
import com.adampay.app.ui.components.AppIcons
import com.adampay.app.ui.components.moneyText
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes

/** The featured goal card's own colours: pink, whatever the app's theme or accent. */
private object Pink {
    val hot = Color(0xFFFF4D9D)
    val deep = Color(0xFFD81B7A)
    val plum = Color(0xFF5B0F45)
    val night = Color(0xFF240A1E)
    val glow = Color(0xFFFF8CC6)
    val ink = Color.White
    val soft = Color.White.copy(alpha = 0.72f)
    val track = Color.White.copy(alpha = 0.18f)
}

/** Progress in four levels: 0–25 % is level 1, … 75–100 % is level 4; a reached goal shows 4 of 4. */
object GoalLevels {
    const val COUNT = 4
    fun level(fraction: Float): Int = if (fraction >= 1f) COUNT else (fraction * COUNT).toInt().coerceIn(0, COUNT - 1) + 1
}

/**
 * The goal under the total balance: a pink card with the goal's icon and name, saved / target, a
 * four-level progress bar that fills when it appears (with a soft shine), and what is left.
 * With no goal yet it invites the user to add one ([onAdd]).
 */
@Composable
fun FeaturedGoalCard(view: GoalView?, onOpen: (GoalEntity) -> Unit, onAdd: () -> Unit, interactive: Boolean = true) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val still = LocalInspectionMode.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(AdamShapes.pocketCard)
            .background(Brush.linearGradient(listOf(Pink.hot, Pink.deep, Pink.plum, Pink.night), start = Offset(0f, 0f), end = Offset(1100f, 700f)))
            .drawBehind {
                // Fine diagonal lines on the far side and a soft glow behind the icon: a texture of its own.
                val far = if (rtl) 0f else size.width * 0.55f
                clipRect(left = far, right = far + size.width * 0.45f) {
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(Color.White.copy(alpha = 0.06f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.2.dp.toPx())
                        x += 14.dp.toPx()
                    }
                }
                val g = Offset(if (rtl) size.width - 40.dp.toPx() else 40.dp.toPx(), 40.dp.toPx())
                drawCircle(Brush.radialGradient(listOf(Pink.glow.copy(alpha = 0.45f), Color.Transparent), center = g, radius = 90.dp.toPx()), radius = 90.dp.toPx(), center = g)
            }
            .then(
                if (interactive) Modifier.tap(pressedScale = 0.98f) { if (view != null) onOpen(view.goal) else onAdd() } else Modifier,
            )
            .padding(18.dp),
    ) {
        if (view == null) {
            EmptyFeatured()
            return@Box
        }
        val g = view.goal
        val p = view.progress
        val level = GoalLevels.level(p.fraction)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(AppIcons.of(g.icon)), null, tint = Pink.ink, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.featured_label), style = Adam.type.micro, color = Pink.soft, maxLines = 1)
                    Text(
                        g.name, style = Adam.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = Pink.ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Sparkle(Modifier.size(22.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Amount(
                    p.saved, style = Adam.type.amountLarge, color = Pink.ink, labelStyle = Adam.type.small, labelColor = Pink.soft,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.featured_of, moneyText(p.target)), style = Adam.type.small, color = Pink.soft,
                    modifier = Modifier.alignByBaseline(), maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${(p.fraction * 100).toInt()}%", style = Adam.type.amountLarge.copy(fontSize = 26.sp), color = Pink.ink,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Spacer(Modifier.height(12.dp))
            LevelBar(p.fraction, animate = !still)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(AdamShapes.pill).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(stringResource(R.string.featured_level, level, GoalLevels.COUNT), style = Adam.type.micro.copy(fontWeight = FontWeight.Bold), color = Pink.ink, maxLines = 1)
                }
                Spacer(Modifier.width(10.dp))
                val line = when {
                    p.reached -> stringResource(R.string.goal_reached)
                    p.perWeekNeeded != null -> stringResource(R.string.goal_per_week, moneyText(p.perWeekNeeded))
                    else -> stringResource(R.string.featured_left, moneyText(p.remaining))
                }
                Text(line, style = Adam.type.small, color = Pink.soft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmptyFeatured() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_add), null, tint = Pink.ink, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.featured_label), style = Adam.type.micro, color = Pink.soft)
            Text(stringResource(R.string.featured_empty), style = Adam.type.bodyMedium, color = Pink.ink)
        }
        Sparkle(Modifier.size(22.dp))
    }
}

/** Four segments with small gaps; the bar grows in when shown and a light shine runs across the filled part. */
@Composable
private fun LevelBar(fraction: Float, animate: Boolean) {
    val fill = remember { Animatable(if (animate) 0f else fraction) }
    LaunchedEffect(fraction) { if (animate) fill.animateTo(fraction, tween(900, easing = FastOutSlowInEasing)) else fill.snapTo(fraction) }
    val shine = if (animate) {
        val t = rememberInfiniteTransition(label = "shine")
        t.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(2600, delayMillis = 900, easing = LinearEasing), RepeatMode.Restart), label = "x").value
    } else -1f
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val gap = 5.dp.toPx()
        val n = GoalLevels.COUNT
        val segW = (size.width - gap * (n - 1)) / n
        val r = CornerRadius(size.height / 2)
        val filledW = size.width * fill.value
        for (i in 0 until n) {
            val left = if (rtl) size.width - (i + 1) * segW - i * gap else i * (segW + gap)
            drawRoundRect(Pink.track, Offset(left, 0f), Size(segW, size.height), r)
            // How much of this segment is filled (measured from the start side).
            val segStart = i * (segW + gap)
            val w = (filledW - segStart).coerceIn(0f, segW)
            if (w > 0f) {
                val x = if (rtl) left + segW - w else left
                drawRoundRect(Color.White, Offset(x, 0f), Size(w, size.height), r)
            }
        }
        if (shine in 0f..1f && filledW > 0f) {
            val cx = if (rtl) size.width - filledW * shine else filledW * shine
            val band = 36.dp.toPx()
            clipRect(left = if (rtl) size.width - filledW else 0f, right = if (rtl) size.width else filledW) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Pink.hot.copy(alpha = 0.55f), Color.Transparent), startX = cx - band, endX = cx + band),
                    topLeft = Offset(cx - band, 0f), size = Size(band * 2, size.height),
                )
            }
        }
    }
}

/** A small four-point star. */
@Composable
private fun Sparkle(modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        val k = r * 0.22f
        val path = Path().apply {
            moveTo(c.x, c.y - r); lineTo(c.x + k, c.y - k); lineTo(c.x + r, c.y); lineTo(c.x + k, c.y + k)
            lineTo(c.x, c.y + r); lineTo(c.x - k, c.y + k); lineTo(c.x - r, c.y); lineTo(c.x - k, c.y - k); close()
        }
        drawPath(path, Color.White.copy(alpha = 0.9f))
        drawCircle(Color.White.copy(alpha = 0.55f), radius = r * 0.16f, center = Offset(c.x + r * 0.85f, c.y - r * 0.75f))
    }
}
