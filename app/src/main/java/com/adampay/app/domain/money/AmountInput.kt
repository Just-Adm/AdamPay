package com.adampay.app.domain.money

/**
 * What the custom keypad has typed so far, in dinars: up to 6 whole digits and 3 decimals.
 * Immutable; every key returns a new value. Pure so it is unit-tested.
 */
data class AmountInput(val whole: String = "", val frac: String? = null) {

    /** Fils value of the text (0 when empty). */
    val fils: Long get() = (whole.ifEmpty { "0" }.toLong() * Money.FILS_PER_DINAR) +
        (frac ?: "").padEnd(3, '0').toLong()

    val isEmpty: Boolean get() = whole.isEmpty() && frac == null

    /** What the display shows while typing: "12", "12.", "12.5", "0.35". Grouped thousands. */
    fun text(): String {
        val w = whole.ifEmpty { "0" }.toLong().let { n ->
            Money.formatNumber(n * Money.FILS_PER_DINAR, decimals = 0).substringBefore('.')
        }
        return if (frac == null) w else "$w.$frac"
    }

    fun digit(d: Int): AmountInput {
        require(d in 0..9)
        return when {
            frac != null -> if (frac.length >= 3) this else copy(frac = frac + d)
            whole.isEmpty() && d == 0 -> this // no leading zeros; "0." comes from the dot key
            whole.length >= 6 -> this
            else -> copy(whole = whole + d)
        }
    }

    fun dot(): AmountInput = if (frac != null) this else copy(frac = "")

    fun backspace(): AmountInput = when {
        frac != null && frac.isNotEmpty() -> copy(frac = frac.dropLast(1))
        frac != null -> copy(frac = null)
        else -> copy(whole = whole.dropLast(1))
    }

    companion object {
        /** Pre-fills the keypad when editing: 1500 → "1.5", 2000 → "2", 350 → "0.35". */
        fun of(fils: Long): AmountInput {
            if (fils <= 0) return AmountInput()
            val (w, f) = Money.split(fils)
            val frac = f.toString().padStart(3, '0').trimEnd('0')
            return AmountInput(whole = if (w == 0L) "" else w.toString(), frac = frac.ifEmpty { null })
        }
    }
}
