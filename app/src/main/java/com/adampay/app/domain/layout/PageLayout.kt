package com.adampay.app.domain.layout

enum class PageId { HOME }

/**
 * Every movable section of a page. Sections added in later phases are appended to the user's saved
 * order automatically (at their default position), so a saved layout never hides new features.
 */
enum class BoxId(val page: PageId) {
    /** Phase 6: the featured goal card, right under the total balance. */
    HOME_FEATURED(PageId.HOME),
    HOME_POCKETS(PageId.HOME),
    HOME_TODAY(PageId.HOME),
    HOME_BUDGET(PageId.HOME),
    HOME_QUICK(PageId.HOME),
    HOME_GOALS(PageId.HOME),
    HOME_RECENT(PageId.HOME),
    ;

    companion object {
        fun defaults(page: PageId): List<BoxId> = entries.filter { it.page == page }
    }
}

/** Same model as Hyper Athlete's page customisation: order + hidden set, encoded "A,B|C". */
data class PageLayout(val page: PageId, val order: List<BoxId>, val hidden: Set<BoxId>) {
    val visible: List<BoxId> get() = order.filterNot { it in hidden }

    fun move(from: BoxId, to: BoxId): PageLayout {
        val i = order.indexOf(from)
        val j = order.indexOf(to)
        if (i < 0 || j < 0 || i == j) return this
        val list = order.toMutableList()
        list.add(j, list.removeAt(i))
        return copy(order = list)
    }

    fun toggle(box: BoxId): PageLayout = copy(hidden = if (box in hidden) hidden - box else hidden + box)

    fun encode(): String = order.joinToString(",") { it.name } + "|" + hidden.joinToString(",") { it.name }

    companion object {
        fun default(page: PageId) = PageLayout(page, BoxId.defaults(page), emptySet())

        /** Restores a saved layout, dropping unknown boxes and inserting new ones at their default index. */
        fun decode(page: PageId, raw: String?): PageLayout {
            val defaults = BoxId.defaults(page)
            if (raw.isNullOrBlank()) return default(page)
            val (orderPart, hiddenPart) = raw.split('|').let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
            fun parse(s: String) = s.split(',').mapNotNull { n -> BoxId.entries.firstOrNull { it.name == n && it.page == page } }
            val saved = parse(orderPart).distinct().toMutableList()
            defaults.forEachIndexed { index, box ->
                if (box !in saved) saved.add(index.coerceAtMost(saved.size), box)
            }
            return PageLayout(page, saved, parse(hiddenPart).toSet())
        }
    }
}
