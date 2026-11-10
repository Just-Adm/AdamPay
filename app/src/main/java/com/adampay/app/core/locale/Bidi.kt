package com.adampay.app.core.locale

/**
 * Unicode bidi isolates for mixed Arabic/English text. Without them, an English term or a
 * "12.500 JD" next to Arabic gets its neighbours (·, +, numbers) reordered by the bidi algorithm.
 */
object Bidi {
    private const val FSI = '\u2068' // first-strong isolate: direction from the content itself
    private const val LRI = '\u2066' // left-to-right isolate
    private const val RLI = '\u2067' // right-to-left isolate
    private const val PDI = '\u2069' // pop directional isolate

    /** Keeps [text] in its own direction inside surrounding text. */
    fun isolate(text: String): String = "$FSI$text$PDI"

    /** Forces left-to-right (amounts with their label, ranges). */
    fun ltr(text: String): String = "$LRI$text$PDI"

    /**
     * Joins parts so each keeps its own direction ("بجيبتي · Food"), laid out in the
     * reading order of the interface. The wrapper direction is explicit because an outer first-strong
     * isolate can't see into nested ones.
     */
    fun join(parts: List<String>, separator: String, rtl: Boolean): String =
        (if (rtl) RLI else LRI) + parts.joinToString(separator) { isolate(it) } + PDI

    /** Removes isolate marks (for speech, search, comparisons). */
    fun strip(text: String): String = text.filterNot { it == FSI || it == LRI || it == RLI || it == PDI }
}
