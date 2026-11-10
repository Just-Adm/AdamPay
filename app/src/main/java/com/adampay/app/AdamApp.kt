package com.adampay.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.adampay.app.data.wallet.WalletRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AdamApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var wallet: WalletRepository
    @Inject lateinit var settings: com.adampay.app.data.settings.SettingsRepository
    @Inject lateinit var reminders: com.adampay.app.core.reminders.Reminders
    @Inject lateinit var accounts: com.adampay.app.data.account.AccountStore
    @Inject lateinit var lock: com.adampay.app.core.security.AppLock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Default pockets, categories and quick items on first run (idempotent).
        scope.launch { runCatching { wallet.ensureSeeded() } }
        // Re-arm the reminders whenever their settings change (and once at start).
        scope.launch {
            settings.settings
                .map { listOf(it.nightlyReminder, it.nightlyTime, it.countReminder, it.countEveryDays, it.language) to it }
                .distinctUntilChangedBy { it.first }
                .collect { (_, s) ->
                    runCatching {
                        reminders.ensureChannels(com.adampay.app.core.locale.Localizer(this@AdamApp, s.language))
                        reminders.schedule(s)
                    }
                }
        }
        watchWidgets()
        // Nightly Drive backup only while a Google account is linked and it's switched on.
        scope.launch {
            accounts.state
                .map { it.google != null && it.autoBackup }
                .distinctUntilChanged()
                .collect { on ->
                    runCatching {
                        if (on) com.adampay.app.core.backup.BackupWorker.schedule(this@AdamApp)
                        else com.adampay.app.core.backup.BackupWorker.cancel(this@AdamApp)
                    }
                }
        }
    }

    /** Home-screen widgets follow every change to the wallet, the settings and the app lock. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun watchWidgets() {
        scope.launch {
            kotlinx.coroutines.flow.combine(wallet.snapshot, wallet.txns, wallet.goals, settings.settings, lock.config) { a, b, c, d, e -> listOf(a, b, c, d, e) }
                .distinctUntilChanged()
                .debounce(400)
                .collect { runCatching { com.adampay.app.ui.widgets.Widgets.refresh(this@AdamApp) } }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
