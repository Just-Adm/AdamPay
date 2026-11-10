package com.adampay.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adampay.app.domain.layout.PageId
import com.adampay.app.domain.layout.PageLayout
import com.adampay.app.domain.model.AppLanguage
import com.adampay.app.domain.model.AppSettings
import com.adampay.app.domain.model.TextSize
import com.adampay.app.domain.model.ThemeMode
import com.adampay.app.domain.money.CurrencyLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Reads/writes [AppSettings]. Every read degrades to defaults on bad data instead of crashing. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object K {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val setupOnce = booleanPreferencesKey("setup_once")
        val userName = stringPreferencesKey("user_name")
        val language = stringPreferencesKey("language")
        val themeMode = stringPreferencesKey("theme_mode")
        val accent = longPreferencesKey("accent_argb")
        val textSize = stringPreferencesKey("text_size")
        val haptics = booleanPreferencesKey("haptics")
        val hideAmounts = booleanPreferencesKey("hide_amounts")
        val decimals = intPreferencesKey("decimals")
        val currencyLabel = stringPreferencesKey("currency_label")
        val weekStart = stringPreferencesKey("week_start")
        val defaultPocket = longPreferencesKey("default_pocket_id")
        val denominations = stringPreferencesKey("denominations")
        val nightly = booleanPreferencesKey("r_nightly")
        val nightlyTime = stringPreferencesKey("r_nightly_time")
        val countReminder = booleanPreferencesKey("r_count")
        val countEvery = intPreferencesKey("r_count_days")
        val noSpend = stringPreferencesKey("no_spend_days")
        val featuredGoal = longPreferencesKey("featured_goal")
        fun layout(page: PageId) = stringPreferencesKey("layout_${page.name}")
    }

    private val data: Flow<Preferences> = context.settingsStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val settings: Flow<AppSettings> = data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    /** Section order / visibility per page (page customisation mode). */
    val layouts: Flow<Map<PageId, PageLayout>> =
        data.map { prefs -> PageId.entries.associateWith { PageLayout.decode(it, prefs[K.layout(it)]) } }

    suspend fun setLayout(layout: PageLayout) = edit { it[K.layout(layout.page)] = layout.encode() }
    suspend fun resetLayout(page: PageId) = edit { it.remove(K.layout(page)) }

    suspend fun setOnboardingDone(done: Boolean) = edit {
        it[K.onboardingDone] = done
        if (done) it[K.setupOnce] = true
    }
    suspend fun setUserName(name: String) = edit { it[K.userName] = name.trim().take(30) }
    suspend fun setLanguage(v: AppLanguage) = edit { it[K.language] = v.name }
    suspend fun setThemeMode(v: ThemeMode) = edit { it[K.themeMode] = v.name }
    suspend fun setAccent(argb: Long) = edit { it[K.accent] = argb }
    suspend fun setTextSize(v: TextSize) = edit { it[K.textSize] = v.name }
    suspend fun setHaptics(v: Boolean) = edit { it[K.haptics] = v }
    suspend fun setHideAmounts(v: Boolean) = edit { it[K.hideAmounts] = v }
    suspend fun setDecimals(v: Int) = edit { it[K.decimals] = v.coerceIn(2, 3) }
    suspend fun setCurrencyLabel(v: CurrencyLabel) = edit { it[K.currencyLabel] = v.name }
    suspend fun setWeekStart(v: DayOfWeek) = edit { it[K.weekStart] = v.name }
    suspend fun setNightlyReminder(on: Boolean) = edit { it[K.nightly] = on }
    suspend fun setNightlyTime(t: java.time.LocalTime) = edit { it[K.nightlyTime] = t.toString() }
    suspend fun setCountReminder(on: Boolean) = edit { it[K.countReminder] = on }
    suspend fun setCountEveryDays(days: Int) = edit { it[K.countEvery] = days.coerceIn(1, 60) }

    /** "Nothing spent today": keeps the streak. Only the last 400 days are kept. */
    suspend fun markNoSpend(epochDay: Long) = edit {
        val set = decodeDays(it[K.noSpend]) + epochDay
        it[K.noSpend] = set.filter { d -> d > epochDay - 400 }.sorted().joinToString(",")
    }

    private fun decodeDays(raw: String?): Set<Long> = raw?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet() ?: emptySet()

    suspend fun setDenominations(list: List<Long>?) = edit {
        if (list == null) it.remove(K.denominations) else it[K.denominations] = com.adampay.app.domain.cash.Denominations.encode(list)
    }
    /** null = automatic (first goal not reached yet), 0 = no card, else that goal. */
    suspend fun setFeaturedGoal(id: Long?) = edit { if (id == null) it.remove(K.featuredGoal) else it[K.featuredGoal] = id }
    suspend fun setDefaultPocket(id: Long?) = edit { if (id == null) it.remove(K.defaultPocket) else it[K.defaultPocket] = id }

    /** Appearance back to the defaults (theme, accent, text size) and every page layout. */
    suspend fun resetAppearance() = edit { p ->
        p.remove(K.themeMode); p.remove(K.accent); p.remove(K.textSize)
        PageId.entries.forEach { p.remove(K.layout(it)) }
    }

    /** Every setting as name → value, for the backup. */
    suspend fun rawSnapshot(): Map<String, Any> = context.settingsStore.data.first().asMap().mapKeys { it.key.name }

    /** Replaces all settings with [values] (a restore). */
    suspend fun restoreRaw(values: Map<String, Any>) = edit { p ->
        p.clear()
        @Suppress("UNCHECKED_CAST")
        values.forEach { (name, v) ->
            when (v) {
                is Boolean -> p[booleanPreferencesKey(name)] = v
                is Int -> p[intPreferencesKey(name)] = v
                is Long -> p[longPreferencesKey(name)] = v
                is Float -> p[androidx.datastore.preferences.core.floatPreferencesKey(name)] = v
                is Double -> p[androidx.datastore.preferences.core.doublePreferencesKey(name)] = v
                is String -> p[stringPreferencesKey(name)] = v
                is Set<*> -> p[androidx.datastore.preferences.core.stringSetPreferencesKey(name)] = v as Set<String>
            }
        }
    }

    suspend fun clearAll() = edit { it.clear() }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsStore.edit { block(it) }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            onboardingDone = this[K.onboardingDone] ?: d.onboardingDone,
            setupOnce = this[K.setupOnce] ?: (this[K.onboardingDone] ?: false),
            userName = this[K.userName] ?: d.userName,
            language = enumOr(this[K.language], d.language),
            themeMode = enumOr(this[K.themeMode], d.themeMode),
            accentArgb = this[K.accent] ?: d.accentArgb,
            textSize = enumOr(this[K.textSize], d.textSize),
            hapticsEnabled = this[K.haptics] ?: d.hapticsEnabled,
            hideAmounts = this[K.hideAmounts] ?: d.hideAmounts,
            decimals = (this[K.decimals] ?: d.decimals).coerceIn(2, 3),
            currencyLabel = enumOr(this[K.currencyLabel], d.currencyLabel),
            weekStart = enumOr(this[K.weekStart], d.weekStart),
            defaultPocketId = this[K.defaultPocket],
            denominations = com.adampay.app.domain.cash.Denominations.decode(this[K.denominations]),
            nightlyReminder = this[K.nightly] ?: d.nightlyReminder,
            nightlyTime = this[K.nightlyTime]?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() } ?: d.nightlyTime,
            countReminder = this[K.countReminder] ?: d.countReminder,
            countEveryDays = this[K.countEvery] ?: d.countEveryDays,
            noSpendDays = decodeDays(this[K.noSpend]),
            featuredGoalId = this[K.featuredGoal],
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
