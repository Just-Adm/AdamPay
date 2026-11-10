package com.adampay.app.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adampay.app.R
import com.adampay.app.core.locale.LocalAppLanguage
import com.adampay.app.data.db.CategoryEntity
import com.adampay.app.data.db.CategoryType
import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.db.TxnType
import com.adampay.app.data.wallet.WalletSnapshot
import com.adampay.app.data.wallet.name
import com.adampay.app.domain.money.AmountInput
import com.adampay.app.domain.wallet.Ledger
import com.adampay.app.ui.components.AppIcons
import com.adampay.app.ui.components.HIDDEN
import com.adampay.app.ui.components.MoneyKeypad
import com.adampay.app.ui.components.PrimaryButton
import com.adampay.app.ui.components.RoundIconButton
import com.adampay.app.ui.components.Segmented
import com.adampay.app.ui.components.dayText
import com.adampay.app.ui.components.moneyText
import com.adampay.app.ui.components.rememberHaptics
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.components.tintPair
import com.adampay.app.ui.components.typeLabel
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes
import com.adampay.app.ui.theme.LocalMoneyFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** What the add sheet opens for: a new expense / income, or an existing row to edit. */
data class AddRequest(
    val editing: TxnEntity? = null,
    val type: TxnType = TxnType.EXPENSE,
    val pocketId: Long? = null,
    /** Transfers only: the receiving pocket. */
    val toPocketId: Long? = null,
)

private val editableTypes = setOf(TxnType.EXPENSE, TxnType.INCOME, TxnType.TRANSFER)

/**
 * Logging in two taps: amount on the keypad, then a category, then Save. The pocket defaults to the
 * default pocket; note and date are optional. The same sheet edits an existing row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheetContent(
    request: AddRequest,
    snapshot: WalletSnapshot,
    defaultPocketId: Long?,
    now: Long,
    onSave: (TxnEntity) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val c = Adam.colors
    val language = LocalAppLanguage.current
    val haptics = rememberHaptics()
    val editing = request.editing
    val initialType = editing?.type ?: request.type

    var type by rememberSaveable { mutableStateOf(initialType) }
    var input by remember { mutableStateOf(AmountInput.of(editing?.amountFils ?: 0)) }
    var categoryId by rememberSaveable { mutableStateOf(editing?.categoryId) }
    var pocketId by rememberSaveable {
        mutableStateOf(editing?.let { it.fromPocketId ?: it.toPocketId } ?: request.pocketId ?: snapshot.defaultPocket(defaultPocketId)?.id)
    }
    // Transfers: [pocketId] is "from", this is "to" (defaults to the next pocket).
    var toPocketId by rememberSaveable {
        mutableStateOf(editing?.toPocketId?.takeIf { editing.type == TxnType.TRANSFER } ?: request.toPocketId
            ?: snapshot.activePockets.firstOrNull { it.id != pocketId }?.id)
    }
    var note by rememberSaveable { mutableStateOf(editing?.note ?: "") }
    var noteOpen by rememberSaveable { mutableStateOf(note.isNotBlank()) }
    var timestamp by rememberSaveable { mutableLongStateOf(editing?.timestamp ?: now) }
    var pickDate by remember { mutableStateOf(false) }

    val categoryType = if (type == TxnType.INCOME || type == TxnType.FOUND) CategoryType.INCOME else CategoryType.EXPENSE
    val categories = snapshot.categories(categoryType).let { list ->
        // An archived category stays visible while editing a row that uses it.
        val current = snapshot.category(categoryId)
        if (current != null && current.archived && current.type == categoryType) list + current else list
    }
    val amountColor by animateColorAsState(
        when {
            input.isEmpty -> c.textMuted
            type == TxnType.TRANSFER -> c.neutral
            type == TxnType.INCOME || type == TxnType.FOUND || type == TxnType.OPENING -> c.income
            else -> c.expense
        },
        tween(200), label = "amountColor",
    )

    fun buildRow(): TxnEntity {
        val cat = categoryId?.takeIf { id -> categories.any { it.id == id } }
            ?: categories.firstOrNull { it.seedKey == "other" || it.seedKey == "other_in" }?.id
        val base = editing ?: TxnEntity(type = type, amountFils = 0, timestamp = timestamp)
        val incoming = type == TxnType.INCOME || type == TxnType.FOUND || type == TxnType.OPENING
        val transfer = type == TxnType.TRANSFER
        return base.copy(
            type = type,
            amountFils = input.fils,
            categoryId = if (type == TxnType.OPENING || transfer) null else cat,
            fromPocketId = if (incoming) null else pocketId,
            toPocketId = if (transfer) toPocketId else if (incoming) pocketId else null,
            note = note.trim(),
            timestamp = timestamp,
        )
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        // Type + delete
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (type in editableTypes) {
                Segmented(
                    options = listOf(TxnType.EXPENSE, TxnType.INCOME, TxnType.TRANSFER),
                    selected = type,
                    label = { typeLabel(it) },
                    onSelect = { if (it != type) { type = it; categoryId = null } },
                    modifier = Modifier.weight(1f),
                    selectedColor = {
                        when (it) {
                            TxnType.EXPENSE -> c.expense
                            TxnType.INCOME -> c.income
                            else -> c.neutral
                        }
                    },
                )
            } else {
                Text(typeLabel(type), style = Adam.type.title, color = c.text, modifier = Modifier.weight(1f))
            }
            if (editing != null) {
                Spacer(Modifier.width(10.dp))
                RoundIconButton(
                    R.drawable.ic_delete, stringResource(R.string.action_delete),
                    onClick = { haptics.reject(); onDelete(editing.id) },
                    tint = c.expense, background = c.expenseSoft, size = 44.dp,
                )
            }
        }

        // Amount
        Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.Bottom) {
                    val sign = when {
                        input.isEmpty || type == TxnType.TRANSFER -> ""
                        type == TxnType.EXPENSE || type == TxnType.LOST -> "−"
                        else -> "+"
                    }
                    Text(sign + input.text(), style = Adam.type.hero.copy(fontSize = 44.sp), color = amountColor, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Text(LocalMoneyFormat.current.label.text, style = Adam.type.title, color = c.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
        }

        // Categories (two rows, scrolls sideways when there are more than eight)
        if (type == TxnType.EXPENSE || type == TxnType.INCOME || type == TxnType.LOST || type == TxnType.FOUND) {
            CategoryGrid(categories, categoryId) { id ->
                haptics.tick()
                categoryId = if (categoryId == id) null else id
            }
            Spacer(Modifier.height(10.dp))
        }

        // Pocket · date · note
        if (type == TxnType.TRANSFER) {
            // From / To on their own rows so long pocket names fit.
            listOf(true, false).forEach { from ->
                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (from) R.string.transfer_from else R.string.transfer_to),
                        style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.width(44.dp),
                    )
                    PocketChip(snapshot, if (from) pocketId else toPocketId, Modifier, wide = true) { id ->
                        if (from) {
                            if (id == toPocketId) toPocketId = pocketId
                            pocketId = id
                        } else {
                            if (id == pocketId) pocketId = toPocketId
                            toPocketId = id
                        }
                    }
                }
            }
            val fromBalance = pocketId?.let { snapshot.balance(it) + (if (editing?.type == TxnType.TRANSFER && editing.fromPocketId == it) editing.amountFils else 0) } ?: 0
            if (input.fils > fromBalance) {
                Text(
                    stringResource(R.string.transfer_more_than_balance, moneyText(fromBalance)),
                    style = Adam.type.small, color = c.warn, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (type != TxnType.TRANSFER) PocketChip(snapshot, pocketId, Modifier.weight(1f, fill = false)) { pocketId = it }
            DetailChip(
                R.drawable.ic_calendar_month,
                dayText(Ledger.date(timestamp, zone), Ledger.date(now, zone)),
            ) { pickDate = true }
            DetailChip(R.drawable.ic_notes, if (note.isBlank()) stringResource(R.string.add_note) else note, active = note.isNotBlank()) { noteOpen = !noteOpen }
        }
        AnimatedVisibility(noteOpen) {
            BasicTextField(
                value = note,
                onValueChange = { note = it.take(80) },
                singleLine = true,
                textStyle = Adam.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().clip(AdamShapes.tile).background(c.surfaceAlt).padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (note.isEmpty()) Text(stringResource(R.string.add_note_hint), style = Adam.type.body, color = c.textMuted)
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.height(12.dp))

        MoneyKeypad(input, { input = it }, keyHeight = 52.dp)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = if (input.fils > 0) stringResource(R.string.add_save_amount, moneyText(input.fils).let { if (it.contains(HIDDEN)) "" else it }).trim() else stringResource(R.string.action_save),
            onClick = { haptics.confirm(); onSave(buildRow()) },
            enabled = input.fils > 0 && pocketId != null && (type != TxnType.TRANSFER || (toPocketId != null && toPocketId != pocketId)),
            modifier = Modifier.fillMaxWidth(),
            icon = R.drawable.ic_check,
        )
        Spacer(Modifier.height(14.dp))
    }

    if (pickDate) {
        val currentDay = Ledger.date(timestamp, zone)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = currentDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= now + 86_400_000L
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        val day = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = if (day == LocalDate.now(zone) && editing == null) LocalTime.now(zone)
                        else Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime()
                        timestamp = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    }
                    pickDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}

/** Categories in two rows read across; scrolls sideways when there are more than eight. */
@Composable
fun CategoryGrid(categories: List<CategoryEntity>, selectedId: Long?, onSelect: (Long) -> Unit) {
    // Column j holds items j and j + half, so row 1 is the first half.
    val half = (categories.size + 1) / 2
    val columns = (0 until half).map { j -> listOfNotNull(categories.getOrNull(j), categories.getOrNull(j + half)) }
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(columns, key = { col -> col.first().id }) { col ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                col.forEach { cat -> CategoryCell(cat, selected = cat.id == selectedId) { onSelect(cat.id) } }
            }
        }
    }
}

@Composable
private fun CategoryCell(cat: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    val language = LocalAppLanguage.current
    val c = Adam.colors
    val (tint, bg) = tintPair(cat.color)
    val ring by animateColorAsState(if (selected) tint else androidx.compose.ui.graphics.Color.Transparent, tween(160), label = "ring")
    Column(
        Modifier.width(80.dp).clip(AdamShapes.tile).tap(pressedScale = 0.92f, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(if (selected) tint else bg).border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (selected) R.drawable.ic_check else AppIcons.of(cat.icon)), null,
                tint = if (selected) c.bg else tint, modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            cat.name.forTerms(language), style = Adam.type.micro, color = if (selected) c.text else c.textSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PocketChip(snapshot: WalletSnapshot, pocketId: Long?, modifier: Modifier, wide: Boolean = false, onPick: (Long) -> Unit) {
    val language = LocalAppLanguage.current
    var open by remember { mutableStateOf(false) }
    val pocket = snapshot.pocket(pocketId)
    Box(modifier) {
        DetailChip(
            AppIcons.of(pocket?.icon), pocket?.name?.forTerms(language) ?: stringResource(R.string.add_pick_pocket),
            dotColor = pocket?.color,
            maxWidth = if (wide) 260.dp else 120.dp,
        ) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Adam.colors.surfaceHigh) {
            snapshot.activePockets.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name.forTerms(language), style = Adam.type.bodyMedium, color = Adam.colors.text, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(12.dp))
                            Text(moneyText(snapshot.balance(p.id)), style = Adam.type.small, color = Adam.colors.textMuted)
                        }
                    },
                    leadingIcon = { Icon(painterResource(AppIcons.of(p.icon)), null, tint = androidx.compose.ui.graphics.Color(p.color), modifier = Modifier.size(20.dp)) },
                    onClick = { onPick(p.id); open = false },
                )
            }
        }
    }
}

@Composable
private fun DetailChip(icon: Int, text: String, active: Boolean = false, dotColor: Long? = null, maxWidth: androidx.compose.ui.unit.Dp = 120.dp, onClick: () -> Unit) {
    val c = Adam.colors
    Row(
        Modifier
            .heightIn(min = 38.dp)
            .clip(AdamShapes.pill)
            .background(if (active) c.accentSoft else c.surfaceAlt)
            .tap(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon), null,
            tint = dotColor?.let { androidx.compose.ui.graphics.Color(it) } ?: if (active) c.accentInk else c.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = Adam.type.smallMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = maxWidth))
    }
}
