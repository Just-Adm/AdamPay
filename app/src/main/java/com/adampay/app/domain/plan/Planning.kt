package com.adampay.app.domain.plan

import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.db.TxnSource
import com.adampay.app.data.db.TxnType
import com.adampay.app.domain.wallet.Ledger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** A week from [start] (inclusive) to [end] (inclusive), using the user's first day of the week. */
data class Week(val start: LocalDate, val end: LocalDate) {
    operator fun contains(day: LocalDate) = !day.isBefore(start) && !day.isAfter(end)

    companion object {
        fun of(day: LocalDate, weekStart: DayOfWeek): Week {
            val start = day.with(TemporalAdjusters.previousOrSame(weekStart))
            return Week(start, start.plusDays(6))
        }
    }
}

/** "4.000 JD left this week ≈ 1.333 per day". Pure; spending = EXPENSE rows only. */
data class BudgetStatus(
    val limit: Long,
    val spent: Long,
    val week: Week,
    /** Days left including today. */
    val daysLeft: Int,
) {
    val left: Long get() = limit - spent
    val over: Boolean get() = spent > limit
    /** What can still be spent per remaining day (0 when nothing is left). */
    val perDay: Long get() = if (left <= 0 || daysLeft <= 0) 0 else left / daysLeft
    /** 0..1 (capped) share of the budget already spent. */
    val fraction: Float get() = if (limit <= 0) 1f else (spent.toFloat() / limit).coerceIn(0f, 1f)

    companion object {
        fun of(limit: Long, txns: List<TxnEntity>, today: LocalDate, weekStart: DayOfWeek, zone: ZoneId): BudgetStatus {
            val week = Week.of(today, weekStart)
            val from = Ledger.startOfDay(week.start, zone)
            val to = Ledger.startOfDay(week.end.plusDays(1), zone)
            val spent = Ledger.spent(txns.filter { it.timestamp in from until to })
            val daysLeft = ChronoUnit.DAYS.between(today, week.end).toInt() + 1
            return BudgetStatus(limit, spent, week, daysLeft)
        }
    }
}

/** Progress of a savings goal and, when there is a saving pace, an estimated reach date. */
data class GoalProgress(
    val saved: Long,
    val target: Long,
    /** Estimated day the target is reached at the recent pace; null when there is no pace (or already reached). */
    val eta: LocalDate?,
    /** To meet the deadline: what still needs saving per week; null without a deadline or when reached. */
    val perWeekNeeded: Long?,
    val deadlinePassed: Boolean,
) {
    val remaining: Long get() = (target - saved).coerceAtLeast(0)
    val reached: Boolean get() = target > 0 && saved >= target
    val fraction: Float get() = if (target <= 0) 0f else (saved.toFloat() / target).coerceIn(0f, 1f)

    companion object {
        /**
         * [ratePerDay] = fils saved per day lately (for a linked pocket: its net inflow over the last 28 days / 28;
         * for a manual goal: saved / days since it was created).
         */
        fun of(saved: Long, target: Long, ratePerDay: Double, today: LocalDate, deadline: LocalDate?): GoalProgress {
            val remaining = (target - saved).coerceAtLeast(0)
            val eta = if (remaining > 0 && ratePerDay > 0.0) today.plusDays(kotlin.math.ceil(remaining / ratePerDay).toLong()) else null
            val perWeek = if (deadline != null && remaining > 0 && deadline.isAfter(today)) {
                val weeks = kotlin.math.ceil(ChronoUnit.DAYS.between(today, deadline) / 7.0).toLong().coerceAtLeast(1)
                (remaining + weeks - 1) / weeks
            } else null
            return GoalProgress(saved, target, eta, perWeek, deadline != null && remaining > 0 && !deadline.isAfter(today))
        }

        /** Net money into a pocket per day over the last [days] days (in minus out, never below zero). */
        fun pocketRate(pocketId: Long, txns: List<TxnEntity>, today: LocalDate, zone: ZoneId, days: Int = 28): Double {
            val from = Ledger.startOfDay(today.minusDays(days.toLong() - 1), zone)
            val net = txns.filter { it.timestamp >= from }.sumOf { t ->
                (if (t.toPocketId == pocketId) t.amountFils else 0) - (if (t.fromPocketId == pocketId) t.amountFils else 0)
            }
            return (net.toDouble() / days).coerceAtLeast(0.0)
        }

        fun manualRate(saved: Long, createdDay: LocalDate, today: LocalDate): Double {
            val days = ChronoUnit.DAYS.between(createdDay, today).coerceAtLeast(1)
            return saved.toDouble() / days
        }
    }
}

/**
 * Logging streak: consecutive days with at least one entry the user made (or a "nothing spent today" tap).
 * Today not logged yet doesn't break the streak; it just isn't counted until it is.
 */
object Streak {
    /** Entries that count: anything the user logged himself, not opening balances or count adjustments. */
    fun counts(t: TxnEntity): Boolean = t.type != TxnType.OPENING && t.createdBy != TxnSource.COUNT

    fun activeDays(txns: List<TxnEntity>, zone: ZoneId): Set<LocalDate> =
        txns.filter(::counts).map { Ledger.date(it.createdAt, zone) }.toSet()

    fun current(active: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in active) today else today.minusDays(1)
        var n = 0
        while (day in active) { n++; day = day.minusDays(1) }
        return n
    }

    fun best(active: Set<LocalDate>): Int {
        var best = 0
        active.forEach { d ->
            if (d.minusDays(1) !in active) {
                var n = 0
                var x = d
                while (x in active) { n++; x = x.plusDays(1) }
                best = maxOf(best, n)
            }
        }
        return best
    }
}
