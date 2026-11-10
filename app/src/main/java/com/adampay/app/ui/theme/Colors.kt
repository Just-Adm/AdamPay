package com.adampay.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Adam Pay colour tokens. Dark is the default: a near-black with a faint green tint, raised surfaces
 * in deep green-greys and an emerald accent (formal, bank-like). Money colours are soft: expense =
 * clay red, income = mint, transfer = slate. Colour is never the only signal (signs and icons go with it).
 */
@Immutable
data class AdamColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceHigh: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    /** Accent dark enough to read as text on [bg]. */
    val accentInk: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val expense: Color,
    val expenseSoft: Color,
    val income: Color,
    val incomeSoft: Color,
    val neutral: Color,
    val neutralSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val scrim: Color,
    /** Faint glow behind the balance on Home. */
    val glow: Color,
)

object Palette {
    val Emerald = Color(0xFF2FB36D)

    /** Accent choices in Settings → Appearance: greens first (emerald is the default), then others. */
    val Accents = listOf(
        Color(0xFF2FB36D), // emerald
        Color(0xFF5ED39B), // mint
        Color(0xFF1FA58F), // teal green
        Color(0xFF8CC63F), // leaf
        Color(0xFF4F9DE8), // sky
        Color(0xFF9B87F5), // lavender
        Color(0xFFE3B25A), // gold
        Color(0xFFE6798D), // rose
    )
}

fun Color.argb(): Long = (value shr 32).toLong() and 0xFFFFFFFFL

fun adamColors(dark: Boolean, accent: Color = Palette.Emerald): AdamColors {
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF0B1410) else Color.White
    return if (dark) {
        val surface = Color(0xFF111714)
        AdamColors(
            isDark = true,
            bg = Color(0xFF080C0A),
            surface = surface,
            surfaceAlt = Color(0xFF18201C),
            surfaceHigh = Color(0xFF1D2622),
            border = Color(0xFF1F2924),
            borderStrong = Color(0xFF2B3731),
            text = Color(0xFFEFF4F1),
            textSecondary = Color(0xFF9DABA4),
            textMuted = Color(0xFF64716B),
            accent = accent,
            accentInk = accent,
            accentSoft = accent.copy(alpha = 0.14f).compositeOver(surface),
            onAccent = onAccent,
            expense = Color(0xFFEE8A80),
            expenseSoft = Color(0xFFEE8A80).copy(alpha = 0.13f).compositeOver(surface),
            income = Color(0xFF6FDCA6),
            incomeSoft = Color(0xFF6FDCA6).copy(alpha = 0.13f).compositeOver(surface),
            neutral = Color(0xFF9DA7B5),
            neutralSoft = Color(0xFF9DA7B5).copy(alpha = 0.12f).compositeOver(surface),
            warn = Color(0xFFE9B44C),
            warnSoft = Color(0xFFE9B44C).copy(alpha = 0.14f).compositeOver(surface),
            scrim = Color(0xB3000000),
            glow = accent.copy(alpha = 0.08f),
        )
    } else {
        val surface = Color(0xFFFFFFFF)
        AdamColors(
            isDark = false,
            bg = Color(0xFFF3F6F4),
            surface = surface,
            surfaceAlt = Color(0xFFE9EFEB),
            surfaceHigh = Color(0xFFFFFFFF),
            border = Color(0xFFE0E7E3),
            borderStrong = Color(0xFFCFD9D3),
            text = Color(0xFF0E1A14),
            textSecondary = Color(0xFF5B6B63),
            textMuted = Color(0xFF94A39B),
            accent = accent,
            accentInk = lerp(accent, Color(0xFF06251A), 0.38f),
            accentSoft = lerp(accent, Color.White, 0.84f),
            onAccent = onAccent,
            expense = Color(0xFFC8574C),
            expenseSoft = Color(0xFFFBEAE7),
            income = Color(0xFF1E8A57),
            incomeSoft = Color(0xFFE3F3EA),
            neutral = Color(0xFF5F6B7A),
            neutralSoft = Color(0xFFECEFF3),
            warn = Color(0xFFA86A06),
            warnSoft = Color(0xFFFBF1DC),
            scrim = Color(0x800E1A14),
            glow = accent.copy(alpha = 0.10f),
        )
    }
}
