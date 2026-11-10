package com.adampay.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adampay.app.R
import com.adampay.app.domain.cash.Denominations
import com.adampay.app.domain.money.AmountInput
import com.adampay.app.ui.cash.denominationLabel
import com.adampay.app.ui.components.AdamCard
import com.adampay.app.ui.components.Amount
import com.adampay.app.ui.components.GhostButton
import com.adampay.app.ui.components.MoneyKeypad
import com.adampay.app.ui.components.PrimaryButton
import com.adampay.app.ui.components.SubScreen
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes

/** Settings → Notes and coins: the denominations the cash counter shows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DenominationsScreen(current: List<Long>, onChange: (List<Long>?) -> Unit, onBack: () -> Unit) {
    val c = Adam.colors
    var input by remember { mutableStateOf(AmountInput()) }
    SubScreen(stringResource(R.string.denoms_title), onBack) {
        Text(stringResource(R.string.denoms_hint), style = Adam.type.small, color = c.textMuted, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            current.forEach { value ->
                val note = Denominations.isNote(value)
                Row(
                    Modifier.clip(AdamShapes.pill).background(c.surface)
                        .border(1.dp, c.border, AdamShapes.pill).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(width = if (note) 22.dp else 14.dp, height = 14.dp)
                            .clip(if (note) RoundedCornerShape(3.dp) else RoundedCornerShape(50))
                            .background(if (note) c.accentSoft else c.surfaceAlt),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(denominationLabel(value), style = Adam.type.smallMedium, color = c.text)
                    Spacer(Modifier.width(4.dp))
                    if (current.size > 1) {
                        Box(
                            Modifier.size(28.dp).clip(AdamShapes.pill).tap(press = false) { onChange(current - value) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_delete), tint = c.textMuted, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
            Text(stringResource(R.string.denoms_add), style = Adam.type.title, color = c.text)
            Spacer(Modifier.height(8.dp))
            Amount(input.fils, style = Adam.type.amountLarge, color = if (input.isEmpty) c.textMuted else c.text, peekable = false)
            Spacer(Modifier.height(10.dp))
            MoneyKeypad(input, { input = it }, keyHeight = 44.dp)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                stringResource(R.string.action_add),
                { onChange(current + input.fils); input = AmountInput() },
                Modifier.fillMaxWidth(),
                enabled = input.fils > 0 && input.fils !in current,
            )
        }
        Spacer(Modifier.height(12.dp))
        GhostButton(stringResource(R.string.denoms_reset), { onChange(null) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_restart_alt)
    }
}
