package com.adampay.app.domain.wallet

import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.db.TxnType

/** History filters: type group, category, pocket and a text search over notes and category names. */
data class HistoryFilter(
    val kind: Kind = Kind.ALL,
    val categoryId: Long? = null,
    val pocketId: Long? = null,
    val query: String = "",
) {
    enum class Kind(val types: Set<TxnType>?) {
        ALL(null),
        EXPENSES(setOf(TxnType.EXPENSE)),
        INCOME(setOf(TxnType.INCOME)),
        TRANSFERS(setOf(TxnType.TRANSFER)),
        LOST_FOUND(setOf(TxnType.LOST, TxnType.FOUND)),
    }

    val isDefault: Boolean get() = this == HistoryFilter()
    val hasExtra: Boolean get() = categoryId != null || pocketId != null

    /** [categoryNames] = every name of each category (both languages) for search. */
    fun matches(t: TxnEntity, categoryNames: Map<Long, List<String>> = emptyMap()): Boolean {
        if (kind.types != null && t.type !in kind.types) return false
        if (categoryId != null && t.categoryId != categoryId) return false
        if (pocketId != null && t.fromPocketId != pocketId && t.toPocketId != pocketId) return false
        val q = normalize(query)
        if (q.isNotEmpty()) {
            val inNote = normalize(t.note).contains(q)
            val inCategory = t.categoryId?.let { id -> categoryNames[id]?.any { normalize(it).contains(q) } } ?: false
            if (!inNote && !inCategory) return false
        }
        return true
    }

    fun apply(txns: List<TxnEntity>, categoryNames: Map<Long, List<String>> = emptyMap()): List<TxnEntity> =
        if (isDefault) txns else txns.filter { matches(it, categoryNames) }

    companion object {
        /** Case-insensitive, ignores Arabic diacritics and treats أ/إ/آ as ا and ة as ه, ى as ي. */
        fun normalize(s: String): String = s.trim().lowercase()
            .filterNot { it in 'ً'..'ْ' || it == 'ـ' }
            .map {
                when (it) {
                    'أ', 'إ', 'آ' -> 'ا'
                    'ة' -> 'ه'
                    'ى' -> 'ي'
                    else -> it
                }
            }.joinToString("")
    }
}
