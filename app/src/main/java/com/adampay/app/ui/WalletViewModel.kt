package com.adampay.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adampay.app.data.db.CashCountEntity
import com.adampay.app.data.db.CategoryEntity
import com.adampay.app.data.db.GoalEntity
import com.adampay.app.domain.plan.BudgetStatus
import com.adampay.app.domain.plan.GoalProgress
import com.adampay.app.domain.plan.Streak
import com.adampay.app.data.db.PocketEntity
import com.adampay.app.data.wallet.PocketRemoval
import com.adampay.app.domain.cash.CountSheet
import com.adampay.app.domain.cash.Resolution
import com.adampay.app.data.db.QuickItemEntity
import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.settings.SettingsRepository
import com.adampay.app.data.wallet.WalletRepository
import com.adampay.app.data.wallet.WalletSnapshot
import com.adampay.app.domain.wallet.Ledger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** What Home and History show. Today's rows are cut from the full list off the main thread. */
data class WalletUi(
    val snapshot: WalletSnapshot,
    val today: LocalDate,
    val txns: List<TxnEntity>,
    val todayTxns: List<TxnEntity>,
    val loaded: Boolean = true,
    /** Latest cash count of each pocket. */
    val lastCounts: Map<Long, CashCountEntity> = emptyMap(),
    /** Money marked "lost / don't remember" since the 1st of this month. */
    val lostThisMonth: Long = 0,
    /** This week's budget; null when no weekly budget is set. */
    val budget: BudgetStatus? = null,
    val goals: List<GoalView> = emptyList(),
    val streak: StreakView = StreakView(),
    /** The goal on the card under the total balance (see AppSettings.featuredGoalId). */
    val featured: GoalView? = null,
) {
    val todaySpent: Long get() = Ledger.spent(todayTxns)
    val todayIn: Long get() = Ledger.earned(todayTxns)

    companion object {
        fun empty(today: LocalDate) = WalletUi(WalletSnapshot.Empty, today, emptyList(), emptyList(), loaded = false)
    }
}

data class GoalView(val goal: GoalEntity, val progress: GoalProgress)

/** Active goals with their progress (Home, the goals page and the goal widget). */
fun goalViews(goals: List<GoalEntity>, snap: WalletSnapshot, txns: List<TxnEntity>, today: LocalDate, zone: java.time.ZoneId): List<GoalView> =
    goals.filterNot { it.archived }.map { g -> GoalView(g, goalProgress(g, snap, txns, today, zone)) }

private fun goalProgress(g: GoalEntity, snap: WalletSnapshot, txns: List<TxnEntity>, today: LocalDate, zone: java.time.ZoneId): GoalProgress {
    val pocket = g.pocketId?.let { snap.pocket(it) }
    val created = Ledger.date(g.createdAt, zone)
    val deadline = g.deadlineEpochDay?.let { LocalDate.ofEpochDay(it) }
    return if (pocket != null) {
        GoalProgress.of(snap.balance(pocket.id).coerceAtLeast(0), g.targetFils, GoalProgress.pocketRate(pocket.id, txns, today, zone), today, deadline)
    } else {
        GoalProgress.of(g.savedFils, g.targetFils, GoalProgress.manualRate(g.savedFils, created, today), today, deadline)
    }
}

/** Which goal the home card shows. */
object Featured {
    fun pick(goals: List<GoalView>, setting: Long?): GoalView? = when (setting) {
        0L -> null
        null -> goals.firstOrNull { !it.progress.reached } ?: goals.firstOrNull()
        else -> goals.firstOrNull { it.goal.id == setting } ?: goals.firstOrNull { !it.progress.reached } ?: goals.firstOrNull()
    }
}

/** Current / best logging streak; [today] = today already counts (logged or "nothing spent"). */
data class StreakView(val current: Int = 0, val best: Int = 0, val today: Boolean = false, val noSpendToday: Boolean = false)

/** One-off messages for the snackbar (with Undo). */
sealed interface WalletEvent {
    data class Saved(val txn: TxnEntity, val edited: Boolean) : WalletEvent
    data class Deleted(val txn: TxnEntity) : WalletEvent
    data object CountSaved : WalletEvent
}

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val wallet: WalletRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    /** The current date, re-checked every 30 s so "today" rolls over at midnight. */
    private val todayFlow: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now(clock))
            delay(30_000)
        }
    }.distinctUntilChanged()

    private val base = combine(wallet.snapshot, wallet.txns, todayFlow, wallet.latestCounts) { snap, txns, today, counts ->
        Base(snap, txns, today, counts)
    }

    val ui: StateFlow<WalletUi> = combine(base, wallet.budget, wallet.goals, settings.settings) { b, budget, goals, s ->
        val zone = clock.zone
        val (snap, txns, today, counts) = b
        val start = Ledger.startOfDay(today, zone)
        val end = Ledger.startOfDay(today.plusDays(1), zone)
        val monthStart = Ledger.startOfDay(today.withDayOfMonth(1), zone)
        val active = Streak.activeDays(txns, zone) + s.noSpendDays.map { LocalDate.ofEpochDay(it) }
        val views = goalViews(goals, snap, txns, today, zone)
        WalletUi(
            snap, today, txns, txns.filter { it.timestamp in start until end },
            lastCounts = counts,
            lostThisMonth = Ledger.lost(txns.filter { it.timestamp >= monthStart }),
            budget = budget?.let { BudgetStatus.of(it.limitFils, txns, today, s.weekStart, zone) },
            goals = views,
            featured = Featured.pick(views, s.featuredGoalId),
            streak = StreakView(
                current = Streak.current(active, today),
                best = Streak.best(active),
                today = today in active,
                noSpendToday = today.toEpochDay() in s.noSpendDays,
            ),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletUi.empty(LocalDate.now(clock)))

    private data class Base(val snap: WalletSnapshot, val txns: List<TxnEntity>, val today: LocalDate, val counts: Map<Long, CashCountEntity>)

    private val _events = Channel<WalletEvent>(Channel.BUFFERED)
    val events: Flow<WalletEvent> = _events.receiveAsFlow()

    fun now(): Long = clock.millis()

    fun save(txn: TxnEntity) = viewModelScope.launch {
        val id = wallet.save(txn)
        _events.send(WalletEvent.Saved(txn.copy(id = id), edited = txn.id != 0L))
    }

    fun delete(id: Long) = viewModelScope.launch {
        wallet.delete(id)?.let { _events.send(WalletEvent.Deleted(it)) }
    }

    /** Undo of a delete: the exact row comes back. */
    fun restore(txn: TxnEntity) = viewModelScope.launch { wallet.restore(txn) }

    /** Undo of a fresh save: the row goes away without another snackbar. */
    fun undoSave(id: Long) = viewModelScope.launch { wallet.delete(id) }

    fun logQuick(item: QuickItemEntity, note: String) = viewModelScope.launch {
        val pocket = ui.value.snapshot.defaultPocket(settings.current().defaultPocketId) ?: return@launch
        val row = wallet.logQuick(item, pocket.id, note)
        _events.send(WalletEvent.Saved(row, edited = false))
    }

    fun saveCount(pocketId: Long, expected: Long, counted: Long, sheet: CountSheet?, resolution: Resolution) = viewModelScope.launch {
        wallet.saveCount(pocketId, expected, counted, sheet, resolution)
        _events.send(WalletEvent.CountSaved)
    }

    fun savePocket(p: PocketEntity) = viewModelScope.launch { wallet.savePocket(p) }
    fun reorderPockets(list: List<PocketEntity>) = viewModelScope.launch { wallet.reorderPockets(list) }
    fun restorePocket(p: PocketEntity) = viewModelScope.launch { wallet.restorePocket(p) }
    fun removePocket(p: PocketEntity, onResult: (PocketRemoval) -> Unit) = viewModelScope.launch {
        onResult(wallet.removePocket(p, ui.value.snapshot.balance(p.id)))
    }

    // Weekly budget, goals, streak

    fun setBudget(limitFils: Long?) = viewModelScope.launch {
        val s = settings.current()
        val week = com.adampay.app.domain.plan.Week.of(LocalDate.now(clock), s.weekStart)
        wallet.setBudget(limitFils, week.start.toEpochDay())
    }

    /** [featured]: true = put it on the home card, false = take it off (no card), null = leave as is. */
    fun saveGoal(g: GoalEntity, featured: Boolean? = null) = viewModelScope.launch {
        val id = wallet.saveGoal(g)
        val current = ui.value.featured?.goal?.id
        when {
            featured == true -> settings.setFeaturedGoal(id)
            featured == false && current == id -> settings.setFeaturedGoal(0)
        }
    }
    fun deleteGoal(id: Long) = viewModelScope.launch { wallet.deleteGoal(id) }
    fun reorderGoals(list: List<GoalEntity>) = viewModelScope.launch { wallet.reorderGoals(list) }
    fun adjustGoal(g: GoalEntity, delta: Long) = viewModelScope.launch { wallet.adjustGoal(g, delta) }

    /** "Nothing spent today": keeps the streak without an entry. */
    fun markNoSpend() = viewModelScope.launch { settings.markNoSpend(LocalDate.now(clock).toEpochDay()) }

    fun saveQuickItem(q: QuickItemEntity) = viewModelScope.launch { wallet.saveQuickItem(q) }
    fun deleteQuickItem(id: Long) = viewModelScope.launch { wallet.deleteQuickItem(id) }
    fun reorderQuickItems(list: List<QuickItemEntity>) = viewModelScope.launch { wallet.reorderQuickItems(list) }

    fun saveCategory(c: CategoryEntity) = viewModelScope.launch { wallet.saveCategory(c) }
    fun removeCategory(c: CategoryEntity) = viewModelScope.launch { wallet.removeCategory(c) }
    fun reorderCategories(list: List<CategoryEntity>) = viewModelScope.launch { wallet.reorderCategories(list) }
}
