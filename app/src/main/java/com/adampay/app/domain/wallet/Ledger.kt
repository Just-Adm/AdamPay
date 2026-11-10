package com.adampay.app.domain.wallet

import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.db.TxnType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Text kept in both languages (seeded names); the language mode picks one. */
data class BiText(val ar: String, val en: String) {
    /** Category / pocket names: English in MIXED and ENGLISH modes, Arabic in ARABIC mode. */
    fun forTerms(language: com.adampay.app.domain.model.AppLanguage) =
        if (language == com.adampay.app.domain.model.AppLanguage.ARABIC) ar.ifBlank { en } else en.ifBlank { ar }

    fun forUi(language: com.adampay.app.domain.model.AppLanguage) =
        if (language == com.adampay.app.domain.model.AppLanguage.ENGLISH) en.ifBlank { ar } else ar.ifBlank { en }
}

/** Which way a transaction moves money, for signs, colours and icons. */
enum class MoneyFlow { OUT, IN, NEUTRAL }

val TxnType.flow: MoneyFlow
    get() = when (this) {
        TxnType.EXPENSE, TxnType.LOST -> MoneyFlow.OUT
        TxnType.INCOME, TxnType.FOUND, TxnType.OPENING -> MoneyFlow.IN
        TxnType.TRANSFER -> MoneyFlow.NEUTRAL
    }

/** Pure money maths over ledger rows. Mirrors the SQL balance query so both can be tested against each other. */
object Ledger {

    /** Balance per pocket id: money in minus money out. */
    fun balances(txns: List<TxnEntity>): Map<Long, Long> {
        val out = HashMap<Long, Long>()
        for (t in txns) {
            t.toPocketId?.let { out[it] = (out[it] ?: 0) + t.amountFils }
            t.fromPocketId?.let { out[it] = (out[it] ?: 0) - t.amountFils }
        }
        return out
    }

    /** Total cash the user should have right now (transfers cancel out). */
    fun total(balances: Map<Long, Long>, pocketIds: Collection<Long>? = null): Long =
        if (pocketIds == null) balances.values.sum() else pocketIds.sumOf { balances[it] ?: 0 }

    /** Sum of spending (EXPENSE only; LOST is tracked on its own). */
    fun spent(txns: List<TxnEntity>): Long = txns.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountFils }

    fun earned(txns: List<TxnEntity>): Long = txns.filter { it.type == TxnType.INCOME }.sumOf { it.amountFils }

    fun lost(txns: List<TxnEntity>): Long = txns.filter { it.type == TxnType.LOST }.sumOf { it.amountFils }

    fun date(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun startOfDay(day: LocalDate, zone: ZoneId): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Rows grouped by day, newest day first, keeping the input order inside a day. */
    fun byDay(txns: List<TxnEntity>, zone: ZoneId): List<DayGroup> =
        txns.groupBy { date(it.timestamp, zone) }
            .map { (day, rows) ->
                DayGroup(
                    day = day,
                    rows = rows,
                    // Day totals are spending and income only: transfers and opening cash are left out.
                    out = rows.filter { it.type == TxnType.EXPENSE || it.type == TxnType.LOST }.sumOf { it.amountFils },
                    `in` = rows.filter { it.type == TxnType.INCOME || it.type == TxnType.FOUND }.sumOf { it.amountFils },
                )
            }
            .sortedByDescending { it.day }
}

data class DayGroup(val day: LocalDate, val rows: List<TxnEntity>, val out: Long, val `in`: Long)
