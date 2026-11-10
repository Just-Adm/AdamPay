package com.adampay.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PocketEntity::class,
        CategoryEntity::class,
        TxnEntity::class,
        QuickItemEntity::class,
        CashCountEntity::class,
        WeeklyBudgetEntity::class,
        GoalEntity::class,
        DebtEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AdamDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
}
