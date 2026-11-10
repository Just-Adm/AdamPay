package com.adampay.app.domain.money

/**
 * Jordanian Dinar maths. Every amount in the app is a [Long] number of fils:
 * 1 JD = 1000 fils, 1 qirsh (piaster) = 10 fils. Floating point is never used for money.
 */
object Money {
    const val FILS_PER_DINAR = 1000L
    const val FILS_PER_QIRSH = 10L

    /** Largest amount the keypad accepts: 999,999.999 JD. */
    const val MAX_FILS = 999_999_999L

    fun dinars(jd: Long): Long = jd * FILS_PER_DINAR
    fun qirsh(q: Long): Long = q * FILS_PER_QIRSH

    /** Whole dinars and the remaining fils (always 0..999) of a non-negative amount. */
    fun split(fils: Long): Pair<Long, Long> {
        val a = kotlin.math.abs(fils)
        return a / FILS_PER_DINAR to a % FILS_PER_DINAR
    }

    /**
     * Number part only, Latin digits: 12500 → "12.500".
     * [decimals] = 3 shows fils; 2 shows qirsh ("12.50") unless the amount has single fils, which are never hidden.
     * [grouping] adds thousands separators ("1,250.000").
     * Negative amounts use a real minus sign (U+2212).
     */
    fun formatNumber(fils: Long, decimals: Int = 3, grouping: Boolean = true): String {
        val (whole, rest) = split(fils)
        val wholeText = if (grouping) group(whole) else whole.toString()
        val frac = when {
            decimals >= 3 || rest % 10 != 0L -> rest.toString().padStart(3, '0')
            decimals == 2 -> (rest / 10).toString().padStart(2, '0')
            else -> ""
        }
        val number = if (frac.isEmpty()) wholeText else "$wholeText.$frac"
        return if (fils < 0) "−$number" else number
    }

    private fun group(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val sb = StringBuilder()
        s.forEachIndexed { i, c ->
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Parses a typed decimal amount in dinars ("12", "12.5", "0.35", ".5", "1,250.75") into fils.
     * More than 3 decimals, a negative sign or anything that isn't a number returns null.
     */
    fun parse(text: String): Long? {
        val t = text.trim().replace(",", "").replace('٫', '.') // Arabic decimal separator
            .map { ch -> if (ch in '٠'..'٩') '0' + (ch - '٠') else ch }.joinToString("")
        if (t.isEmpty() || t == ".") return null
        val parts = t.split('.')
        if (parts.size > 2) return null
        val whole = parts[0].ifEmpty { "0" }
        val frac = parts.getOrElse(1) { "" }
        if (!whole.all { it.isDigit() } || !frac.all { it.isDigit() } || frac.length > 3) return null
        if (whole.length > 6) return null
        val value = whole.toLong() * FILS_PER_DINAR + frac.padEnd(3, '0').ifEmpty { "0" }.toLong()
        return value.takeIf { it <= MAX_FILS }
    }

    /** a / b rounded down to whole fils (e.g. budget per day); 0 when [days] <= 0. */
    fun perDay(fils: Long, days: Int): Long = if (days <= 0) 0 else fils / days
}

/** User's display choice: how many decimals and which label. */
data class MoneyFormat(
    val decimals: Int = 3,
    val label: CurrencyLabel = CurrencyLabel.JD,
) {
    /** "12.500" (number only). */
    fun number(fils: Long, signed: Boolean = false): String {
        val n = Money.formatNumber(fils, decimals)
        return if (signed && fils > 0) "+$n" else n
    }
}

enum class CurrencyLabel(val text: String) { JD("JD"), ARABIC("د.أ") }
