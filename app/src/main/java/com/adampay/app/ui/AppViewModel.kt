package com.adampay.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adampay.app.data.settings.SettingsRepository
import com.adampay.app.data.wallet.WalletRepository
import com.adampay.app.domain.layout.PageId
import com.adampay.app.domain.layout.PageLayout
import com.adampay.app.domain.model.AppLanguage
import com.adampay.app.domain.model.AppSettings
import com.adampay.app.domain.model.TextSize
import com.adampay.app.domain.model.ThemeMode
import com.adampay.app.domain.money.CurrencyLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

/** Settings, layouts and the app lock for the whole UI. `null` until all are read (the splash waits for it). */
data class AppState(
    val settings: AppSettings,
    val layouts: Map<PageId, PageLayout>,
    val security: com.adampay.app.domain.security.SecurityConfig = com.adampay.app.domain.security.SecurityConfig(),
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val wallet: WalletRepository,
    private val lock: com.adampay.app.core.security.AppLock,
) : ViewModel() {

    val state: StateFlow<AppState?> = combine(settings.settings, settings.layouts, lock.config) { s, l, sec -> AppState(s, l, sec) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /* App lock (Settings → App lock and the lock screen). */
    val locked: StateFlow<Boolean> = lock.locked
    suspend fun checkPin(pin: String): com.adampay.app.domain.security.PinCheck = lock.check(pin)
    fun biometricUnlocked() = launch { lock.biometricUnlocked() }
    suspend fun setPin(pin: String) = lock.setPin(pin)
    suspend fun disableLock() = lock.disable()
    fun setBiometric(on: Boolean) = launch { lock.setBiometric(on) }
    fun setLockTimeout(t: com.adampay.app.domain.security.LockTimeout) = launch { lock.setTimeout(t) }
    fun setSecureScreen(on: Boolean) = launch { lock.setSecureScreen(on) }
    /** Before showing Google's own sign-in screen: returning from it doesn't ask for the PIN. */
    fun expectReturn() = lock.expectReturn()

    /** A screen requested by a notification ("count"), consumed once by the UI. */
    private val _openRequest = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val openRequest: kotlinx.coroutines.flow.StateFlow<String?> = _openRequest
    fun open(screen: String?) { if (screen != null) _openRequest.value = screen }
    fun consumeOpen() { _openRequest.value = null }

    fun setNightlyReminder(on: Boolean) = launch { settings.setNightlyReminder(on) }
    fun setNightlyTime(t: java.time.LocalTime) = launch { settings.setNightlyTime(t) }
    fun setCountReminder(on: Boolean) = launch { settings.setCountReminder(on) }
    fun setCountEveryDays(days: Int) = launch { settings.setCountEveryDays(days) }

    fun setLanguage(v: AppLanguage) = launch { settings.setLanguage(v) }
    fun setThemeMode(v: ThemeMode) = launch { settings.setThemeMode(v) }
    fun setAccent(argb: Long) = launch { settings.setAccent(argb) }
    fun setTextSize(v: TextSize) = launch { settings.setTextSize(v) }
    fun setHaptics(v: Boolean) = launch { settings.setHaptics(v) }
    fun setHideAmounts(v: Boolean) = launch { settings.setHideAmounts(v) }
    fun setDecimals(v: Int) = launch { settings.setDecimals(v) }
    fun setCurrencyLabel(v: CurrencyLabel) = launch { settings.setCurrencyLabel(v) }
    fun setWeekStart(v: DayOfWeek) = launch { settings.setWeekStart(v) }
    fun setDefaultPocket(id: Long?) = launch { settings.setDefaultPocket(id) }
    fun setLayout(layout: PageLayout) = launch { settings.setLayout(layout) }
    fun resetLayout(page: PageId) = launch { settings.resetLayout(page) }
    fun resetAppearance() = launch { settings.resetAppearance() }

    /** null = back to the default notes and coins. */
    fun setDenominations(v: List<Long>?) = launch { settings.setDenominations(v) }
    fun setUserName(v: String) = launch { settings.setUserName(v) }

    /** Settings → "Run setup again": back to the setup screens with the current answers filled in. */
    fun restartSetup() = launch { settings.setOnboardingDone(false) }

    /** Leaves a re-run of setup without changing anything. */
    fun cancelSetup() = launch { settings.setOnboardingDone(true) }

    /** Opening balance per pocket, to fill in the setup screen when it runs again. */
    suspend fun openingBalances(): Map<Long, Long> = wallet.openingBalances()

    /** Setup finished: name, opening balances (replacing earlier ones), then Home. */
    fun finishOnboarding(name: String, opening: Map<Long, Long>) = launch {
        wallet.ensureSeeded()
        settings.setUserName(name)
        wallet.setOpeningBalances(opening)
        settings.setOnboardingDone(true)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
