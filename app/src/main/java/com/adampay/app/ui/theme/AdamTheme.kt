package com.adampay.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.adampay.app.R
import com.adampay.app.domain.model.ThemeMode
import com.adampay.app.domain.money.MoneyFormat

val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
)

/** Tajawal with tabular digits (tools/make_figures.py): amounts don't jump while they change. */
val Figures = FontFamily(
    Font(R.font.figures_regular, FontWeight.Normal),
    Font(R.font.figures_medium, FontWeight.Medium),
    Font(R.font.figures_bold, FontWeight.Bold),
)

@Immutable
data class AdamType(
    /** The big balance on Home. */
    val hero: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyMedium: TextStyle,
    val small: TextStyle,
    val smallMedium: TextStyle,
    val micro: TextStyle,
    /** Amounts in lists and cards (tabular). */
    val amount: TextStyle,
    val amountLarge: TextStyle,
    val button: TextStyle,
)

private fun style(size: TextUnit, weight: FontWeight, lineHeight: TextUnit, family: FontFamily = Tajawal, spacing: TextUnit = TextUnit.Unspecified) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = spacing,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

fun adamType(): AdamType = AdamType(
    hero = style(52.sp, FontWeight.Bold, 58.sp, Figures, (-0.02).em),
    h1 = style(26.sp, FontWeight.Bold, 34.sp),
    h2 = style(20.sp, FontWeight.Bold, 27.sp),
    title = style(16.5.sp, FontWeight.Bold, 23.sp),
    body = style(15.sp, FontWeight.Normal, 22.sp),
    bodyMedium = style(15.sp, FontWeight.Medium, 22.sp),
    small = style(13.5.sp, FontWeight.Normal, 19.sp),
    smallMedium = style(13.5.sp, FontWeight.Medium, 19.sp),
    micro = style(11.5.sp, FontWeight.Medium, 15.sp),
    amount = style(15.5.sp, FontWeight.Bold, 21.sp, Figures),
    amountLarge = style(30.sp, FontWeight.Bold, 36.sp, Figures, (-0.01).em),
    button = style(16.sp, FontWeight.Bold, 21.sp),
)

/** Radii: soft and generous; pocket cards are the most rounded thing on screen. */
object AdamShapes {
    val card = RoundedCornerShape(24.dp)
    val pocketCard = RoundedCornerShape(26.dp)
    val tile = RoundedCornerShape(16.dp)
    val small = RoundedCornerShape(12.dp)
    val sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val pill = RoundedCornerShape(percent = 50)
}

val LocalAdamColors = staticCompositionLocalOf { adamColors(dark = true) }
val LocalAdamType = staticCompositionLocalOf { adamType() }
val LocalHapticsEnabled = staticCompositionLocalOf { true }
val LocalMoneyFormat = staticCompositionLocalOf { MoneyFormat() }
/** "Hide amounts": every amount shows •••• until peeked. */
val LocalHideAmounts = staticCompositionLocalOf { false }

/** Short accessors: `Adam.colors.accent`, `Adam.type.title`. */
object Adam {
    val colors: AdamColors @Composable get() = LocalAdamColors.current
    val type: AdamType @Composable get() = LocalAdamType.current
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun AdamTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accent: Color = Palette.Emerald,
    textScale: Float = 1f,
    hapticsEnabled: Boolean = true,
    moneyFormat: MoneyFormat = MoneyFormat(),
    hideAmounts: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themeMode)
    val colors = remember(dark, accent) { adamColors(dark, accent) }
    val type = remember { adamType() }
    val density = LocalDensity.current
    val scaled = remember(density, textScale) { Density(density.density, density.fontScale * textScale) }
    MaterialTheme(colorScheme = colors.toMaterial(), typography = type.toMaterial()) {
        CompositionLocalProvider(
            LocalAdamColors provides colors,
            LocalAdamType provides type,
            LocalHapticsEnabled provides hapticsEnabled,
            LocalMoneyFormat provides moneyFormat,
            LocalHideAmounts provides hideAmounts,
            LocalDensity provides scaled,
            content = content,
        )
    }
}

private fun AdamColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accentSoft, onPrimaryContainer = accentInk,
        secondary = neutral, onSecondary = Color.White,
        secondaryContainer = surfaceAlt, onSecondaryContainer = text,
        tertiary = income,
        background = bg, onBackground = text,
        surface = surface, onSurface = text,
        surfaceVariant = surfaceAlt, onSurfaceVariant = textSecondary,
        surfaceContainerLowest = surface, surfaceContainerLow = surface,
        surfaceContainer = surfaceAlt, surfaceContainerHigh = surfaceHigh, surfaceContainerHighest = surfaceHigh,
        surfaceTint = Color.Transparent,
        outline = borderStrong, outlineVariant = border,
        error = expense, onError = Color.White,
        scrim = scrim,
        inverseSurface = text, inverseOnSurface = bg, inversePrimary = accentInk,
    )
}

private fun AdamType.toMaterial(): Typography = Typography(
    displayLarge = hero, displayMedium = amountLarge, displaySmall = h1,
    headlineLarge = h1, headlineMedium = h1, headlineSmall = h2,
    titleLarge = h2, titleMedium = title, titleSmall = smallMedium,
    bodyLarge = body, bodyMedium = body, bodySmall = small,
    labelLarge = bodyMedium, labelMedium = smallMedium, labelSmall = micro,
)
