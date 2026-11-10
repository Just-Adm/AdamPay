package com.adampay.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adampay.app.R
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes

/** A soft raised surface: no shadow in dark mode, a hairline in light mode. */
@Composable
fun AdamCard(
    modifier: Modifier = Modifier,
    shape: Shape = AdamShapes.card,
    padding: PaddingValues = PaddingValues(18.dp),
    color: Color = Adam.colors.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Adam.colors
    Column(
        modifier
            .clip(shape)
            .background(color)
            .then(if (!c.isDark) Modifier.border(1.dp, c.border, shape) else Modifier)
            .then(if (onClick != null) Modifier.tap(pressedScale = 0.98f, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Round icon badge: categories, pockets, settings rows. */
@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tint: Color = Adam.colors.accent,
    background: Color = Adam.colors.accentSoft,
    size: Dp = 42.dp,
    iconSize: Dp = 21.dp,
    shape: Shape = CircleShape,
) {
    Box(modifier.size(size).clip(shape).background(background), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** A category colour as icon tint + soft background that works on both themes. */
@Composable
fun tintPair(argb: Long): Pair<Color, Color> {
    val base = Color(argb)
    val bg = if (Adam.colors.isDark) base.copy(alpha = 0.16f) else base.copy(alpha = 0.14f)
    return base to bg
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(top = 26.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Adam.type.title, color = Adam.colors.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action, style = Adam.type.smallMedium, color = Adam.colors.accentInk,
                modifier = Modifier.clip(AdamShapes.pill).tap(press = false, onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Small rounded chip; [selected] fills it with the accent. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    @DrawableRes icon: Int? = null,
    iconTint: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Adam.colors
    val bg by animateColorAsState(if (selected) c.accent else c.surfaceAlt, tween(180), label = "chipBg")
    val fg by animateColorAsState(if (selected) c.onAccent else c.text, tween(180), label = "chipFg")
    Row(
        modifier
            .clip(AdamShapes.pill)
            .background(bg)
            .then(if (onClick != null) Modifier.tap(role = Role.Tab, onClick = onClick) else Modifier)
            .padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = if (selected) fg else iconTint ?: c.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Adam.type.smallMedium, color = fg, maxLines = 1)
    }
}

/** Two to four mutually exclusive options in a sliding pill. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: (T) -> Color? = { null },
) {
    val c = Adam.colors
    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp).clip(AdamShapes.pill).background(c.surfaceAlt).padding(4.dp),
    ) {
        val w = maxWidth / options.size
        val index = options.indexOf(selected).coerceAtLeast(0)
        val x by animateDpAsState(w * index, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "seg")
        val fill = selectedColor(selected) ?: c.surfaceHigh
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(AdamShapes.pill).background(fill))
        Row(Modifier.fillMaxSize()) {
            options.forEach { o ->
                val on = o == selected
                val tint by animateColorAsState(
                    if (on) (if (selectedColor(o) != null) Color(0xFF17140D) else c.text) else c.textSecondary,
                    tween(160), label = "segText",
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(AdamShapes.pill).tap(press = false, role = Role.Tab) { onSelect(o) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(o), style = Adam.type.smallMedium, color = tint, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    color: Color = Adam.colors.accent,
    contentColor: Color = Adam.colors.onAccent,
) {
    val c = Adam.colors
    val bg by animateColorAsState(if (enabled) color else c.surfaceAlt, tween(180), label = "btnBg")
    val fg by animateColorAsState(if (enabled) contentColor else c.textMuted, tween(180), label = "btnFg")
    Row(
        modifier
            .height(54.dp)
            .clip(AdamShapes.pill)
            .background(bg)
            .tap(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Adam.type.button, color = fg, maxLines = 1)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Adam.colors.text, @DrawableRes icon: Int? = null) {
    Row(
        modifier
            .height(48.dp)
            .clip(AdamShapes.pill)
            .background(Adam.colors.surfaceAlt)
            .tap(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Adam.type.bodyMedium, color = color, maxLines = 1)
    }
}

/** Round icon-only button. */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    tint: Color = Adam.colors.text,
    background: Color = Adam.colors.surfaceAlt,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(background).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun AdamDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.8.dp).background(Adam.colors.border))
}

/**
 * Settings list row: icon, title + subtitle, trailing slot (a chevron when clickable).
 * [onClick] is the last parameter on purpose: a trailing lambda is the click, never content.
 */
@Composable
fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(AdamShapes.tile)
            .then(if (onClick != null) Modifier.tap(press = false, onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, size = 38.dp, iconSize = 19.dp, shape = RoundedCornerShape(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Adam.type.bodyMedium, color = Adam.colors.text)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Adam.type.small, color = Adam.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(painterResource(R.drawable.ic_chevron_right), null, tint = Adam.colors.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun AdamSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = Adam.colors
    val haptics = rememberHaptics()
    Switch(
        checked = checked,
        onCheckedChange = { haptics.click(); onCheckedChange(it) },
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onAccent, checkedTrackColor = c.accent, checkedBorderColor = c.accent,
            uncheckedThumbColor = c.textSecondary, uncheckedTrackColor = c.surfaceAlt, uncheckedBorderColor = c.borderStrong,
        ),
    )
}

@Composable
fun SwitchRow(@DrawableRes icon: Int, title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    SettingsRow(icon, title, subtitle = subtitle, trailing = { AdamSwitch(checked, onChange) }, onClick = { onChange(!checked) })
}

@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, size = 60.dp, iconSize = 28.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, style = Adam.type.title, color = Adam.colors.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = Adam.type.small, color = Adam.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

/** A pushed settings page: back button, title, scrolling content. */
@Composable
fun SubScreen(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val c = Adam.colors
    Column(modifier.fillMaxSize().background(c.bg)) {
        Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack, background = Color.Transparent)
            Spacer(Modifier.width(6.dp))
            Text(title, style = Adam.type.h2, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(
            Modifier.weight(1f).then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 18.dp),
        ) {
            content()
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** Single-line text field in the app's style: label above, soft filled box. */
@Composable
fun AdamTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    rtl: Boolean? = null,
) {
    val c = Adam.colors
    Column(modifier.fillMaxWidth()) {
        Text(label, style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides when (rtl) {
                true -> androidx.compose.ui.unit.LayoutDirection.Rtl
                false -> androidx.compose.ui.unit.LayoutDirection.Ltr
                null -> androidx.compose.ui.platform.LocalLayoutDirection.current
            },
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = Adam.type.body.copy(color = c.text),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().clip(AdamShapes.tile).background(c.surfaceAlt).padding(horizontal = 14.dp, vertical = 13.dp)) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = Adam.type.body, color = c.textMuted)
                        inner()
                    }
                },
            )
        }
    }
}
