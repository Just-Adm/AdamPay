package com.adampay.app.ui.plan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adampay.app.R
import com.adampay.app.core.locale.AppLocales
import com.adampay.app.core.locale.LocalAppLanguage
import com.adampay.app.core.locale.bidiJoin
import com.adampay.app.data.db.GoalEntity
import com.adampay.app.data.wallet.CardPalette
import com.adampay.app.data.wallet.WalletSnapshot
import com.adampay.app.data.wallet.name
import com.adampay.app.domain.money.AmountInput
import com.adampay.app.domain.plan.BudgetStatus
import com.adampay.app.ui.GoalView
import com.adampay.app.ui.WalletUi
import com.adampay.app.ui.components.AdamCard
import com.adampay.app.ui.components.AdamTextField
import com.adampay.app.ui.components.Amount
import com.adampay.app.ui.components.AppIcons
import com.adampay.app.ui.components.Chip
import com.adampay.app.ui.components.EmptyState
import com.adampay.app.ui.components.GhostButton
import com.adampay.app.ui.components.IconBadge
import com.adampay.app.ui.components.MoneyKeypad
import com.adampay.app.ui.components.PrimaryButton
import com.adampay.app.ui.components.RoundIconButton
import com.adampay.app.ui.components.SectionTitle
import com.adampay.app.ui.components.Sign
import com.adampay.app.ui.components.moneyText
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.components.tintPair
import com.adampay.app.ui.settings.ColorRow
import com.adampay.app.ui.settings.ReorderPage
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A thin rounded progress bar. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "progress")
    Box(modifier.fillMaxWidth().height(8.dp).clip(AdamShapes.pill).background(Adam.colors.surfaceAlt)) {
        Box(Modifier.fillMaxWidth(f).height(8.dp).clip(AdamShapes.pill).background(color))
    }
}

@Composable
private fun dateText(day: LocalDate): String =
    DateTimeFormatter.ofPattern(stringResource(R.string.day_pattern_year), AppLocales.ui(LocalAppLanguage.current)).format(day)

// ───────────────────────────── Weekly budget ─────────────────────────────

/** Home: what is left this week and per day, or an invitation to set a budget. */
@Composable
fun BudgetCard(budget: BudgetStatus?, interactive: Boolean, onEdit: () -> Unit) {
    val c = Adam.colors
    SectionTitle(stringResource(R.string.budget_title), action = if (budget != null) stringResource(R.string.action_edit) else null, onAction = if (interactive) onEdit else null)
    AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp), onClick = if (interactive) onEdit else null) {
        if (budget == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(R.drawable.ic_flag, size = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.budget_set), style = Adam.type.bodyMedium, color = c.text)
                    Text(stringResource(R.string.budget_set_sub), style = Adam.type.small, color = c.textMuted)
                }
            }
            return@AdamCard
        }
        Text(stringResource(if (budget.over) R.string.budget_over else R.string.budget_left), style = Adam.type.small, color = c.textSecondary)
        Spacer(Modifier.height(4.dp))
        Amount(
            if (budget.over) -budget.left else budget.left,
            style = Adam.type.amountLarge, color = if (budget.over) c.expense else c.text,
            sign = if (budget.over) Sign.MINUS else Sign.NONE,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (budget.over) stringResource(R.string.budget_over_hint)
            else bidiJoin(listOf(stringResource(R.string.budget_per_day, moneyText(budget.perDay)), stringResource(R.string.budget_days_left, androidx.compose.ui.res.pluralStringResource(R.plurals.days_count, budget.daysLeft, budget.daysLeft)))),
            style = Adam.type.small, color = if (budget.over) c.expense else c.textMuted,
        )
        Spacer(Modifier.height(14.dp))
        ProgressBar(budget.fraction, when {
            budget.over -> c.expense
            budget.fraction > 0.8f -> c.warn
            else -> c.accent
        })
        Spacer(Modifier.height(8.dp))
        Row {
            Text(stringResource(R.string.budget_spent, moneyText(budget.spent)), style = Adam.type.micro, color = c.textMuted, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.budget_limit, moneyText(budget.limit)), style = Adam.type.micro, color = c.textMuted)
        }
    }
}

/** Sheet: weekly limit on the keypad; remove the budget. */
@Composable
fun BudgetEditor(current: Long?, onSave: (Long?) -> Unit) {
    val c = Adam.colors
    var input by remember { mutableStateOf(AmountInput.of(current ?: 0)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
        Text(stringResource(R.string.budget_title), style = Adam.type.h2, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.budget_editor_hint), style = Adam.type.small, color = c.textSecondary)
        Spacer(Modifier.height(16.dp))
        Amount(input.fils, Modifier.align(Alignment.CenterHorizontally), style = Adam.type.hero.copy(fontSize = 40.sp), color = if (input.isEmpty) c.textMuted else c.text, labelStyle = Adam.type.title, peekable = false)
        Spacer(Modifier.height(16.dp))
        MoneyKeypad(input, { input = it }, keyHeight = 50.dp)
        Spacer(Modifier.height(14.dp))
        PrimaryButton(stringResource(R.string.action_save), { onSave(input.fils) }, Modifier.fillMaxWidth(), enabled = input.fils > 0)
        if (current != null) {
            Spacer(Modifier.height(10.dp))
            GhostButton(stringResource(R.string.budget_remove), { onSave(null) }, Modifier.fillMaxWidth(), color = c.expense)
        }
    }
}

// ───────────────────────────── Goals ─────────────────────────────

/** Home: the first goals with their progress. */
@Composable
fun GoalsSection(ui: WalletUi, interactive: Boolean, onAll: () -> Unit, onGoal: (GoalEntity) -> Unit) {
    SectionTitle(stringResource(R.string.goals_title), action = stringResource(if (ui.goals.isEmpty()) R.string.goals_add else R.string.home_see_all), onAction = if (interactive) onAll else null)
    AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
        if (ui.goals.isEmpty()) {
            Row(Modifier.fillMaxWidth().then(if (interactive) Modifier.tap(press = false, onClick = onAll) else Modifier).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(R.drawable.ic_savings, size = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.goals_empty_title), style = Adam.type.bodyMedium, color = Adam.colors.text)
                    Text(stringResource(R.string.goals_empty_body), style = Adam.type.small, color = Adam.colors.textMuted)
                }
            }
        } else {
            ui.goals.take(2).forEach { g -> GoalRow(g, ui.snapshot, if (interactive) ({ onGoal(g.goal) }) else null) }
        }
    }
}

@Composable
fun GoalRow(view: GoalView, snapshot: WalletSnapshot, onClick: (() -> Unit)?) {
    val c = Adam.colors
    val language = LocalAppLanguage.current
    val g = view.goal
    val p = view.progress
    val (tint, bg) = tintPair(g.color)
    Column(Modifier.fillMaxWidth().clip(AdamShapes.tile).then(if (onClick != null) Modifier.tap(press = false, onClick = onClick) else Modifier).padding(vertical = 12.dp, horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.of(g.icon), tint = tint, background = bg, size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.name, style = Adam.type.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = when {
                    p.reached -> stringResource(R.string.goal_reached)
                    p.deadlinePassed -> stringResource(R.string.goal_deadline_passed)
                    p.perWeekNeeded != null -> stringResource(R.string.goal_per_week, moneyText(p.perWeekNeeded))
                    p.eta != null -> stringResource(R.string.goal_eta, dateText(p.eta))
                    else -> stringResource(R.string.goal_no_pace)
                }
                val linked = g.pocketId?.let { snapshot.pocket(it)?.name?.forTerms(language) }
                Text(bidiJoin(listOfNotNull(sub, linked)), style = Adam.type.small, color = if (p.reached) c.income else if (p.deadlinePassed) c.warn else c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${(p.fraction * 100).toInt()}%", style = Adam.type.amount, color = if (p.reached) c.income else c.text)
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(p.fraction, if (p.reached) c.income else Color(g.color))
        Spacer(Modifier.height(6.dp))
        Row {
            Amount(p.saved, style = Adam.type.small, color = c.textSecondary, labelStyle = Adam.type.micro, modifier = Modifier.weight(1f))
            Amount(p.target, style = Adam.type.small, color = c.textMuted, labelStyle = Adam.type.micro)
        }
    }
}

/** Goals page: reorder, add, edit; manual goals get money put aside or taken out in the editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    ui: WalletUi,
    onSave: (GoalEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onReorder: (List<GoalEntity>) -> Unit,
    onAdjust: (GoalEntity, Long) -> Unit,
    onBack: () -> Unit,
    initialEditing: GoalEntity? = null,
    /** The goal on the home card, if any; the editor offers to put a goal there. */
    featuredId: Long? = null,
    onSaveFeatured: (GoalEntity, Boolean) -> Unit = { g, _ -> onSave(g) },
) {
    var editing by remember { mutableStateOf(initialEditing) }
    ReorderPage(
        title = stringResource(R.string.goals_title),
        items = ui.goals,
        key = { it.goal.id },
        onBack = onBack,
        onReorder = { list -> onReorder(list.map { it.goal }) },
        onNew = {
            editing = GoalEntity(name = "", icon = "savings", color = CardPalette.Solids[ui.goals.size % CardPalette.Solids.size], targetFils = 0, createdAt = 0)
        },
        newLabel = stringResource(R.string.goals_new),
        header = {
            Text(stringResource(R.string.goals_hint), style = Adam.type.small, color = Adam.colors.textMuted, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 14.dp))
        },
        empty = { EmptyState(R.drawable.ic_savings, stringResource(R.string.goals_empty_title), stringResource(R.string.goals_empty_body)) },
    ) { v -> Box(Modifier.padding(horizontal = 10.dp)) { GoalRow(v, ui.snapshot) { editing = v.goal } } }

    editing?.let { g ->
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { editing = null }, sheetState = state, containerColor = Adam.colors.surface, shape = AdamShapes.sheet) {
            GoalEditor(
                g, ui,
                onSave = { onSave(it); editing = null },
                featured = g.id != 0L && g.id == featuredId,
                onSaveFeatured = { goal, on -> onSaveFeatured(goal, on); editing = null },
                onDelete = { onDelete(g.id); editing = null },
                onAdjust = { delta -> onAdjust(g, delta); editing = null },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GoalEditor(
    goal: GoalEntity,
    ui: WalletUi,
    onSave: (GoalEntity) -> Unit,
    onDelete: () -> Unit,
    onAdjust: (Long) -> Unit,
    featured: Boolean = false,
    onSaveFeatured: ((GoalEntity, Boolean) -> Unit)? = null,
) {
    val c = Adam.colors
    val language = LocalAppLanguage.current
    var name by rememberSaveable { mutableStateOf(goal.name) }
    var icon by rememberSaveable { mutableStateOf(goal.icon) }
    var color by rememberSaveable { mutableStateOf(goal.color) }
    var target by remember { mutableStateOf(AmountInput.of(goal.targetFils)) }
    var deadline by rememberSaveable { mutableStateOf(goal.deadlineEpochDay) }
    var pocketId by rememberSaveable { mutableStateOf(goal.pocketId) }
    var keypad by rememberSaveable { mutableStateOf(if (goal.id == 0L) "target" else "") }
    var put by remember { mutableStateOf(AmountInput()) }
    var pickDate by remember { mutableStateOf(false) }
    var onHome by rememberSaveable { mutableStateOf(featured) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (goal.id == 0L) R.string.goals_new else R.string.goal_edit), style = Adam.type.h2, color = c.text, modifier = Modifier.weight(1f))
            if (goal.id != 0L) RoundIconButton(R.drawable.ic_delete, stringResource(R.string.action_delete), onDelete, tint = c.expense, background = c.expenseSoft)
        }

        // Manual goals: put money aside / take it out (linked goals follow their pocket).
        if (goal.id != 0L && goal.pocketId == null) {
            AdamCard(Modifier.fillMaxWidth().padding(top = 14.dp), color = c.surfaceAlt, padding = PaddingValues(14.dp)) {
                Text(stringResource(R.string.goal_saved_so_far), style = Adam.type.small, color = c.textSecondary)
                Amount(goal.savedFils, style = Adam.type.amountLarge, peekable = false)
                Spacer(Modifier.height(10.dp))
                if (keypad == "put") {
                    Amount(put.fils, style = Adam.type.title, color = if (put.isEmpty) c.textMuted else c.text, peekable = false)
                    Spacer(Modifier.height(8.dp))
                    MoneyKeypad(put, { put = it }, keyHeight = 44.dp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(stringResource(R.string.goal_take_out), { onAdjust(-put.fils) }, Modifier.weight(1f), color = c.expense)
                        PrimaryButton(stringResource(R.string.goal_put_aside), { onAdjust(put.fils) }, Modifier.weight(1f), enabled = put.fils > 0)
                    }
                } else {
                    GhostButton(stringResource(R.string.goal_change_saved), { keypad = "put" }, Modifier.fillMaxWidth(), icon = R.drawable.ic_add)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        AdamTextField(name, { name = it.take(30) }, stringResource(R.string.goal_name), placeholder = stringResource(R.string.goal_name_hint))

        Text(stringResource(R.string.goal_target), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))
        AdamCard(Modifier.fillMaxWidth(), color = c.surfaceAlt, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), onClick = { keypad = if (keypad == "target") "" else "target" }) {
            Amount(target.fils, style = Adam.type.amountLarge, color = if (target.isEmpty) c.textMuted else c.text, peekable = false)
        }
        if (keypad == "target") {
            Spacer(Modifier.height(10.dp))
            MoneyKeypad(target, { target = it }, keyHeight = 46.dp)
        }

        Text(stringResource(R.string.goal_pocket), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp))
        Text(stringResource(R.string.goal_pocket_hint), style = Adam.type.micro, color = c.textMuted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Chip(stringResource(R.string.goal_pocket_none), selected = pocketId == null) { pocketId = null } }
            items(ui.snapshot.activePockets, key = { it.id }) { p ->
                Chip(p.name.forTerms(language), selected = pocketId == p.id, icon = AppIcons.of(p.icon), iconTint = Color(p.color)) { pocketId = p.id }
            }
        }

        Text(stringResource(R.string.goal_deadline), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(deadline?.let { dateText(LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.goal_deadline_none), selected = deadline != null, icon = R.drawable.ic_calendar_month) { pickDate = true }
            if (deadline != null) Chip(stringResource(R.string.goal_deadline_clear)) { deadline = null }
        }

        Text(stringResource(R.string.cat_icon), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalIcons.forEach { key ->
                val on = key == icon
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(if (on) Color(color) else c.surfaceAlt).tap(pressedScale = 0.9f) { icon = key },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(AppIcons.of(key)), null, tint = if (on) c.bg else c.textSecondary, modifier = Modifier.size(20.dp)) }
            }
        }
        Text(stringResource(R.string.cat_color), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
        ColorRow(CardPalette.Solids, color) { color = it }

        if (onSaveFeatured != null) {
            Spacer(Modifier.height(14.dp))
            com.adampay.app.ui.components.SwitchRow(
                R.drawable.ic_star, stringResource(R.string.goal_featured), onHome, { onHome = it },
                subtitle = stringResource(R.string.goal_featured_sub),
            )
        }
        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            stringResource(R.string.action_save),
            {
                val edited = goal.copy(name = name.trim(), icon = icon, color = color, targetFils = target.fils, deadlineEpochDay = deadline, pocketId = pocketId)
                if (onSaveFeatured != null && onHome != featured) onSaveFeatured(edited, onHome) else onSave(edited)
            },
            Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && target.fils > 0,
        )
        Spacer(Modifier.height(20.dp))
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (deadline?.let { LocalDate.ofEpochDay(it) } ?: ui.today.plusMonths(1)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= ui.today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { deadline = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    pickDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}

private val GoalIcons = listOf(
    "savings", "headphones", "sports_esports", "mobile", "checkroom", "fitness_center", "sports_soccer", "two_wheeler",
    "menu_book", "school", "beach_access", "redeem", "diamond", "star", "music_note", "pets",
)
