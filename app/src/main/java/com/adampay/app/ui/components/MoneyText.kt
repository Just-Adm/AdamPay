package com.adampay.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.adampay.app.core.locale.Bidi
import com.adampay.app.domain.money.Money
import com.adampay.app.domain.money.MoneyFormat
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.LocalHideAmounts
import com.adampay.app.ui.theme.LocalMoneyFormat
import kotlinx.coroutines.delay

const val HIDDEN = "••••"

/** Leading sign shown in front of an amount. Never colour alone: the sign says the direction. */
enum class Sign(val text: String) { NONE(""), PLUS("+"), MINUS("−") }

/** "12.500 JD" / "−2.350 د.أ" as plain text (for strings, notifications, speech-free labels). */
fun MoneyFormat.text(fils: Long, sign: Sign = Sign.NONE, hidden: Boolean = false): String {
    val value = if (sign == Sign.NONE) fils else kotlin.math.abs(fils)
    val n = if (hidden) HIDDEN else sign.text + Money.formatNumber(value, decimals)
    return Bidi.ltr(n) + " " + label.text
}

@Composable
fun moneyText(fils: Long, sign: Sign = Sign.NONE): String =
    LocalMoneyFormat.current.text(fils, sign, LocalHideAmounts.current)

/**
 * An amount with its currency label. Honours "Hide amounts" (tap to peek for 4 s).
 * [roll] animates each digit that changes, sliding up when the value grows and down when it shrinks.
 */
@Composable
fun Amount(
    fils: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = Adam.type.amount,
    color: Color = Adam.colors.text,
    sign: Sign = Sign.NONE,
    label: Boolean = true,
    labelStyle: TextStyle = Adam.type.micro,
    labelColor: Color = Adam.colors.textSecondary,
    labelGap: Dp = 4.dp,
    roll: Boolean = false,
    peekable: Boolean = true,
) {
    val format = LocalMoneyFormat.current
    val hideAll = LocalHideAmounts.current
    var peek by remember { mutableStateOf(false) }
    LaunchedEffect(peek) { if (peek) { delay(4000); peek = false } }
    val hidden = hideAll && !peek
    val value = if (sign == Sign.NONE) fils else kotlin.math.abs(fils)
    val number = sign.text + Money.formatNumber(value, format.decimals)
    val spoken = format.text(fils, sign)
    Row(
        modifier
            .then(if (hideAll && peekable) Modifier.tap(press = false, role = null) { peek = !peek } else Modifier)
            .semantics { contentDescription = if (hidden) HIDDEN else Bidi.strip(spoken) },
    ) {
        // Digits are always laid out left-to-right, even inside Arabic text.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.alignByBaseline()) {
                when {
                    hidden -> Text(HIDDEN, style = style, color = color)
                    roll && !LocalInspectionMode.current -> RollingNumber(number, style, color)
                    else -> Text(number, style = style, color = color, maxLines = 1, softWrap = false)
                }
            }
        }
        if (label) {
            Spacer(Modifier.width(labelGap))
            Text(format.label.text, style = labelStyle, color = labelColor, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
    }
}

/** Each character animates on its own, keyed by its position from the right so digits stay aligned. */
@Composable
private fun RollingNumber(number: String, style: TextStyle, color: Color) {
    var previous by rememberSaveable { mutableStateOf(number) }
    val up = compare(number, previous) >= 0
    LaunchedEffect(number) { previous = number }
    Row {
        val chars = number.toList()
        chars.forEachIndexed { index, ch ->
            val fromRight = chars.size - index
            androidx.compose.runtime.key(fromRight) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val dir = if (up) 1 else -1
                        (slideInVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { it * dir } + fadeIn(tween(160)))
                            .togetherWith(slideOutVertically(tween(220)) { -it * dir } + fadeOut(tween(120)))
                    },
                    label = "digit",
                ) { c -> Text(c.toString(), style = style, color = color) }
            }
        }
    }
}

private fun compare(a: String, b: String): Int {
    fun v(s: String) = s.filter { it.isDigit() }.toBigIntegerOrNull() ?: java.math.BigInteger.ZERO
    val sa = if (a.startsWith("−")) v(a).negate() else v(a)
    val sb = if (b.startsWith("−")) v(b).negate() else v(b)
    return sa.compareTo(sb)
}
