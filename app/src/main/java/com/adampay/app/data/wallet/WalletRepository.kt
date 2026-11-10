package com.adampay.app.data.wallet

import androidx.room.withTransaction
import com.adampay.app.data.db.AdamDatabase
import com.adampay.app.data.db.CashCountEntity
import com.adampay.app.data.db.GoalEntity
import com.adampay.app.data.db.WeeklyBudgetEntity
import com.adampay.app.domain.cash.CountSheet
import com.adampay.app.domain.cash.Explanation
import com.adampay.app.domain.cash.Resolution
import com.adampay.app.data.db.CategoryEntity
import com.adampay.app.data.db.CategoryType
import com.adampay.app.data.db.PocketEntity
import com.adampay.app.data.db.QuickItemEntity
import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.db.TxnSource
import com.adampay.app.data.db.TxnType
import com.adampay.app.domain.wallet.BiText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

val PocketEntity.name: BiText get() = BiText(nameAr, nameEn)
val CategoryEntity.name: BiText get() = BiText(nameAr, nameEn)
val QuickItemEntity.name: BiText get() = BiText(nameAr, nameEn)

/** Pockets with their derived balances, categories and quick items: what most screens need. */
data class WalletSnapshot(
    val pockets: List<PocketEntity>,
    val balances: Map<Long, Long>,
    val categories: List<CategoryEntity>,
    val quickItems: List<QuickItemEntity>,
) {
    val activePockets: List<PocketEntity> get() = pockets.filterNot { it.archived }
    /** Total cash I should have now: every pocket, archived ones included if they still hold money. */
    val total: Long get() = pockets.sumOf { balances[it.id] ?: 0 }
    fun balance(pocketId: Long): Long = balances[pocketId] ?: 0
    fun pocket(id: Long?): PocketEntity? = pockets.firstOrNull { it.id == id }
    fun category(id: Long?): CategoryEntity? = categories.firstOrNull { it.id == id }
    fun categories(type: CategoryType): List<CategoryEntity> = categories.filter { it.type == type && !it.archived }

    /** The add sheet's default pocket: the chosen one if it still exists, else the first active pocket. */
    fun defaultPocket(preferred: Long?): PocketEntity? =
        activePockets.firstOrNull { it.id == preferred } ?: activePockets.firstOrNull()

    companion object {
        val Empty = WalletSnapshot(emptyList(), emptyMap(), emptyList(), emptyList())
    }
}

enum class PocketRemoval { ARCHIVED, DELETED, HAS_MONEY, LAST_ONE }

@Singleton
class WalletRepository @Inject constructor(
    private val db: AdamDatabase,
    private val clock: Clock,
) {
    private val dao = db.walletDao()
    private val seedLock = Mutex()

    val snapshot: Flow<WalletSnapshot> = combine(
        dao.observePockets(),
        dao.observeBalances(),
        dao.observeCategories(),
        dao.observeQuickItems(),
    ) { pockets, balances, categories, quick ->
        WalletSnapshot(pockets, balances.associate { it.pocketId to it.balance }, categories, quick)
    }.distinctUntilChanged()

    val txns: Flow<List<TxnEntity>> = dao.observeTxns()

    fun recent(limit: Int): Flow<List<TxnEntity>> = dao.observeRecent(limit)

    fun between(from: Long, to: Long): Flow<List<TxnEntity>> = dao.observeBetween(from, to)

    /** Creates the default pockets, categories and quick items on first run. Safe to call every launch. */
    suspend fun ensureSeeded() = seedLock.withLock {
        dao.seed(Seeds.pockets, Seeds.categories) { Seeds.quickItems(it) }
        // Untouched pockets from 0.1.0 get the current look and names.
        val updated = dao.pockets().mapNotNull { p ->
            val legacy = Seeds.legacyPockets[p.seedKey] ?: return@mapNotNull null
            val seed = Seeds.pockets.firstOrNull { it.seedKey == p.seedKey } ?: return@mapNotNull null
            var next = p
            if (p.color == legacy.first) next = next.copy(color = seed.color, color2 = seed.color2)
            if (p.nameAr == legacy.second) next = next.copy(nameAr = seed.nameAr)
            next.takeIf { it != p }
        }
        if (updated.isNotEmpty()) dao.updatePockets(updated)
        val renamed = dao.categories().mapNotNull { c ->
            val old = Seeds.legacyCategoryNames[c.seedKey] ?: return@mapNotNull null
            val seed = Seeds.categories.firstOrNull { it.seedKey == c.seedKey } ?: return@mapNotNull null
            c.copy(nameAr = seed.nameAr).takeIf { c.nameAr == old }
        }
        if (renamed.isNotEmpty()) dao.updateCategories(renamed)
    }

    fun now(): Long = clock.millis()

    /** Inserts a new row or replaces an edited one. Amount must be positive. */
    suspend fun save(txn: TxnEntity): Long {
        require(txn.amountFils > 0) { "amount must be positive" }
        return if (txn.id == 0L) dao.insertTxn(txn.copy(createdAt = now())) else { dao.upsertTxn(txn); txn.id }
    }

    suspend fun txn(id: Long): TxnEntity? = dao.txn(id)

    /** Deletes and returns the row so the UI can offer Undo. */
    suspend fun delete(id: Long): TxnEntity? {
        val row = dao.txn(id) ?: return null
        dao.deleteTxn(id)
        return row
    }

    /** Undo of [delete]: puts the exact row back (same id). */
    suspend fun restore(txn: TxnEntity) {
        dao.upsertTxn(txn)
    }

    /** One tap on a quick item. [note] is the item's name in the current language, so history reads "Falafel". */
    suspend fun logQuick(item: QuickItemEntity, fallbackPocketId: Long, note: String, source: TxnSource = TxnSource.QUICK): TxnEntity {
        val pocket = item.pocketId ?: fallbackPocketId
        val t = now()
        val row = when (item.type) {
            TxnType.INCOME -> TxnEntity(type = TxnType.INCOME, amountFils = item.amountFils, categoryId = item.categoryId, toPocketId = pocket, note = note, timestamp = t, createdBy = source)
            else -> TxnEntity(type = TxnType.EXPENSE, amountFils = item.amountFils, categoryId = item.categoryId, fromPocketId = pocket, note = note, timestamp = t, createdBy = source)
        }
        val id = save(row)
        return row.copy(id = id)
    }

    /** Opening balance per pocket (the sum of its OPENING rows). */
    suspend fun openingBalances(): Map<Long, Long> =
        dao.openingRows().groupBy { it.toPocketId ?: 0L }.mapValues { (_, rows) -> rows.sumOf { it.amountFils } }

    /**
     * Setup's "opening balance" step. Replaces every OPENING row, so running setup again from Settings
     * corrects the starting cash instead of adding to it. A pocket's first OPENING time is kept.
     */
    suspend fun setOpeningBalances(amounts: Map<Long, Long>) = db.withTransaction {
        val t = now()
        val previous = dao.openingRows()
        val firstTime = previous.groupBy { it.toPocketId }.mapValues { (_, rows) -> rows.minOf { it.timestamp } }
        dao.deleteOpeningRows()
        amounts.filterValues { it > 0 }.forEach { (pocket, fils) ->
            val at = firstTime[pocket] ?: t
            dao.insertTxn(TxnEntity(type = TxnType.OPENING, amountFils = fils, toPocketId = pocket, timestamp = at, createdAt = at))
        }
    }

    // Cash counts

    /** The latest count of every pocket, by pocket id. */
    val latestCounts: Flow<Map<Long, CashCountEntity>> =
        dao.observeLatestCounts().map { list -> list.associateBy { it.pocketId } }

    /**
     * Saves a cash count and the rows that explain its difference, all or nothing.
     * Missing money → EXPENSE (explained) / LOST rows out of the pocket; extra money → INCOME / FOUND
     * rows into it. Afterwards the pocket's balance equals [counted] when the resolution is complete.
     */
    suspend fun saveCount(
        pocketId: Long,
        expected: Long,
        counted: Long,
        sheet: CountSheet?,
        resolution: Resolution,
    ): Long = db.withTransaction {
        val t = now()
        val countId = dao.insertCount(
            CashCountEntity(pocketId = pocketId, timestamp = t, expectedFils = expected, countedFils = counted, denominations = sheet?.toJson()),
        )
        resolution.explanations.forEach { e ->
            val row = when (e) {
                is Explanation.Expense -> TxnEntity(type = TxnType.EXPENSE, amountFils = e.amount, categoryId = e.categoryId, fromPocketId = pocketId, note = e.note, timestamp = t)
                is Explanation.Lost -> TxnEntity(type = TxnType.LOST, amountFils = e.amount, fromPocketId = pocketId, timestamp = t)
                is Explanation.Income -> TxnEntity(type = TxnType.INCOME, amountFils = e.amount, categoryId = e.categoryId, toPocketId = pocketId, note = e.note, timestamp = t)
                is Explanation.Found -> TxnEntity(type = TxnType.FOUND, amountFils = e.amount, toPocketId = pocketId, timestamp = t)
            }
            dao.insertTxn(row.copy(createdAt = t, createdBy = TxnSource.COUNT, cashCountId = countId))
        }
        countId
    }

    // Transfers

    /** Moves cash between two pockets. Not spending: the total stays the same. */
    suspend fun transfer(fromPocketId: Long, toPocketId: Long, amount: Long, note: String = "", timestamp: Long = now()): Long {
        require(fromPocketId != toPocketId) { "same pocket" }
        return save(TxnEntity(type = TxnType.TRANSFER, amountFils = amount, fromPocketId = fromPocketId, toPocketId = toPocketId, note = note, timestamp = timestamp))
    }

    // Pockets

    suspend fun savePocket(p: PocketEntity): Long =
        if (p.id == 0L) dao.upsertPocket(p.copy(order = dao.nextPocketOrder())) else { dao.upsertPocket(p); p.id }

    suspend fun reorderPockets(ordered: List<PocketEntity>) = dao.updatePockets(ordered.mapIndexed { i, p -> p.copy(order = i) })

    /**
     * What removing a pocket does: refused while it still holds money (move or spend it first),
     * archived when it has history (old rows keep its name), deleted when it was never used.
     */
    suspend fun removePocket(p: PocketEntity, balance: Long): PocketRemoval {
        if (balance != 0L) return PocketRemoval.HAS_MONEY
        if (dao.pockets().count { !it.archived } <= 1 && !p.archived) return PocketRemoval.LAST_ONE
        return if (dao.txnCountForPocket(p.id) > 0) {
            dao.upsertPocket(p.copy(archived = true)); PocketRemoval.ARCHIVED
        } else {
            dao.deletePocket(p.id); PocketRemoval.DELETED
        }
    }

    suspend fun restorePocket(p: PocketEntity) { dao.upsertPocket(p.copy(archived = false)) }

    // Weekly budget

    val budget: Flow<WeeklyBudgetEntity?> = dao.observeBudget()

    /** Sets the weekly limit from [fromEpochDay] (the current week's start); null limit = no budget. Old rows stay for stats. */
    suspend fun setBudget(limitFils: Long?, fromEpochDay: Long) = db.withTransaction {
        dao.deactivateBudgets()
        if (limitFils != null && limitFils > 0) dao.insertBudget(WeeklyBudgetEntity(limitFils = limitFils, fromEpochDay = fromEpochDay))
    }

    // Goals

    val goals: Flow<List<GoalEntity>> = dao.observeGoals()

    suspend fun saveGoal(g: GoalEntity): Long =
        if (g.id == 0L) dao.upsertGoal(g.copy(order = dao.nextGoalOrder(), createdAt = now())) else { dao.upsertGoal(g); g.id }

    suspend fun deleteGoal(id: Long) = dao.deleteGoal(id)
    suspend fun reorderGoals(ordered: List<GoalEntity>) = dao.updateGoals(ordered.mapIndexed { i, g -> g.copy(order = i) })

    /** A manual goal (no linked pocket): add to or take from what is put aside. Never below zero. */
    suspend fun adjustGoal(g: GoalEntity, delta: Long) { dao.upsertGoal(g.copy(savedFils = (g.savedFils + delta).coerceAtLeast(0))) }

    // Categories
    suspend fun saveCategory(c: CategoryEntity): Long =
        if (c.id == 0L) dao.upsertCategory(c.copy(order = dao.nextCategoryOrder(c.type))) else { dao.upsertCategory(c); c.id }

    suspend fun reorderCategories(ordered: List<CategoryEntity>) =
        dao.updateCategories(ordered.mapIndexed { i, c -> c.copy(order = i) })

    /** Categories with history are archived (old rows keep their name); unused ones are deleted. */
    suspend fun removeCategory(c: CategoryEntity) {
        if (dao.txnCountForCategory(c.id) > 0) dao.upsertCategory(c.copy(archived = true)) else dao.deleteCategory(c.id)
    }

    // Quick items
    suspend fun saveQuickItem(q: QuickItemEntity): Long =
        if (q.id == 0L) dao.upsertQuickItem(q.copy(order = dao.nextQuickOrder())) else { dao.upsertQuickItem(q); q.id }

    suspend fun deleteQuickItem(id: Long) = dao.deleteQuickItem(id)

    suspend fun reorderQuickItems(ordered: List<QuickItemEntity>) =
        dao.updateQuickItems(ordered.mapIndexed { i, q -> q.copy(order = i) })
}
