package com.adampay.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adampay.app.R
import com.adampay.app.core.locale.LocalAppLanguage
import com.adampay.app.data.db.PocketEntity
import com.adampay.app.data.db.QuickItemEntity
import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.db.TxnType
import com.adampay.app.data.wallet.name
import com.adampay.app.domain.layout.BoxId
import com.adampay.app.domain.layout.PageLayout
import com.adampay.app.domain.money.Money
import com.adampay.app.ui.WalletUi
import com.adampay.app.ui.components.AdamCard
import com.adampay.app.ui.components.Amount
import com.adampay.app.ui.components.AppIcons
import com.adampay.app.ui.components.EmptyState
import com.adampay.app.ui.components.RoundIconButton
import com.adampay.app.ui.components.SectionTitle
import com.adampay.app.ui.components.Sign
import com.adampay.app.ui.components.TxnItem
import com.adampay.app.ui.components.enter
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.components.tintPair
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes
import com.adampay.app.ui.theme.LocalHideAmounts
import com.adampay.app.ui.theme.LocalMoneyFormat

class HomeActions(
    val onToggleHide: () -> Unit = {},
    val onNoSpend: () -> Unit = {},
    val onBudget: () -> Unit = {},
    val onGoals: () -> Unit = {},
    val onGoal: (com.adampay.app.data.db.GoalEntity) -> Unit = {},
    val onQuick: (QuickItemEntity, String) -> Unit = { _, _ -> },
    val onManageQuick: () -> Unit = {},
    val onSeeAll: () -> Unit = {},
    val onTxn: (TxnEntity) -> Unit = {},
    val onPocket: (PocketEntity) -> Unit = {},
    val onLayoutChange: (PageLayout) -> Unit = {},
    val onLayoutReset: () -> Unit = {},
    val onEditDone: () -> Unit = {},
)

@Composable
fun HomeScreen(
    ui: WalletUi,
    layout: PageLayout,
    editMode: Boolean,
    actions: HomeActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    userName: String = "",
    hour: Int = java.time.LocalTime.now().hour,
) {
    Column(modifier.fillMaxSize()) {
        if (editMode) {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
            EditModeBar(onReset = actions.onLayoutReset, onDone = actions.onEditDone)
        }
        EditableBoxList(
            layout = layout,
            editMode = editMode,
            onLayoutChange = actions.onLayoutChange,
            modifier = Modifier.weight(1f),
            contentPadding = contentPadding,
            header = {
                if (!editMode) item(key = "hero") { Header(ui, userName, hour, actions) }
            },
        ) { id, interactive ->
            Column(Modifier.padding(horizontal = 18.dp)) {
                when (id) {
                    BoxId.HOME_FEATURED -> when {
                        ui.featured != null || ui.goals.isEmpty() || !interactive ->
                            FeaturedGoalCard(ui.featured, onOpen = actions.onGoal, onAdd = actions.onGoals, interactive = interactive)
                        // Goals exist but the user chose no card: nothing here.
                        else -> Unit
                    }
                    BoxId.HOME_POCKETS -> PocketsBox(ui, interactive, actions.onPocket)
                    BoxId.HOME_TODAY -> TodayCard(ui, interactive, actions.onNoSpend)
                    BoxId.HOME_BUDGET -> com.adampay.app.ui.plan.BudgetCard(ui.budget, interactive, actions.onBudget)
                    BoxId.HOME_GOALS -> {
                        // The featured goal already has its own card above; list the others here.
                        val featuredShown = ui.featured != null && BoxId.HOME_FEATURED in layout.visible
                        val rest = if (featuredShown) ui.copy(goals = ui.goals.filter { it.goal.id != ui.featured?.goal?.id }) else ui
                        if (!(featuredShown && rest.goals.isEmpty()) || !interactive) {
                            com.adampay.app.ui.plan.GoalsSection(rest, interactive, actions.onGoals, actions.onGoal)
                        }
                    }
                    BoxId.HOME_QUICK -> QuickSection(ui, interactive, actions)
                    BoxId.HOME_RECENT -> RecentSection(ui, interactive, actions)
                }
            }
        }
    }
}

/** Greeting with the user's name and the eye, then the big total balance (as in 0.1.0, without the wordmark). */
@Composable
private fun Header(ui: WalletUi, userName: String, hour: Int, actions: HomeActions) {
    val c = Adam.colors
    val hidden = LocalHideAmounts.current
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // A faint accent glow behind the balance.
                drawCircle(
                    Brush.radialGradient(listOf(c.glow, Color.Transparent), center = Offset(size.width * 0.5f, size.height * 0.62f), radius = size.width * 0.62f),
                    radius = size.width * 0.62f, center = Offset(size.width * 0.5f, size.height * 0.62f),
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (hour in 4..11) R.string.home_good_morning else R.string.home_good_evening),
                    style = Adam.type.small, color = c.textSecondary,
                )
                Text(
                    userName.ifBlank { stringResource(R.string.home_welcome) },
                    style = Adam.type.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            RoundIconButton(
                if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                stringResource(if (hidden) R.string.home_show_amounts else R.string.home_hide_amounts),
                actions.onToggleHide, size = 40.dp, tint = c.textSecondary,
            )
        }
        Spacer(Modifier.height(30.dp))
        Text(stringResource(R.string.home_total), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(10.dp))
        Amount(
            ui.snapshot.total,
            modifier = Modifier.align(Alignment.CenterHorizontally).enter(0),
            style = Adam.type.hero,
            color = if (ui.snapshot.total < 0) c.expense else c.text,
            labelStyle = Adam.type.title,
            labelColor = c.accentInk,
            labelGap = 8.dp,
            roll = true,
        )
        Spacer(Modifier.height(34.dp))
    }
}

/** The pockets in one box: a single card at a time; swipe sideways for the next one. */
@Composable
private fun PocketsBox(ui: WalletUi, interactive: Boolean, onPocket: (PocketEntity) -> Unit) {
    val c = Adam.colors
    val pockets = ui.snapshot.activePockets
    AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
        if (pockets.isEmpty()) return@AdamCard
        val pager = rememberPagerState { pockets.size }
        HorizontalPager(
            state = pager,
            pageSpacing = 12.dp,
            userScrollEnabled = interactive,
            key = { pockets[it].id },
        ) { page ->
            val p = pockets[page]
            PocketCard(p, ui.snapshot.balance(p.id), onClick = if (interactive) ({ onPocket(p) }) else null)
        }
        if (pockets.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(pockets.size) { i ->
                    val on = i == pager.currentPage
                    val w by androidx.compose.animation.core.animateDpAsState(if (on) 20.dp else 6.dp, label = "dot")
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(6.dp).width(w).clip(AdamShapes.pill)
                            .background(if (on) c.accent else c.borderStrong),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

/** A pocket as a card: its own gradient, icon, name and balance, with faint coin rings. */
@Composable
fun PocketCard(pocket: PocketEntity, balance: Long, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val language = LocalAppLanguage.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val start = Color(pocket.color)
    val end = pocket.color2?.let { Color(it) } ?: start
    Box(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(AdamShapes.pocketCard)
            .background(Brush.linearGradient(listOf(start, end), start = Offset(0f, 0f), end = Offset(900f, 520f)))
            .drawBehind {
                val ring = Color.White.copy(alpha = 0.10f)
                val cx = if (rtl) size.width * 0.06f else size.width * 0.94f
                val center = Offset(cx, size.height * 0.98f)
                drawCircle(ring, radius = size.height * 0.62f, center = center, style = Stroke(1.4.dp.toPx()))
                drawCircle(ring, radius = size.height * 0.42f, center = center, style = Stroke(1.4.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.06f), radius = size.height * 0.24f, center = center)
            }
            .then(if (onClick != null) Modifier.tap(pressedScale = 0.97f, onClick = onClick) else Modifier)
            .padding(16.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(AppIcons.of(pocket.icon)), null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(
                pocket.name.forTerms(language), style = Adam.type.smallMedium, color = Color.White.copy(alpha = 0.86f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Amount(
                balance, style = Adam.type.amountLarge.copy(fontSize = 26.sp), color = Color.White,
                labelColor = Color.White.copy(alpha = 0.75f), labelStyle = Adam.type.micro,
            )
        }
    }
}

@Composable
private fun TodayCard(ui: WalletUi, interactive: Boolean, onNoSpend: () -> Unit) {
    val c = Adam.colors
    SectionTitle(stringResource(R.string.home_today))
    AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stat(stringResource(R.string.home_spent_today), ui.todaySpent, Sign.MINUS, c.expense, R.drawable.ic_south_west, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(40.dp).background(c.border))
            Spacer(Modifier.width(16.dp))
            Stat(stringResource(R.string.home_in_today), ui.todayIn, Sign.PLUS, c.income, R.drawable.ic_north_east, Modifier.weight(1f))
        }
        val count = ui.todayTxns.count { it.type == TxnType.EXPENSE || it.type == TxnType.INCOME }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    count > 0 -> pluralStringResource(R.plurals.home_today_entries, count, count)
                    ui.streak.noSpendToday -> stringResource(R.string.home_no_spend_marked)
                    else -> stringResource(R.string.home_today_none)
                },
                style = Adam.type.small, color = c.textMuted, modifier = Modifier.weight(1f),
            )
            // Logging streak: consecutive days with an entry or "nothing spent today".
            if (ui.streak.current > 0) {
                Row(
                    Modifier.clip(AdamShapes.pill).background(if (ui.streak.today) c.accentSoft else c.surfaceAlt).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(if (ui.streak.today) R.drawable.ic_local_fire_department_fill else R.drawable.ic_local_fire_department), null,
                        tint = if (ui.streak.today) c.accentInk else c.textMuted, modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        pluralStringResource(R.plurals.streak_days, ui.streak.current, ui.streak.current),
                        style = Adam.type.micro, color = if (ui.streak.today) c.accentInk else c.textSecondary,
                    )
                }
            }
        }
        // Nothing logged yet today: one tap keeps the streak without an entry.
        if (count == 0 && !ui.streak.today) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(AdamShapes.tile).background(c.surfaceAlt)
                    .then(if (interactive) Modifier.tap(onClick = onNoSpend) else Modifier).padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_check_circle), null, tint = c.accentInk, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_no_spend), style = Adam.type.smallMedium, color = c.text)
            }
        }
    }
}

@Composable
private fun Stat(label: String, fils: Long, sign: Sign, color: Color, icon: Int, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = Adam.type.small, color = Adam.colors.textSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Amount(fils, sign = if (fils == 0L) Sign.NONE else sign, color = if (fils == 0L) Adam.colors.textSecondary else color, style = Adam.type.amountLarge.copy(fontSize = 21.sp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickSection(ui: WalletUi, interactive: Boolean, actions: HomeActions) {
    val language = LocalAppLanguage.current
    val format = LocalMoneyFormat.current
    SectionTitle(stringResource(R.string.home_quick), action = stringResource(R.string.action_edit), onAction = if (interactive) actions.onManageQuick else null)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ui.snapshot.quickItems.forEach { q ->
            val cat = ui.snapshot.category(q.categoryId)
            val (tint, bg) = tintPair(cat?.color ?: 0xFF9AA0A6)
            val name = q.name.forUi(language)
            Row(
                Modifier
                    .clip(AdamShapes.pill)
                    .background(Adam.colors.surface)
                    .then(if (interactive) Modifier.tap { actions.onQuick(q, name) } else Modifier)
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                    Icon(painterResource(AppIcons.of(cat?.icon)), null, tint = tint, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(name, style = Adam.type.smallMedium, color = Adam.colors.text, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (LocalHideAmounts.current) com.adampay.app.ui.components.HIDDEN else Money.formatNumber(q.amountFils, format.decimals),
                    style = Adam.type.amount.copy(fontSize = 13.sp), color = Adam.colors.textSecondary,
                )
            }
        }
        Row(
            Modifier
                .clip(AdamShapes.pill)
                .background(Adam.colors.accentSoft)
                .then(if (interactive) Modifier.tap(onClick = actions.onManageQuick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_add), null, tint = Adam.colors.accentInk, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.home_quick_new), style = Adam.type.smallMedium, color = Adam.colors.accentInk)
        }
    }
}

@Composable
private fun RecentSection(ui: WalletUi, interactive: Boolean, actions: HomeActions) {
    SectionTitle(
        stringResource(R.string.home_recent),
        action = if (ui.txns.isNotEmpty()) stringResource(R.string.home_see_all) else null,
        onAction = if (interactive) actions.onSeeAll else null,
    )
    AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
        val recent = ui.txns.take(5)
        if (recent.isEmpty()) {
            EmptyState(R.drawable.ic_receipt_long, stringResource(R.string.home_recent_empty_title), stringResource(R.string.home_recent_empty_body))
        } else {
            recent.forEach { t ->
                TxnItem(t, ui.snapshot, onClick = if (interactive) ({ actions.onTxn(t) }) else null)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
