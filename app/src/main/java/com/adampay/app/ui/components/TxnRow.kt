package com.adampay.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adampay.app.R
import com.adampay.app.core.locale.AppLocales
import com.adampay.app.core.locale.Bidi
import com.adampay.app.core.locale.LocalAppLanguage
import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.db.TxnType
import com.adampay.app.data.wallet.WalletSnapshot
import com.adampay.app.data.wallet.name
import com.adampay.app.domain.wallet.MoneyFlow
import com.adampay.app.domain.wallet.flow
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Sign and colour of a row's amount. */
@Composable
fun flowStyle(type: TxnType): Pair<Sign, Color> = when (type.flow) {
    MoneyFlow.OUT -> Sign.MINUS to Adam.colors.expense
    MoneyFlow.IN -> Sign.PLUS to Adam.colors.income
    MoneyFlow.NEUTRAL -> Sign.NONE to Adam.colors.neutral
}

@Composable
@ReadOnlyComposable
fun typeLabel(type: TxnType): String = stringResource(
    when (type) {
        TxnType.EXPENSE -> R.string.type_expense
        TxnType.INCOME -> R.string.type_income
        TxnType.TRANSFER -> R.string.type_transfer
        TxnType.LOST -> R.string.type_lost
        TxnType.FOUND -> R.string.type_found
        TxnType.OPENING -> R.string.type_opening
    },
)

/** Icon key + colour for a row: its category, or a fixed look for system types. */
@Composable
fun txnLook(txn: TxnEntity, snapshot: WalletSnapshot): Pair<Int, Long> {
    val cat = snapshot.category(txn.categoryId)
    return when {
        txn.type == TxnType.TRANSFER -> R.drawable.ic_swap_horiz to 0xFF9DA7B5
        txn.type == TxnType.OPENING -> R.drawable.ic_wallet to 0xFF2FB36D
        txn.type == TxnType.LOST && cat == null -> R.drawable.ic_search to 0xFFEE8A80
        txn.type == TxnType.FOUND && cat == null -> R.drawable.ic_star to 0xFF79CF9C
        cat != null -> AppIcons.of(cat.icon) to cat.color
        else -> R.drawable.ic_category to 0xFF9AA0A6
    }
}

/** "2:05 PM" / "2:05 م" with Latin digits. */
@Composable
@ReadOnlyComposable
fun timeText(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val locale = AppLocales.ui(LocalAppLanguage.current)
    return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        .format(Instant.ofEpochMilli(millis).atZone(zone))
}

/** "Today", "Yesterday" or "Thursday, 15 Oct". */
@Composable
@ReadOnlyComposable
fun dayText(day: LocalDate, today: LocalDate): String {
    val locale = AppLocales.ui(LocalAppLanguage.current)
    return when (day) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> {
            val pattern = if (day.year == today.year) stringResource(R.string.day_pattern) else stringResource(R.string.day_pattern_year)
            DateTimeFormatter.ofPattern(pattern, locale).format(day)
        }
    }
}

/** One ledger row: icon, title (note or category), category · pocket · time, signed amount. */
@Composable
fun TxnItem(
    txn: TxnEntity,
    snapshot: WalletSnapshot,
    modifier: Modifier = Modifier,
    showTime: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val language = LocalAppLanguage.current
    val cat = snapshot.category(txn.categoryId)
    val (icon, color) = txnLook(txn, snapshot)
    val (tint, bg) = tintPair(color)
    val (sign, amountColor) = flowStyle(txn.type)
    val catName = cat?.name?.forTerms(language)
    val title = txn.note.ifBlank { catName ?: typeLabel(txn.type) }
    val pocketText = when (txn.type) {
        TxnType.TRANSFER -> listOfNotNull(snapshot.pocket(txn.fromPocketId), snapshot.pocket(txn.toPocketId))
            .joinToString(" → ") { Bidi.isolate(it.name.forTerms(language)) }
        else -> snapshot.pocket(txn.fromPocketId ?: txn.toPocketId)?.name?.forTerms(language)
    }
    val parts = buildList {
        if (txn.note.isNotBlank() && catName != null) add(catName)
        pocketText?.let { add(it) }
        if (showTime) add(timeText(txn.timestamp))
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(AdamShapes.tile)
            .then(if (onClick != null) Modifier.tap(press = false, onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, tint = tint, background = bg, size = 42.dp, iconSize = 20.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = Adam.type.bodyMedium, color = Adam.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (parts.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    bidiJoinText(parts), style = Adam.type.small, color = Adam.colors.textMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Amount(txn.amountFils, sign = sign, color = amountColor, labelColor = amountColor.copy(alpha = 0.7f))
    }
}

@Composable
@ReadOnlyComposable
fun bidiJoinText(parts: List<String>): String = com.adampay.app.core.locale.bidiJoin(parts)
