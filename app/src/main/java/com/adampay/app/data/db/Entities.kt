package com.adampay.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The money database. Every amount is a Long in fils (1 JD = 1000 fils).
 * Pocket balances are never stored: they are derived from [TxnEntity] rows (single source of truth).
 * Enums are stored by name so a reorder of the Kotlin enum can't corrupt old rows.
 */

enum class TxnType {
    EXPENSE, INCOME, TRANSFER, LOST, FOUND,
    /** Cash the user already had when starting the app. Counts in balances, never in income stats. */
    OPENING,
}

enum class TxnSource { MANUAL, VOICE, QUICK, WIDGET, NOTIFICATION, COUNT }

enum class CategoryType { EXPENSE, INCOME }

enum class DebtDirection { I_OWE, OWES_ME }

/** A place where cash lives: "In my pocket", "Hidden at home", "Piggy bank". */
@Entity(tableName = "pocket")
data class PocketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    /** Key into the app's icon set (see ui/components/AppIcons). */
    val icon: String,
    /** Card colour; with [color2] the card is a gradient from [color] to [color2]. ARGB. */
    val color: Long,
    val color2: Long? = null,
    @ColumnInfo(name = "sort_order") val order: Int = 0,
    val archived: Boolean = false,
    /** Set for the pockets the app created on first run ("pocket", "home", "piggy"). */
    @ColumnInfo(name = "seed_key") val seedKey: String? = null,
)

@Entity(tableName = "category", indices = [Index("type")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    val icon: String,
    val color: Long,
    val type: CategoryType,
    @ColumnInfo(name = "sort_order") val order: Int = 0,
    val archived: Boolean = false,
    @ColumnInfo(name = "seed_key") val seedKey: String? = null,
)

/**
 * One movement of cash.
 *  - EXPENSE / LOST: money leaves [fromPocketId].
 *  - INCOME / FOUND / OPENING: money arrives in [toPocketId].
 *  - TRANSFER: from [fromPocketId] to [toPocketId] (not spending).
 */
@Entity(
    tableName = "txn",
    indices = [Index("timestamp"), Index("category_id"), Index("from_pocket_id"), Index("to_pocket_id")],
)
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxnType,
    @ColumnInfo(name = "amount_fils") val amountFils: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "from_pocket_id") val fromPocketId: Long? = null,
    @ColumnInfo(name = "to_pocket_id") val toPocketId: Long? = null,
    val note: String = "",
    /** When the money moved (epoch millis); the user can change it. */
    val timestamp: Long,
    @ColumnInfo(name = "created_by") val createdBy: TxnSource = TxnSource.MANUAL,
    /** The cash count that produced this row (LOST / FOUND / explained difference), if any. */
    @ColumnInfo(name = "cash_count_id") val cashCountId: Long? = null,
    /** When the row was written (epoch millis); tie-breaker for ordering. */
    @ColumnInfo(name = "created_at") val createdAt: Long = timestamp,
)

/** A saved frequent entry: one tap logs it ("Service 0.350"). */
@Entity(tableName = "quick_item")
data class QuickItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "amount_fils") val amountFils: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    /** null = the default pocket at the time of logging. */
    @ColumnInfo(name = "pocket_id") val pocketId: Long? = null,
    val type: TxnType = TxnType.EXPENSE,
    @ColumnInfo(name = "sort_order") val order: Int = 0,
)

/** "Count my cash": what was really there vs what the app expected. */
@Entity(tableName = "cash_count", indices = [Index("pocket_id"), Index("timestamp")])
data class CashCountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "pocket_id") val pocketId: Long,
    val timestamp: Long,
    @ColumnInfo(name = "expected_fils") val expectedFils: Long,
    @ColumnInfo(name = "counted_fils") val countedFils: Long,
    /** {"50000":1,"500":2} = denomination in fils → count; null when typed directly. */
    val denominations: String? = null,
)

@Entity(tableName = "weekly_budget")
data class WeeklyBudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "limit_fils") val limitFils: Long,
    /** Epoch day the budget applies from (a week start). */
    @ColumnInfo(name = "from_epoch_day") val fromEpochDay: Long,
    val active: Boolean = true,
)

@Entity(tableName = "goal")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Long,
    @ColumnInfo(name = "target_fils") val targetFils: Long,
    /** Saved so far when the goal has no linked pocket (a linked pocket's balance is used otherwise). */
    @ColumnInfo(name = "saved_fils") val savedFils: Long = 0,
    @ColumnInfo(name = "deadline_epoch_day") val deadlineEpochDay: Long? = null,
    @ColumnInfo(name = "pocket_id") val pocketId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "sort_order") val order: Int = 0,
    val archived: Boolean = false,
)

@Entity(tableName = "debt")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    @ColumnInfo(name = "amount_fils") val amountFils: Long,
    val direction: DebtDirection,
    @ColumnInfo(name = "due_epoch_day") val dueEpochDay: Long? = null,
    val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val settled: Boolean = false,
    @ColumnInfo(name = "settled_at") val settledAt: Long? = null,
)

/** Result row of the balance query. */
data class PocketBalance(
    @ColumnInfo(name = "pocket_id") val pocketId: Long,
    val balance: Long,
)
