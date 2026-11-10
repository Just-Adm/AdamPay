package com.adampay.app.domain.model

import com.adampay.app.domain.money.CurrencyLabel
import com.adampay.app.domain.money.MoneyFormat
import java.time.DayOfWeek

/** Three language modes. MIXED = Arabic interface with English category and pocket names. */
enum class AppLanguage { MIXED, ARABIC, ENGLISH }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TextSize(val scale: Float) { SMALL(0.92f), DEFAULT(1f), LARGE(1.1f) }

/** Everything the user can change that isn't money data. Persisted in DataStore. */
data class AppSettings(
    val onboardingDone: Boolean = false,
    /** True once setup was finished at least once: running it again from Settings edits instead of starting over. */
    val setupOnce: Boolean = false,
    /** What the app calls the user ("Good evening, Adam"). */
    val userName: String = "",
    val language: AppLanguage = AppLanguage.MIXED,
    /** Dark is the hero look. */
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentArgb: Long = DEFAULT_ACCENT,
    val textSize: TextSize = TextSize.DEFAULT,
    val hapticsEnabled: Boolean = true,
    /** Shows •••• instead of amounts everywhere; tap a hidden amount to peek. */
    val hideAmounts: Boolean = false,
    val decimals: Int = 3,
    val currencyLabel: CurrencyLabel = CurrencyLabel.JD,
    /** Jordanian week: Saturday → Friday (weekly budget, "this week"). */
    val weekStart: DayOfWeek = DayOfWeek.SATURDAY,
    /** Pocket the add sheet picks by default; null = the first pocket ("In my pocket"). */
    val defaultPocketId: Long? = null,
    /** "What did you spend today?" every evening, with an inline amount reply. */
    val nightlyReminder: Boolean = true,
    val nightlyTime: java.time.LocalTime = java.time.LocalTime.of(21, 30),
    /** Remind to count cash when no pocket was counted for [countEveryDays] days. Off by default. */
    val countReminder: Boolean = false,
    val countEveryDays: Int = 7,
    /** Days the user tapped "nothing spent today" (epoch days): they keep the logging streak. */
    val noSpendDays: Set<Long> = emptySet(),
    /** Notes and coins in the cash counter, fils, largest first. */
    val denominations: List<Long> = com.adampay.app.domain.cash.Denominations.Default,
    /** Goal on the card under the total balance: null = the first goal not yet reached, 0 = none, else its id. */
    val featuredGoalId: Long? = null,
) {
    val moneyFormat: MoneyFormat get() = MoneyFormat(decimals, currencyLabel)

    companion object {
        const val DEFAULT_ACCENT = 0xFF2FB36DL
    }
}
