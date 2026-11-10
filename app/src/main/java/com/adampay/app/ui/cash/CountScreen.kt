package com.adampay.app.ui.cash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adampay.app.R
import com.adampay.app.core.locale.LocalAppLanguage
import com.adampay.app.data.db.CategoryType
import com.adampay.app.data.wallet.name
import com.adampay.app.domain.cash.CountDifference
import com.adampay.app.domain.cash.CountSheet
import com.adampay.app.domain.cash.Denominations
import com.adampay.app.domain.cash.Explanation
import com.adampay.app.domain.cash.Resolution
import com.adampay.app.domain.money.AmountInput
import com.adampay.app.domain.money.Money
import com.adampay.app.ui.WalletUi
import com.adampay.app.ui.add.CategoryGrid
import com.adampay.app.ui.components.AdamCard
import com.adampay.app.ui.components.AdamDivider
import com.adampay.app.ui.components.AdamTextField
import com.adampay.app.ui.components.Amount
import com.adampay.app.ui.components.AppIcons
import com.adampay.app.ui.components.Chip
import com.adampay.app.ui.components.GhostButton
import com.adampay.app.ui.components.MoneyKeypad
import com.adampay.app.ui.components.PrimaryButton
import com.adampay.app.ui.components.RoundIconButton
import com.adampay.app.ui.components.Segmented
import com.adampay.app.ui.components.Sign
import com.adampay.app.ui.components.rememberHaptics
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.components.tintPair
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes

private enum class Mode { COUNT, TYPE }

/** "Count my cash": count notes and coins (or type the amount), compare, explain the difference, save. */
@Composable
fun CountScreen(
    ui: WalletUi,
    denominations: List<Long>,
    initialPocketId: Long?,
    onSave: (pocketId: Long, expected: Long, counted: Long, sheet: CountSheet?, resolution: Resolution) -> Unit,
    onBack: () -> Unit,
    initialSheet: CountSheet = CountSheet(),
    initialResult: Boolean = false,
    initialResolution: Resolution? = null,
) {
    val c = Adam.colors
    val snapshot = ui.snapshot
    var pocketId by rememberSaveable { mutableStateOf(initialPocketId ?: snapshot.activePockets.firstOrNull()?.id) }
    var mode by rememberSaveable { mutableStateOf(Mode.COUNT) }
    var sheet by remember { mutableStateOf(initialSheet) }
    var typed by remember { mutableStateOf(AmountInput()) }
    var showResult by rememberSaveable { mutableStateOf(initialResult) }
    var resolution by remember { mutableStateOf(initialResolution) }
    var confirmLeave by remember { mutableStateOf(false) }

    val expected = pocketId?.let { snapshot.balance(it) } ?: 0
    val counted = if (mode == Mode.COUNT) sheet.total else typed.fils
    val touched = sheet.pieces > 0 || !typed.isEmpty
    val leave = { if (touched || showResult) confirmLeave = true else onBack() }
    BackHandler { if (showResult) showResult = false else leave() }

    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), { if (showResult) showResult = false else leave() }, background = Color.Transparent)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.count_title), style = Adam.type.h2, color = c.text, modifier = Modifier.weight(1f))
        }
        AnimatedContent(
            targetState = showResult,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.weight(1f),
            label = "count",
        ) { result ->
            val pocket = pocketId
            if (!result || pocket == null) {
                EnterStage(
                    ui = ui, pocketId = pocketId, onPocket = { pocketId = it },
                    expected = expected, mode = mode, onMode = { mode = it },
                    denominations = denominations, sheet = sheet, onSheet = { sheet = it },
                    typed = typed, onTyped = { typed = it }, counted = counted,
                    onDone = {
                        resolution = Resolution(CountDifference.of(expected, counted))
                        showResult = true
                    },
                )
            } else {
                val r = resolution ?: Resolution(CountDifference.of(expected, counted))
                ResultStage(
                    ui = ui, expected = expected, counted = counted, resolution = r,
                    onResolution = { resolution = it },
                    onSave = { onSave(pocket, expected, counted, if (mode == Mode.COUNT) sheet else null, r) },
                )
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = c.surfaceHigh,
            title = { Text(stringResource(R.string.count_leave_title), style = Adam.type.title, color = c.text) },
            text = { Text(stringResource(R.string.count_leave_body), style = Adam.type.body, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(R.string.count_leave_confirm), color = c.expense) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }
}

@Composable
private fun EnterStage(
    ui: WalletUi,
    pocketId: Long?,
    onPocket: (Long) -> Unit,
    expected: Long,
    mode: Mode,
    onMode: (Mode) -> Unit,
    denominations: List<Long>,
    sheet: CountSheet,
    onSheet: (CountSheet) -> Unit,
    typed: AmountInput,
    onTyped: (AmountInput) -> Unit,
    counted: Long,
    onDone: () -> Unit,
) {
    val c = Adam.colors
    val language = LocalAppLanguage.current
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ui.snapshot.activePockets, key = { it.id }) { p ->
                Chip(p.name.forTerms(language), selected = p.id == pocketId, icon = AppIcons.of(p.icon), iconTint = Color(p.color)) { onPocket(p.id) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.count_expected), style = Adam.type.small, color = c.textSecondary, modifier = Modifier.weight(1f))
            Amount(expected, color = c.textSecondary)
        }
        Segmented(
            listOf(Mode.COUNT, Mode.TYPE), mode,
            label = { stringResource(if (it == Mode.COUNT) R.string.count_mode_count else R.string.count_mode_type) },
            onSelect = onMode,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (mode == Mode.COUNT) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                items(denominations, key = { it }) { value ->
                    DenominationRow(value, sheet.count(value),
                        onMinus = { haptics.tick(); onSheet(sheet.minus(value)) },
                        onPlus = { haptics.tick(); onSheet(sheet.plus(value)) },
                    )
                }
            }
        } else {
            Column(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.Center) {
                Amount(
                    typed.fils, Modifier.align(Alignment.CenterHorizontally), style = Adam.type.hero.copy(fontSize = 44.sp),
                    color = if (typed.isEmpty) c.textMuted else c.text, labelStyle = Adam.type.title, peekable = false,
                )
                Spacer(Modifier.height(18.dp))
                MoneyKeypad(typed, onTyped, keyHeight = 52.dp)
            }
        }
        // Live total + continue
        Column(Modifier.fillMaxWidth().background(c.surface).windowInsetsPadding(WindowInsets.navigationBars)) {
            AdamDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.count_counted), style = Adam.type.small, color = c.textSecondary)
                    Amount(counted, style = Adam.type.amountLarge.copy(fontSize = 26.sp), labelColor = c.accentInk, peekable = false, roll = true)
                }
                PrimaryButton(stringResource(R.string.count_compare), { haptics.confirm(); onDone() }, enabled = pocketId != null)
            }
        }
    }
}

/** Denomination label in the user's language: "50 JD" for notes, "½ JD", "10 qirsh"… for coins. */
@Composable
fun denominationLabel(fils: Long): String = when {
    fils >= Money.FILS_PER_DINAR && fils % Money.FILS_PER_DINAR == 0L -> stringResource(R.string.denom_dinars, (fils / Money.FILS_PER_DINAR).toInt())
    fils == 500L -> stringResource(R.string.denom_half)
    fils == 250L -> stringResource(R.string.denom_quarter)
    fils % Money.FILS_PER_QIRSH == 0L -> {
        val q = (fils / Money.FILS_PER_QIRSH).toInt()
        if (q == 1) stringResource(R.string.denom_qirsh_one) else stringResource(R.string.denom_qirsh, q)
    }
    else -> stringResource(R.string.denom_fils, fils.toInt())
}

@Composable
private fun DenominationRow(value: Long, count: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = Adam.colors
    val note = Denominations.isNote(value)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(AdamShapes.tile).background(c.surface).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A note is a small rounded rectangle, a coin a circle: shape tells them apart, not only colour.
        Box(
            Modifier.size(width = if (note) 44.dp else 34.dp, height = if (note) 26.dp else 34.dp)
                .clip(if (note) RoundedCornerShape(6.dp) else CircleShape)
                .background(if (note) c.accentSoft else c.surfaceAlt)
                .border(1.dp, if (note) c.accent.copy(alpha = 0.5f) else c.borderStrong, if (note) RoundedCornerShape(6.dp) else CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(denominationLabel(value), style = Adam.type.bodyMedium, color = c.text)
            if (count > 0) Amount(value * count, style = Adam.type.small, color = c.textMuted, labelStyle = Adam.type.micro, peekable = false)
        }
        RoundIconButton(R.drawable.ic_remove, stringResource(R.string.count_less), onMinus, size = 38.dp, tint = if (count > 0) c.text else c.textMuted)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                count.toString(), style = Adam.type.amount.copy(fontSize = 18.sp), color = if (count > 0) c.text else c.textMuted,
                textAlign = TextAlign.Center, modifier = Modifier.width(44.dp),
            )
        }
        RoundIconButton(R.drawable.ic_add, stringResource(R.string.count_more), onPlus, size = 38.dp, tint = c.onAccent, background = c.accent)
    }
}

@Composable
private fun ResultStage(
    ui: WalletUi,
    expected: Long,
    counted: Long,
    resolution: Resolution,
    onResolution: (Resolution) -> Unit,
    onSave: () -> Unit,
) {
    val c = Adam.colors
    val haptics = rememberHaptics()
    val diff = resolution.difference
    var explaining by remember(diff) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(10.dp))
            val (icon, tint, bg) = when (diff) {
                CountDifference.Match -> Triple(R.drawable.ic_check_circle_fill, c.income, c.incomeSoft)
                is CountDifference.Missing -> Triple(R.drawable.ic_trending_down, c.expense, c.expenseSoft)
                is CountDifference.Extra -> Triple(R.drawable.ic_trending_up, c.income, c.incomeSoft)
            }
            Box(Modifier.size(68.dp).clip(CircleShape).background(bg).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                when (diff) {
                    CountDifference.Match -> stringResource(R.string.count_match_title)
                    is CountDifference.Missing -> stringResource(R.string.count_missing_title)
                    is CountDifference.Extra -> stringResource(R.string.count_extra_title)
                },
                style = Adam.type.h2, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            if (diff !is CountDifference.Match) {
                Spacer(Modifier.height(6.dp))
                Amount(
                    when (diff) { is CountDifference.Missing -> diff.amount; is CountDifference.Extra -> diff.amount; else -> 0 },
                    Modifier.align(Alignment.CenterHorizontally),
                    style = Adam.type.amountLarge, color = tint,
                    sign = if (diff is CountDifference.Missing) Sign.MINUS else Sign.PLUS, peekable = false,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when (diff) {
                    CountDifference.Match -> stringResource(R.string.count_match_body)
                    is CountDifference.Missing -> stringResource(R.string.count_missing_body)
                    is CountDifference.Extra -> stringResource(R.string.count_extra_body)
                },
                style = Adam.type.body, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
            AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                SummaryRow(stringResource(R.string.count_expected), expected)
                AdamDivider()
                SummaryRow(stringResource(R.string.count_counted), counted)
                resolution.explanations.forEachIndexed { i, e ->
                    AdamDivider()
                    ExplanationRow(ui, e) { onResolution(resolution.removeAt(i)) }
                }
                if (diff !is CountDifference.Match && resolution.remaining > 0 && resolution.explanations.isNotEmpty()) {
                    AdamDivider()
                    SummaryRow(stringResource(R.string.count_remaining), resolution.remaining, c.warn)
                }
            }

            if (diff is CountDifference.Missing && ui.lostThisMonth > 0) {
                Text(
                    stringResource(R.string.count_lost_month, com.adampay.app.ui.components.moneyText(ui.lostThisMonth)),
                    style = Adam.type.small, color = c.textMuted, modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                )
            }

            AnimatedVisibility(explaining && !resolution.complete) {
                ExplainEditor(
                    ui = ui,
                    income = diff is CountDifference.Extra,
                    max = resolution.remaining,
                    onAdd = { e -> haptics.confirm(); onResolution(resolution.add(e)); explaining = false },
                    onCancel = { explaining = false },
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 18.dp, vertical = 12.dp)) {
            when {
                diff is CountDifference.Match || resolution.complete ->
                    PrimaryButton(stringResource(R.string.count_save), { haptics.confirm(); onSave() }, Modifier.fillMaxWidth(), icon = R.drawable.ic_check)
                !explaining -> {
                    val missing = diff is CountDifference.Missing
                    PrimaryButton(
                        stringResource(if (missing) R.string.count_log_expense else R.string.count_log_income),
                        { explaining = true }, Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    GhostButton(
                        stringResource(if (missing) R.string.count_mark_lost else R.string.count_mark_found),
                        { haptics.click(); onResolution(resolution.rest()) }, Modifier.fillMaxWidth(),
                        color = if (missing) c.expense else c.income,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, fils: Long, color: Color = Adam.colors.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Adam.type.body, color = Adam.colors.textSecondary, modifier = Modifier.weight(1f))
        Amount(fils, color = color, peekable = false)
    }
}

@Composable
private fun ExplanationRow(ui: WalletUi, e: Explanation, onRemove: () -> Unit) {
    val c = Adam.colors
    val language = LocalAppLanguage.current
    val (label, catId) = when (e) {
        is Explanation.Expense -> (e.note.ifBlank { ui.snapshot.category(e.categoryId)?.name?.forTerms(language) ?: stringResource(R.string.type_expense) }) to e.categoryId
        is Explanation.Income -> (e.note.ifBlank { ui.snapshot.category(e.categoryId)?.name?.forTerms(language) ?: stringResource(R.string.type_income) }) to e.categoryId
        is Explanation.Lost -> stringResource(R.string.type_lost) to null
        is Explanation.Found -> stringResource(R.string.type_found) to null
    }
    val cat = ui.snapshot.category(catId)
    val (tint, bg) = tintPair(cat?.color ?: if (e is Explanation.Lost) 0xFFEE8A80 else 0xFF6FDCA6)
    val out = e is Explanation.Expense || e is Explanation.Lost
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(painterResource(cat?.let { AppIcons.of(it.icon) } ?: if (out) R.drawable.ic_search else R.drawable.ic_star), null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = Adam.type.bodyMedium, color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
        Amount(e.amount, sign = if (out) Sign.MINUS else Sign.PLUS, color = if (out) c.expense else c.income, peekable = false)
        Spacer(Modifier.width(4.dp))
        RoundIconButton(R.drawable.ic_close, stringResource(R.string.action_delete), onRemove, size = 32.dp, tint = c.textMuted, background = Color.Transparent)
    }
}

/** Amount (defaults to what is left), category and note for one explained part of the difference. */
@Composable
private fun ExplainEditor(ui: WalletUi, income: Boolean, max: Long, onAdd: (Explanation) -> Unit, onCancel: () -> Unit) {
    val c = Adam.colors
    var input by remember(max) { mutableStateOf(AmountInput.of(max)) }
    val cats = ui.snapshot.categories(if (income) CategoryType.INCOME else CategoryType.EXPENSE)
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    AdamCard(Modifier.fillMaxWidth().padding(top = 14.dp), padding = PaddingValues(14.dp)) {
        Text(stringResource(if (income) R.string.count_log_income else R.string.count_log_expense), style = Adam.type.title, color = c.text)
        Spacer(Modifier.height(10.dp))
        Amount(input.fils.coerceAtMost(max), style = Adam.type.amountLarge, color = if (income) c.income else c.expense, peekable = false)
        if (input.fils > max) Text(stringResource(R.string.count_capped), style = Adam.type.micro, color = c.warn)
        Spacer(Modifier.height(10.dp))
        CategoryGrid(cats, categoryId) { id -> categoryId = if (categoryId == id) null else id }
        Spacer(Modifier.height(10.dp))
        AdamTextField(note, { note = it.take(80) }, stringResource(R.string.add_note), placeholder = stringResource(R.string.add_note_hint))
        Spacer(Modifier.height(10.dp))
        MoneyKeypad(input, { input = it }, keyHeight = 44.dp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(stringResource(R.string.action_cancel), onCancel)
            PrimaryButton(
                stringResource(R.string.action_add),
                {
                    val amount = input.fils.coerceAtMost(max)
                    val cat = categoryId ?: cats.firstOrNull { it.seedKey == "other" || it.seedKey == "other_in" }?.id
                    onAdd(if (income) Explanation.Income(amount, cat, note.trim()) else Explanation.Expense(amount, cat, note.trim()))
                },
                Modifier.weight(1f), enabled = input.fils > 0,
            )
        }
    }
}
