package com.adampay.app.domain.cash

import com.adampay.app.domain.money.Money

/** Notes and coins the counter offers, in fils, largest first. Editable in Settings. */
object Denominations {
    /** Notes 50, 20, 10, 5, 1 JD; coins ½ JD, ¼ JD, 10 qirsh, 5 qirsh, 1 qirsh. */
    val Default: List<Long> = listOf(50_000, 20_000, 10_000, 5_000, 1_000, 500, 250, 100, 50, 10)

    /** Notes are 1 JD and up; anything smaller is a coin. */
    fun isNote(fils: Long) = fils >= Money.FILS_PER_DINAR

    fun encode(list: List<Long>): String = normalize(list).joinToString(",")

    fun decode(raw: String?): List<Long> =
        raw?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.let(::normalize)?.takeIf { it.isNotEmpty() } ?: Default

    /** Positive, unique, largest first. */
    fun normalize(list: List<Long>): List<Long> = list.filter { it > 0 }.distinct().sortedDescending()
}

/** How many of each denomination were counted. Immutable; pure maths, unit-tested. */
data class CountSheet(val counts: Map<Long, Int> = emptyMap()) {
    val total: Long get() = counts.entries.sumOf { (value, n) -> value * n }
    val pieces: Int get() = counts.values.sum()

    fun count(value: Long): Int = counts[value] ?: 0
    fun set(value: Long, n: Int): CountSheet =
        CountSheet(if (n <= 0) counts - value else counts + (value to n.coerceAtMost(9_999)))
    fun plus(value: Long) = set(value, count(value) + 1)
    fun minus(value: Long) = set(value, count(value) - 1)

    /** {"50000":1,"500":2}: stored with the count so it can be shown later. */
    fun toJson(): String =
        counts.entries.filter { it.value > 0 }.sortedByDescending { it.key }
            .joinToString(",", "{", "}") { (k, v) -> "\"$k\":$v" }

    companion object {
        fun fromJson(json: String?): CountSheet {
            if (json.isNullOrBlank()) return CountSheet()
            val map = Regex("\"(\\d+)\"\\s*:\\s*(\\d+)").findAll(json)
                .associate { it.groupValues[1].toLong() to it.groupValues[2].toInt() }
            return CountSheet(map.filterValues { it > 0 })
        }
    }
}

/** The result of comparing counted cash with what the ledger says should be there. */
sealed interface CountDifference {
    data object Match : CountDifference
    /** Less cash than expected: [amount] fils need explaining (an expense) or marking as lost. */
    data class Missing(val amount: Long) : CountDifference
    /** More cash than expected: [amount] fils are forgotten income or found money. */
    data class Extra(val amount: Long) : CountDifference

    companion object {
        fun of(expected: Long, counted: Long): CountDifference = when {
            counted == expected -> Match
            counted < expected -> Missing(expected - counted)
            else -> Extra(counted - expected)
        }
    }
}

/**
 * One way of explaining part of a difference. Several can be combined ("1.500 was shawarma, the rest
 * I don't remember"); [Resolution.remaining] tells what is still unexplained.
 */
sealed interface Explanation {
    val amount: Long
    data class Expense(override val amount: Long, val categoryId: Long?, val note: String = "") : Explanation
    data class Lost(override val amount: Long) : Explanation
    data class Income(override val amount: Long, val categoryId: Long?, val note: String = "") : Explanation
    data class Found(override val amount: Long) : Explanation
}

data class Resolution(val difference: CountDifference, val explanations: List<Explanation> = emptyList()) {
    private val target: Long get() = when (difference) {
        CountDifference.Match -> 0
        is CountDifference.Missing -> difference.amount
        is CountDifference.Extra -> difference.amount
    }

    val explained: Long get() = explanations.sumOf { it.amount }
    val remaining: Long get() = (target - explained).coerceAtLeast(0)
    val complete: Boolean get() = remaining == 0L

    /** Adds an explanation of the right kind, capped at what is still unexplained. */
    fun add(e: Explanation): Resolution {
        val fits = when (difference) {
            CountDifference.Match -> false
            is CountDifference.Missing -> e is Explanation.Expense || e is Explanation.Lost
            is CountDifference.Extra -> e is Explanation.Income || e is Explanation.Found
        }
        require(fits) { "explanation does not match the difference" }
        val amount = e.amount.coerceAtMost(remaining)
        if (amount <= 0) return this
        val capped = when (e) {
            is Explanation.Expense -> e.copy(amount = amount)
            is Explanation.Lost -> e.copy(amount = amount)
            is Explanation.Income -> e.copy(amount = amount)
            is Explanation.Found -> e.copy(amount = amount)
        }
        return copy(explanations = explanations + capped)
    }

    fun removeAt(index: Int): Resolution = copy(explanations = explanations.filterIndexed { i, _ -> i != index })

    /** Marks whatever is left as lost (missing) or found (extra). */
    fun rest(): Resolution = when (difference) {
        CountDifference.Match -> this
        is CountDifference.Missing -> if (remaining > 0) add(Explanation.Lost(remaining)) else this
        is CountDifference.Extra -> if (remaining > 0) add(Explanation.Found(remaining)) else this
    }
}
