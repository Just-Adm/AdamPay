package com.adampay.app.ui.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adampay.app.R
import com.adampay.app.core.locale.LocalAppLanguage
import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.wallet.name
import com.adampay.app.domain.wallet.HistoryFilter
import com.adampay.app.domain.wallet.Ledger
import com.adampay.app.ui.WalletUi
import com.adampay.app.ui.components.AppIcons
import com.adampay.app.ui.components.Chip
import com.adampay.app.ui.components.EmptyState
import com.adampay.app.ui.components.TxnItem
import com.adampay.app.ui.components.bidiJoinText
import com.adampay.app.ui.components.dayText
import com.adampay.app.ui.components.moneyText
import com.adampay.app.ui.components.rememberHaptics
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes
import java.time.ZoneId

/** Every entry, grouped by day, with type chips, category / pocket filters and note search. Swipe to delete. */
@Composable
fun HistoryScreen(
    ui: WalletUi,
    filter: HistoryFilter,
    onFilterChange: (HistoryFilter) -> Unit,
    onTxn: (TxnEntity) -> Unit,
    contentPadding: PaddingValues,
    onDelete: (Long) -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val c = Adam.colors
    val snapshot = ui.snapshot
    val names = remember(snapshot.categories) { snapshot.categories.associate { it.id to listOf(it.nameAr, it.nameEn) } }
    val filtered = remember(ui.txns, filter, names) { filter.apply(ui.txns, names) }
    val groups = remember(filtered) { Ledger.byDay(filtered, zone) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "head") {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 18.dp, end = 18.dp, top = 14.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.history_title), style = Adam.type.h1, color = c.text, modifier = Modifier.weight(1f))
                    Text(pluralStringResource(R.plurals.history_count, filtered.size, filtered.size), style = Adam.type.small, color = c.textMuted)
                }
                Spacer(Modifier.height(14.dp))
                SearchField(filter.query) { onFilterChange(filter.copy(query = it)) }
                Spacer(Modifier.height(12.dp))
            }
        }
        item(key = "chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(HistoryFilter.Kind.entries) { k ->
                    Chip(kindLabel(k), selected = filter.kind == k) { onFilterChange(filter.copy(kind = k)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryFilter(ui, filter, onFilterChange)
                PocketFilter(ui, filter, onFilterChange)
            }
        }

        if (groups.isEmpty()) {
            item(key = "empty") {
                if (ui.txns.isEmpty()) {
                    EmptyState(R.drawable.ic_receipt_long, stringResource(R.string.history_first_title), stringResource(R.string.history_first_body), Modifier.padding(top = 40.dp))
                } else {
                    EmptyState(R.drawable.ic_search, stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body), Modifier.padding(top = 40.dp))
                }
            }
        }

        groups.forEach { g ->
            item(key = "d-${g.day}") {
                Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayText(g.day, ui.today), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
                    val parts = buildList {
                        if (g.out > 0) add(stringResource(R.string.history_day_out, moneyText(g.out)))
                        if (g.`in` > 0) add(stringResource(R.string.history_day_in, moneyText(g.`in`)))
                    }
                    if (parts.isNotEmpty()) Text(bidiJoinText(parts), style = Adam.type.micro, color = c.textMuted, maxLines = 1)
                }
            }
            items(g.rows, key = { it.id }) { t ->
                SwipeRow(onDelete = { onDelete(t.id) }) {
                    Box(Modifier.background(c.bg).padding(horizontal = 14.dp)) {
                        TxnItem(t, snapshot, onClick = { onTxn(t) })
                    }
                }
            }
        }
    }
}

@Composable
private fun kindLabel(k: HistoryFilter.Kind): String = stringResource(
    when (k) {
        HistoryFilter.Kind.ALL -> R.string.history_all
        HistoryFilter.Kind.EXPENSES -> R.string.history_expenses
        HistoryFilter.Kind.INCOME -> R.string.history_income
        HistoryFilter.Kind.TRANSFERS -> R.string.history_transfers
        HistoryFilter.Kind.LOST_FOUND -> R.string.history_lost
    },
)

@Composable
private fun SwipeRow(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val c = Adam.colors
    val haptics = rememberHaptics()
    val state = rememberSwipeToDismissBoxState()
    // Delete once when the swipe settles (confirmValueChange can fire several times per gesture).
    androidx.compose.runtime.LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) { haptics.reject(); onDelete() }
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val bg by animateColorAsState(
                if (state.targetValue == SwipeToDismissBoxValue.EndToStart) c.expenseSoft else Color.Transparent, tween(150), label = "swipeBg",
            )
            Box(Modifier.fillMaxSize().background(bg).padding(horizontal = 26.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete), tint = c.expense)
            }
        },
    ) { content() }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val c = Adam.colors
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = Adam.type.body.copy(color = c.text),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().clip(AdamShapes.pill).background(c.surface).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_search), null, tint = c.textMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(stringResource(R.string.history_search), style = Adam.type.body, color = c.textMuted)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        painterResource(R.drawable.ic_close), stringResource(R.string.history_clear), tint = c.textSecondary,
                        modifier = Modifier.size(20.dp).clip(AdamShapes.pill).tap(press = false) { onChange("") },
                    )
                }
            }
        },
    )
}

@Composable
private fun CategoryFilter(ui: WalletUi, filter: HistoryFilter, onChange: (HistoryFilter) -> Unit) {
    val language = LocalAppLanguage.current
    var open by remember { mutableStateOf(false) }
    val selected = ui.snapshot.category(filter.categoryId)
    Box {
        Chip(
            selected?.name?.forTerms(language) ?: stringResource(R.string.history_category),
            selected = selected != null,
            icon = selected?.let { AppIcons.of(it.icon) } ?: R.drawable.ic_category,
        ) { open = true }
        DropdownMenu(open, { open = false }, containerColor = Adam.colors.surfaceHigh) {
            DropdownMenuItem(text = { Text(stringResource(R.string.history_any)) }, onClick = { onChange(filter.copy(categoryId = null)); open = false })
            ui.snapshot.categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name.forTerms(language), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(painterResource(AppIcons.of(cat.icon)), null, tint = Color(cat.color), modifier = Modifier.size(20.dp)) },
                    onClick = { onChange(filter.copy(categoryId = cat.id)); open = false },
                )
            }
        }
    }
}

@Composable
private fun PocketFilter(ui: WalletUi, filter: HistoryFilter, onChange: (HistoryFilter) -> Unit) {
    val language = LocalAppLanguage.current
    var open by remember { mutableStateOf(false) }
    val selected = ui.snapshot.pocket(filter.pocketId)
    Box {
        Chip(
            selected?.name?.forTerms(language) ?: stringResource(R.string.history_pocket),
            selected = selected != null,
            icon = selected?.let { AppIcons.of(it.icon) } ?: R.drawable.ic_wallet,
        ) { open = true }
        DropdownMenu(open, { open = false }, containerColor = Adam.colors.surfaceHigh) {
            DropdownMenuItem(text = { Text(stringResource(R.string.history_any)) }, onClick = { onChange(filter.copy(pocketId = null)); open = false })
            ui.snapshot.pockets.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name.forTerms(language), maxLines = 1) },
                    leadingIcon = { Icon(painterResource(AppIcons.of(p.icon)), null, tint = Color(p.color), modifier = Modifier.size(20.dp)) },
                    onClick = { onChange(filter.copy(pocketId = p.id)); open = false },
                )
            }
        }
    }
}
