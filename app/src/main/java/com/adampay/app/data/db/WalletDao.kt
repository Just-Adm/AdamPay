package com.adampay.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {

    // Pockets
    @Query("SELECT * FROM pocket ORDER BY archived, sort_order, id")
    fun observePockets(): Flow<List<PocketEntity>>

    @Query("SELECT * FROM pocket ORDER BY archived, sort_order, id")
    suspend fun pockets(): List<PocketEntity>

    @Upsert suspend fun upsertPocket(p: PocketEntity): Long
    @Update suspend fun updatePockets(list: List<PocketEntity>)

    @Query("SELECT COUNT(*) FROM pocket") suspend fun pocketCount(): Int

    /**
     * Balance of every pocket that has rows, derived from the ledger:
     * money in (to_pocket_id) minus money out (from_pocket_id).
     */
    @Query(
        """
        SELECT pocket_id, SUM(delta) AS balance FROM (
            SELECT to_pocket_id AS pocket_id, amount_fils AS delta FROM txn WHERE to_pocket_id IS NOT NULL
            UNION ALL
            SELECT from_pocket_id AS pocket_id, -amount_fils AS delta FROM txn WHERE from_pocket_id IS NOT NULL
        ) GROUP BY pocket_id
        """,
    )
    fun observeBalances(): Flow<List<PocketBalance>>

    // Categories
    @Query("SELECT * FROM category ORDER BY type, archived, sort_order, id")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM category") suspend fun categoryCount(): Int
    @Query("SELECT * FROM category") suspend fun categories(): List<CategoryEntity>

    @Upsert suspend fun upsertCategory(c: CategoryEntity): Long
    @Update suspend fun updateCategories(list: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM txn WHERE category_id = :id") suspend fun txnCountForCategory(id: Long): Int
    @Query("DELETE FROM category WHERE id = :id") suspend fun deleteCategory(id: Long)

    // Transactions
    @Query("SELECT * FROM txn ORDER BY timestamp DESC, created_at DESC, id DESC")
    fun observeTxns(): Flow<List<TxnEntity>>

    @Query("SELECT * FROM txn ORDER BY timestamp DESC, created_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TxnEntity>>

    @Query("SELECT * FROM txn WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC, id DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<TxnEntity>>

    @Query("SELECT * FROM txn WHERE id = :id") suspend fun txn(id: Long): TxnEntity?

    @Insert suspend fun insertTxn(t: TxnEntity): Long
    @Upsert suspend fun upsertTxn(t: TxnEntity): Long
    @Query("DELETE FROM txn WHERE id = :id") suspend fun deleteTxn(id: Long)

    // Quick items
    @Query("SELECT * FROM quick_item ORDER BY sort_order, id")
    fun observeQuickItems(): Flow<List<QuickItemEntity>>

    @Upsert suspend fun upsertQuickItem(q: QuickItemEntity): Long
    @Update suspend fun updateQuickItems(list: List<QuickItemEntity>)
    @Query("DELETE FROM quick_item WHERE id = :id") suspend fun deleteQuickItem(id: Long)
    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM quick_item") suspend fun nextQuickOrder(): Int
    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM category WHERE type = :type") suspend fun nextCategoryOrder(type: CategoryType): Int

    // Cash counts
    @Insert suspend fun insertCount(c: CashCountEntity): Long

    /** The latest count of every pocket. */
    @Query(
        """
        SELECT c.* FROM cash_count c
        JOIN (SELECT pocket_id, MAX(timestamp) AS t FROM cash_count GROUP BY pocket_id) latest
        ON c.pocket_id = latest.pocket_id AND c.timestamp = latest.t
        """,
    )
    fun observeLatestCounts(): Flow<List<CashCountEntity>>

    // Pocket management
    @Query("SELECT COUNT(*) FROM txn WHERE from_pocket_id = :id OR to_pocket_id = :id") suspend fun txnCountForPocket(id: Long): Int
    @Query("DELETE FROM pocket WHERE id = :id") suspend fun deletePocket(id: Long)
    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM pocket") suspend fun nextPocketOrder(): Int

    // Weekly budget: the newest active row applies.
    @Query("SELECT * FROM weekly_budget WHERE active = 1 ORDER BY from_epoch_day DESC, id DESC LIMIT 1")
    fun observeBudget(): Flow<WeeklyBudgetEntity?>
    @Insert suspend fun insertBudget(b: WeeklyBudgetEntity): Long
    @Query("UPDATE weekly_budget SET active = 0") suspend fun deactivateBudgets()

    // Goals
    @Query("SELECT * FROM goal ORDER BY archived, sort_order, id") fun observeGoals(): Flow<List<GoalEntity>>
    @Upsert suspend fun upsertGoal(g: GoalEntity): Long
    @Update suspend fun updateGoals(list: List<GoalEntity>)
    @Query("DELETE FROM goal WHERE id = :id") suspend fun deleteGoal(id: Long)
    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM goal") suspend fun nextGoalOrder(): Int

    @Query("SELECT * FROM txn WHERE type = 'OPENING'") suspend fun openingRows(): List<TxnEntity>
    @Query("DELETE FROM txn WHERE type = 'OPENING'") suspend fun deleteOpeningRows()

    @Transaction
    suspend fun seed(pockets: List<PocketEntity>, categories: List<CategoryEntity>, quick: (List<CategoryEntity>) -> List<QuickItemEntity>) {
        if (pocketCount() == 0) pockets.forEach { upsertPocket(it) }
        if (categoryCount() == 0) {
            val ids = categories.map { it.copy(id = upsertCategory(it)) }
            quick(ids).forEach { upsertQuickItem(it) }
        }
    }
}
