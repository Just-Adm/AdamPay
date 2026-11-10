package com.adampay.app.data.wallet

import com.adampay.app.data.db.CategoryEntity
import com.adampay.app.data.db.CategoryType
import com.adampay.app.data.db.PocketEntity
import com.adampay.app.data.db.QuickItemEntity
import com.adampay.app.data.db.TxnType

/** Card gradients offered for pockets (start, end). The first three are the seeded pockets'. */
object CardPalette {
    data class Gradient(val start: Long, val end: Long)

    val Gradients = listOf(
        Gradient(0xFF2FB36D, 0xFF0C4A30), // emerald
        Gradient(0xFF1F9C88, 0xFF0A3836), // pine
        Gradient(0xFF6DBE57, 0xFF24532A), // leaf
        Gradient(0xFF3E7D66, 0xFF15261F), // forest
        Gradient(0xFFE3B25A, 0xFF8F5E1A), // amber
        Gradient(0xFF3FA7A0, 0xFF1B4A66), // lagoon
        Gradient(0xFFE07A8C, 0xFF6E2A52), // rosewood
        Gradient(0xFF7C86F2, 0xFF2C2D78), // indigo
        Gradient(0xFFEF8A4C, 0xFF84301F), // ember
        Gradient(0xFF8DBF5A, 0xFF2D5A2E), // moss
        Gradient(0xFFB18CF0, 0xFF4B2C86), // lilac
        Gradient(0xFF6B7180, 0xFF24262D), // graphite
    )

    /** Solid colours for categories and single-colour cards. */
    val Solids = listOf(
        0xFFF2A65A, 0xFF5AA9F2, 0xFF9B87F5, 0xFFE77A8F, 0xFF4FC7A0, 0xFF7C8CF8,
        0xFFE3B25A, 0xFF5FCB8E, 0xFFEF8A4C, 0xFF3FA7A0, 0xFFB18CF0, 0xFF9AA0A6,
    )
}

/** What a fresh install starts with. Names are stored in both languages; the user can rename them. */
object Seeds {
    val pockets = listOf(
        PocketEntity(nameAr = "في جيبي", nameEn = "In my pocket", icon = "wallet", color = 0xFF2FB36D, color2 = 0xFF0C4A30, order = 0, seedKey = "pocket"),
        PocketEntity(nameAr = "في المنزل", nameEn = "Hidden at home", icon = "cottage", color = 0xFF1F9C88, color2 = 0xFF0A3836, order = 1, seedKey = "home"),
        PocketEntity(nameAr = "الحصّالة", nameEn = "Piggy bank", icon = "savings", color = 0xFF6DBE57, color2 = 0xFF24532A, order = 2, seedKey = "piggy"),
    )

    /**
     * 0.1.0 seeded gold / lagoon / rosewood cards and Jordanian names. Seeded pockets the user never
     * changed are moved to the green look and the formal names (seed key → old colour, old Arabic name).
     */
    val legacyPockets = mapOf(
        "pocket" to (0xFFE3B25AL to "بجيبتي"),
        "home" to (0xFF3FA7A0L to "مخبّا بالبيت"),
        "piggy" to (0xFFE07A8CL to "الحصالة"),
    )

    /** 0.1.0 Arabic category names (Jordanian) → replaced by the formal ones when the user never renamed them. */
    val legacyCategoryNames = mapOf(
        "food" to "أكل", "outings" to "طلعات", "clothes" to "أواعي", "other" to "غير هيك",
        "family" to "مصروف من الأهل", "work" to "شغل", "sold" to "بعت إشي", "other_in" to "غير هيك",
    )

    private fun exp(key: String, ar: String, en: String, icon: String, color: Long, order: Int) =
        CategoryEntity(nameAr = ar, nameEn = en, icon = icon, color = color, type = CategoryType.EXPENSE, order = order, seedKey = key)

    private fun inc(key: String, ar: String, en: String, icon: String, color: Long, order: Int) =
        CategoryEntity(nameAr = ar, nameEn = en, icon = icon, color = color, type = CategoryType.INCOME, order = order, seedKey = key)

    val categories = listOf(
        exp("food", "طعام", "Food", "lunch_dining", 0xFFF2A65A, 0),
        exp("transport", "مواصلات", "Transport", "local_taxi", 0xFF5AA9F2, 1),
        exp("topup", "شحن رصيد", "Phone top-up", "mobile", 0xFF9B87F5, 2),
        exp("outings", "ترفيه", "Outings", "celebration", 0xFFE77A8F, 3),
        exp("clothes", "ملابس", "Clothes", "checkroom", 0xFF4FC7A0, 4),
        exp("games", "ألعاب", "Games", "sports_esports", 0xFF7C8CF8, 5),
        exp("gifts", "هدايا", "Gifts", "redeem", 0xFFE3B25A, 6),
        exp("other", "أخرى", "Other", "more_horiz", 0xFF9AA0A6, 7),
        inc("family", "مصروف من العائلة", "Pocket money from family", "family_restroom", 0xFF5FCB8E, 0),
        inc("eidiya", "عيدية", "Eidiya", "toll", 0xFFE3B25A, 1),
        inc("work", "عمل", "Work", "work", 0xFF5AA9F2, 2),
        inc("sold", "بيع أغراض", "Sold something", "sell", 0xFFF2A65A, 3),
        inc("other_in", "أخرى", "Other", "more_horiz", 0xFF9AA0A6, 4),
    )

    /** Examples the user can edit or delete: everyday Amman prices. */
    fun quickItems(categories: List<CategoryEntity>): List<QuickItemEntity> {
        fun cat(key: String) = categories.firstOrNull { it.seedKey == key }?.id
        return listOf(
            QuickItemEntity(nameAr = "سرفيس", nameEn = "Service taxi", amountFils = 350, categoryId = cat("transport"), type = TxnType.EXPENSE, order = 0),
            QuickItemEntity(nameAr = "ساندويشة فلافل", nameEn = "Falafel sandwich", amountFils = 500, categoryId = cat("food"), type = TxnType.EXPENSE, order = 1),
            QuickItemEntity(nameAr = "شحن رصيد", nameEn = "Top-up", amountFils = 1000, categoryId = cat("topup"), type = TxnType.EXPENSE, order = 2),
        )
    }
}
