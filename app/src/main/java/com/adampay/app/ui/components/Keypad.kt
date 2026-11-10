package com.adampay.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adampay.app.R
import com.adampay.app.domain.money.AmountInput
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes

/**
 * The money keypad: 1–9, dot, 0, backspace (long-press clears). Always laid out like a phone
 * keypad (left-to-right), whatever the language.
 */
@Composable
fun MoneyKeypad(
    value: AmountInput,
    onChange: (AmountInput) -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 54.dp,
) {
    val haptics = rememberHaptics()
    fun press(next: AmountInput) {
        if (next == value) haptics.reject() else haptics.tick()
        onChange(next)
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { d -> Key(Modifier.weight(1f), keyHeight, onClick = { press(value.digit(d)) }) { Digit(d.toString()) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Key(Modifier.weight(1f), keyHeight, onClick = { press(value.dot()) }) { Digit(".") }
                Key(Modifier.weight(1f), keyHeight, onClick = { press(value.digit(0)) }) { Digit("0") }
                Key(
                    Modifier.weight(1f), keyHeight,
                    onLongClick = { haptics.reject(); onChange(AmountInput()) },
                    onClick = { press(value.backspace()) },
                ) {
                    Icon(
                        painterResource(R.drawable.ic_backspace), stringResource(R.string.key_backspace),
                        tint = Adam.colors.textSecondary, modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Digit(text: String) {
    Text(text, style = Adam.type.amountLarge.copy(fontSize = 25.sp), color = Adam.colors.text)
}

@Composable
private fun Key(
    modifier: Modifier,
    height: Dp,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .height(height)
            .clip(AdamShapes.tile)
            .background(Adam.colors.surfaceAlt.copy(alpha = if (Adam.colors.isDark) 0.7f else 0.9f))
            .tap(pressedScale = 0.92f, onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
