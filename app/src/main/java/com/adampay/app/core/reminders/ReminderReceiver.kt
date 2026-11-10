package com.adampay.app.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.adampay.app.core.locale.Localizer
import com.adampay.app.data.db.TxnSource
import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.db.TxnType
import com.adampay.app.data.settings.SettingsRepository
import com.adampay.app.data.wallet.WalletRepository
import com.adampay.app.domain.money.Money
import com.adampay.app.domain.wallet.Ledger
import com.adampay.app.ui.components.text
import com.adampay.app.core.locale.Bidi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Fires the reminders and handles the notification actions (inline amount, "nothing spent"), then re-arms. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var reminders: Reminders
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var wallet: WalletRepository
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(context, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val s = settings.current()
        val l = Localizer(context, s.language)
        val zone = clock.zone
        val today = LocalDate.now(clock)
        when (intent.action) {
            Reminders.ACTION_NIGHTLY -> {
                if (s.nightlyReminder && s.onboardingDone) {
                    val start = Ledger.startOfDay(today, zone)
                    val spent = Ledger.spent(wallet.txns.first().filter { it.timestamp >= start })
                    reminders.showNightly(l, if (spent > 0) Bidi.strip(s.moneyFormat.text(spent)) else null)
                }
                reminders.schedule(s)
            }
            Reminders.ACTION_COUNT_CHECK -> {
                if (s.countReminder && s.onboardingDone) {
                    val last = wallet.latestCounts.first().values.maxOfOrNull { it.timestamp }?.let { Ledger.date(it, zone) }
                    val firstUse = wallet.txns.first().minOfOrNull { it.createdAt }?.let { Ledger.date(it, zone) }
                    if (Reminders.countDue(last, firstUse, today, s.countEveryDays)) reminders.showCount(l, s.countEveryDays)
                }
                reminders.schedule(s)
            }
            Reminders.ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Reminders.KEY_AMOUNT)?.toString().orEmpty()
                val fils = Money.parse(text)
                val snapshot = wallet.snapshot.first()
                val pocket = snapshot.defaultPocket(s.defaultPocketId)
                if (fils == null || fils <= 0 || pocket == null) {
                    reminders.showNightly(l, null, error = true)
                    return
                }
                val other = snapshot.categories.firstOrNull { it.seedKey == "other" && !it.archived }?.id
                val id = wallet.save(
                    TxnEntity(
                        type = TxnType.EXPENSE, amountFils = fils, categoryId = other, fromPocketId = pocket.id,
                        timestamp = clock.millis(), createdBy = TxnSource.NOTIFICATION,
                    ),
                )
                reminders.showSaved(l, Bidi.strip(s.moneyFormat.text(fils)), id)
            }
            Reminders.ACTION_NO_SPEND -> {
                settings.markNoSpend(today.toEpochDay())
                reminders.clearNightly()
            }
        }
    }
}

/** Re-arms the reminders after a reboot, an app update or a clock / time-zone change (system broadcasts only). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var reminders: Reminders
    @Inject lateinit var settings: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SYSTEM_ACTIONS) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { reminders.schedule(settings.current()) } finally { pending.finish() }
        }
    }

    private companion object {
        val SYSTEM_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
