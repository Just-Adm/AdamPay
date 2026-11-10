package com.adampay.app.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adampay.app.R
import com.adampay.app.data.db.TxnEntity
import com.adampay.app.domain.layout.PageId
import com.adampay.app.domain.layout.PageLayout
import com.adampay.app.ui.AppState
import com.adampay.app.ui.WalletEvent
import com.adampay.app.ui.WalletUi
import com.adampay.app.ui.WalletViewModel
import com.adampay.app.ui.add.AddRequest
import com.adampay.app.ui.add.AddSheetContent
import com.adampay.app.ui.components.AdamDivider
import com.adampay.app.ui.components.rememberHaptics
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.components.text
import com.adampay.app.domain.wallet.HistoryFilter
import com.adampay.app.ui.history.HistoryScreen
import com.adampay.app.ui.home.HomeActions
import com.adampay.app.ui.home.HomeScreen
import com.adampay.app.ui.settings.SettingsActions
import com.adampay.app.ui.settings.SettingsHome
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes
import kotlinx.coroutines.launch

enum class Tab(val icon: Int, val iconSelected: Int, val label: Int) {
    HOME(R.drawable.ic_home, R.drawable.ic_home_fill, R.string.nav_home),
    HISTORY(R.drawable.ic_receipt_long, R.drawable.ic_receipt_long_fill, R.string.nav_history),
    SETTINGS(R.drawable.ic_settings, R.drawable.ic_settings_fill, R.string.nav_settings),
}

/** Navigation out of the main tabs (settings sub-pages). */
class MainNav(
    val open: (String) -> Unit = {},
)

/**
 * The three tabs, the floating dock with the gold "+" button, the add sheet and the Undo snackbar.
 * [vm] is null in screenshot tests; [walletUi] then supplies the data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: AppState,
    walletUi: WalletUi,
    vm: WalletViewModel?,
    nav: MainNav,
    onToggleHide: () -> Unit,
    onLayoutChange: (PageLayout) -> Unit,
    onLayoutReset: () -> Unit,
    settingsActions: SettingsActions = SettingsActions(),
    initialTab: Tab = Tab.HOME,
    startInEditMode: Boolean = false,
    initialSheet: AddRequest? = null,
    editModeRequest: Boolean = false,
    onEditModeConsumed: () -> Unit = {},
    /** Fixed hour for the greeting in screenshot tests. */
    homeHour: Int? = null,
) {
    val c = Adam.colors
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var editMode by rememberSaveable { mutableStateOf(startInEditMode) }
    var sheet by remember { mutableStateOf(initialSheet) }
    var historyFilter by remember { mutableStateOf(HistoryFilter()) }
    var budgetSheet by remember { mutableStateOf(false) }
    var pocketSheet by remember { mutableStateOf<com.adampay.app.data.db.PocketEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val format = state.settings.moneyFormat

    LaunchedEffect(editModeRequest) {
        if (editModeRequest) { tab = Tab.HOME; editMode = true; onEditModeConsumed() }
    }

    // Saved / deleted → snackbar with Undo.
    LaunchedEffect(vm) {
        vm?.events?.collect { e ->
            when (e) {
                is WalletEvent.Saved -> {
                    haptics.confirm()
                    if (e.edited) {
                        snackbar.showSnackbar(context.getString(R.string.snack_updated), duration = SnackbarDuration.Short)
                    } else {
                        val msg = context.getString(R.string.snack_saved, format.text(e.txn.amountFils, hidden = state.settings.hideAmounts))
                        val r = snackbar.showSnackbar(msg, actionLabel = context.getString(R.string.action_undo), duration = SnackbarDuration.Short)
                        if (r == SnackbarResult.ActionPerformed) vm.undoSave(e.txn.id)
                    }
                }
                WalletEvent.CountSaved -> {
                    haptics.confirm()
                    snackbar.showSnackbar(context.getString(R.string.snack_count_saved), duration = SnackbarDuration.Short)
                }
                is WalletEvent.Deleted -> {
                    haptics.reject()
                    val r = snackbar.showSnackbar(
                        context.getString(R.string.snack_deleted), actionLabel = context.getString(R.string.action_undo), duration = SnackbarDuration.Long,
                    )
                    if (r == SnackbarResult.ActionPerformed) vm.restore(e.txn)
                }
            }
        }
    }

    BackHandler(enabled = editMode || tab != Tab.HOME) {
        if (editMode) editMode = false else tab = Tab.HOME
    }

    val openTxn: (TxnEntity) -> Unit = { sheet = AddRequest(editing = it) }
    val bottomPad = PaddingValues(bottom = 108.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())

    Box(Modifier.fillMaxSize().background(c.bg)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            label = "tab",
        ) { t ->
            when (t) {
                Tab.HOME -> HomeScreen(
                    ui = walletUi,
                    layout = state.layouts[PageId.HOME] ?: PageLayout.default(PageId.HOME),
                    editMode = editMode,
                    contentPadding = bottomPad,
                    userName = state.settings.userName,
                    hour = homeHour ?: java.time.LocalTime.now().hour,
                    actions = HomeActions(
                        onToggleHide = onToggleHide,
                        onQuick = { item, name -> vm?.logQuick(item, name) },
                        onManageQuick = { nav.open(Routes.QUICK) },
                        onSeeAll = { historyFilter = HistoryFilter(); tab = Tab.HISTORY },
                        onTxn = openTxn,
                        onPocket = { p -> pocketSheet = p },
                        onNoSpend = { haptics.confirm(); vm?.markNoSpend() },
                        onBudget = { budgetSheet = true },
                        onGoals = { nav.open(Routes.GOALS) },
                        onGoal = { g -> nav.open(Routes.goals(g.id)) },
                        onLayoutChange = onLayoutChange,
                        onLayoutReset = onLayoutReset,
                        onEditDone = { editMode = false },
                    ),
                )
                Tab.HISTORY -> HistoryScreen(
                    ui = walletUi,
                    filter = historyFilter,
                    onFilterChange = { historyFilter = it },
                    onTxn = openTxn,
                    contentPadding = bottomPad,
                    onDelete = { vm?.delete(it) },
                )
                Tab.SETTINGS -> SettingsHome(state, walletUi.snapshot, nav, settingsActions.copy(openBudget = { budgetSheet = true }), contentPadding = bottomPad)
            }
        }

        if (!editMode) {
            Dock(
                tab = tab,
                onTab = { haptics.tick(); tab = it },
                onAdd = { haptics.click(); sheet = AddRequest() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 92.dp, start = 12.dp, end = 12.dp),
        ) { data ->
            Snackbar(
                data,
                shape = AdamShapes.tile,
                containerColor = c.surfaceHigh,
                contentColor = c.text,
                actionColor = c.accentInk,
            )
        }
    }

    if (budgetSheet) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { budgetSheet = false },
            sheetState = state,
            containerColor = c.surface,
            shape = AdamShapes.sheet,
            dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 12.dp).size(width = 36.dp, height = 4.dp).clip(AdamShapes.pill).background(c.borderStrong)) },
        ) {
            com.adampay.app.ui.plan.BudgetEditor(walletUi.budget?.limit) { limit ->
                vm?.setBudget(limit)
                scope.launch { state.hide() }.invokeOnCompletion { budgetSheet = false }
            }
        }
    }

    pocketSheet?.let { p ->
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        fun then(action: () -> Unit) {
            scope.launch { state.hide() }.invokeOnCompletion { pocketSheet = null; action() }
        }
        ModalBottomSheet(
            onDismissRequest = { pocketSheet = null },
            sheetState = state,
            containerColor = c.surface,
            shape = AdamShapes.sheet,
            dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 12.dp).size(width = 36.dp, height = 4.dp).clip(AdamShapes.pill).background(c.borderStrong)) },
        ) {
            com.adampay.app.ui.pockets.PocketSheetContent(
                p, walletUi,
                com.adampay.app.ui.pockets.PocketActions(
                    count = { then { nav.open(Routes.count(it.id)) } },
                    transfer = { then { sheet = AddRequest(type = com.adampay.app.data.db.TxnType.TRANSFER, pocketId = it.id) } },
                    statement = { then { historyFilter = HistoryFilter(pocketId = it.id); tab = Tab.HISTORY } },
                    edit = { then { nav.open(Routes.pockets(it.id)) } },
                ),
            )
        }
    }

    sheet?.let { req ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        fun close() {
            scope.launch { sheetState.hide() }.invokeOnCompletion { sheet = null }
        }
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = sheetState,
            containerColor = c.surface,
            shape = AdamShapes.sheet,
            dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 12.dp).size(width = 36.dp, height = 4.dp).clip(AdamShapes.pill).background(c.borderStrong)) },
        ) {
            AddSheetContent(
                request = req,
                snapshot = walletUi.snapshot,
                defaultPocketId = state.settings.defaultPocketId,
                now = vm?.now() ?: System.currentTimeMillis(),
                onSave = { vm?.save(it); close() },
                onDelete = { vm?.delete(it); close() },
            )
        }
    }
}

/** Floating dock: a capsule of tabs (the selected one shows its label) and a detached gold "+" orb. */
@Composable
private fun Dock(tab: Tab, onTab: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = Adam.colors
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(64.dp)
                .shadow(18.dp, AdamShapes.pill, ambientColor = Color.Black, spotColor = Color.Black.copy(alpha = if (c.isDark) 0.6f else 0.18f))
                .clip(AdamShapes.pill)
                .background(c.surfaceHigh)
                .border(1.dp, c.border, AdamShapes.pill)
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                val selected = t == tab
                val bg by animateColorAsState(if (selected) c.accentSoft else Color.Transparent, tween(200), label = "dockBg")
                val fg by animateColorAsState(if (selected) c.accentInk else c.textSecondary, tween(200), label = "dockFg")
                val label = stringResource(t.label)
                Row(
                    Modifier
                        .height(52.dp)
                        .clip(AdamShapes.pill)
                        .background(bg)
                        .tap(role = Role.Tab, onClick = { onTab(t) })
                        .semantics { contentDescription = label }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(if (selected) t.iconSelected else t.icon), null, tint = fg, modifier = Modifier.size(22.dp))
                    AnimatedVisibility(
                        selected,
                        enter = expandHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = shrinkHorizontally(tween(160)) + fadeOut(tween(100)),
                    ) {
                        Row {
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = Adam.type.smallMedium, color = fg, maxLines = 1)
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .size(64.dp)
                .shadow(18.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent.copy(alpha = 0.5f))
                .clip(CircleShape)
                .background(c.accent)
                .tap(pressedScale = 0.9f, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.nav_add), tint = c.onAccent, modifier = Modifier.size(30.dp))
        }
    }
}

/** Settings sub-page routes. */
object Routes {
    const val GOALS = "goals"
    fun goals(editId: Long?) = if (editId == null) GOALS else "$GOALS?edit=$editId"
    const val REMINDERS = "reminders"
    const val COUNT = "count"
    const val POCKETS = "pockets"
    const val DENOMS = "denominations"
    fun count(pocketId: Long?) = if (pocketId == null) COUNT else "$COUNT?pocket=$pocketId"
    fun pockets(editId: Long?) = if (editId == null) POCKETS else "$POCKETS?edit=$editId"
    const val APPEARANCE = "appearance"
    const val LANGUAGE = "language"
    const val MONEY = "money"
    const val CATEGORIES = "categories"
    const val QUICK = "quick"
    const val SECURITY = "security"
    const val ACCESS = "access"
    const val BACKUP = "backup"
    const val PIN = "pin"
    fun pin(flow: com.adampay.app.ui.security.PinFlow) = "$PIN/${flow.name}"
}
