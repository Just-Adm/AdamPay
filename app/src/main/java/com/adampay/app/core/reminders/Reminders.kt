package com.adampay.app.core.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.adampay.app.HomeActivity
import com.adampay.app.R
import com.adampay.app.core.locale.Localizer
import com.adampay.app.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evening "what did you spend today?" (inline reply) and the optional cash-count reminder.
 * Alarms are inexact (a 10-minute window) so no exact-alarm permission is needed; they are
 * re-armed after each firing, on boot, on time / time-zone changes and whenever settings change.
 */
@Singleton
class Reminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannels(l: Localizer) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DAILY, l.string(R.string.channel_daily), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_COUNT, l.string(R.string.channel_count), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Arms (or cancels) both alarms from the current settings. */
    fun schedule(s: AppSettings) {
        val now = LocalDateTime.now(clock)
        if (s.nightlyReminder) arm(ACTION_NIGHTLY, next(now, s.nightlyTime)) else cancel(ACTION_NIGHTLY)
        if (s.countReminder) arm(ACTION_COUNT_CHECK, next(now, COUNT_CHECK_TIME)) else cancel(ACTION_COUNT_CHECK)
    }

    private fun arm(action: String, at: LocalDateTime) {
        val millis = at.atZone(clock.zone).toInstant().toEpochMilli()
        alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, WINDOW_MS, pending(action))
    }

    private fun cancel(action: String) = alarms.cancel(pending(action))

    private fun pending(action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, ReminderReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The nightly notification: reply with an amount, or "nothing spent today". */
    fun showNightly(l: Localizer, spentToday: String?, error: Boolean = false) {
        if (!canNotify()) return
        ensureChannels(l)
        val reply = RemoteInput.Builder(KEY_AMOUNT).setLabel(l.string(R.string.notif_amount_hint)).build()
        val replyIntent = PendingIntent.getBroadcast(
            context, 11, Intent(context, ReminderReceiver::class.java).setAction(ACTION_REPLY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val noSpend = PendingIntent.getBroadcast(
            context, 12, Intent(context, ReminderReceiver::class.java).setAction(ACTION_NO_SPEND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_DAILY)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle(l.string(if (error) R.string.notif_reply_error else R.string.notif_nightly_title))
            .setContentText(
                when {
                    error -> l.string(R.string.notif_reply_error_body)
                    spentToday != null -> l.string(R.string.notif_nightly_logged, spentToday)
                    else -> l.string(R.string.notif_nightly_body)
                },
            )
            .setOnlyAlertOnce(error)
            .setContentIntent(openApp(null))
            .setAutoCancel(true)
            .addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_add, l.string(R.string.notif_reply), replyIntent)
                    .addRemoteInput(reply).setAllowGeneratedReplies(false).build(),
            )
            .addAction(NotificationCompat.Action.Builder(R.drawable.ic_check, l.string(R.string.notif_no_spend), noSpend).build())
            .build()
        NotificationManagerCompat.from(context).notify(ID_NIGHTLY, n)
    }

    /** After an inline reply: confirm, and offer to pick the category. */
    fun showSaved(l: Localizer, amount: String, txnId: Long) {
        if (!canNotify()) return
        val pick = PendingIntent.getActivity(
            context, 13, Intent(context, QuickCategoryActivity::class.java).putExtra(QuickCategoryActivity.EXTRA_TXN, txnId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_DAILY)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle(l.string(R.string.notif_saved_title, amount))
            .setContentText(l.string(R.string.notif_saved_body))
            .setContentIntent(pick)
            .setAutoCancel(true)
            .setTimeoutAfter(6 * 60 * 60 * 1000L)
            .addAction(NotificationCompat.Action.Builder(R.drawable.ic_category, l.string(R.string.notif_pick_category), pick).build())
            .build()
        NotificationManagerCompat.from(context).notify(ID_NIGHTLY, n)
    }

    fun clearNightly() = NotificationManagerCompat.from(context).cancel(ID_NIGHTLY)

    fun showCount(l: Localizer, days: Int) {
        if (!canNotify()) return
        ensureChannels(l)
        val n = NotificationCompat.Builder(context, CH_COUNT)
            .setSmallIcon(R.drawable.ic_payments)
            .setContentTitle(l.string(R.string.notif_count_title))
            .setContentText(l.string(R.string.notif_count_body, days))
            .setContentIntent(openApp(HomeActivity.OPEN_COUNT))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_COUNT, n)
    }

    private fun openApp(open: String?): PendingIntent = PendingIntent.getActivity(
        context, (open ?: "home").hashCode(),
        Intent(context, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (open != null) putExtra(HomeActivity.EXTRA_OPEN, open)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CH_DAILY = "daily"
        const val CH_COUNT = "count"
        const val ACTION_NIGHTLY = "com.adampay.app.NIGHTLY"
        const val ACTION_COUNT_CHECK = "com.adampay.app.COUNT_CHECK"
        const val ACTION_REPLY = "com.adampay.app.REPLY"
        const val ACTION_NO_SPEND = "com.adampay.app.NO_SPEND"
        const val KEY_AMOUNT = "amount"
        const val ID_NIGHTLY = 100
        const val ID_COUNT = 102
        private const val WINDOW_MS = 10 * 60 * 1000L
        val COUNT_CHECK_TIME: LocalTime = LocalTime.of(19, 0)

        /** The next occurrence of [time] after [now] (today if still ahead, otherwise tomorrow). */
        fun next(now: LocalDateTime, time: LocalTime): LocalDateTime {
            val today = now.toLocalDate().atTime(time)
            return if (today.isAfter(now)) today else today.plusDays(1)
        }

        /** Is a cash count due? Last count of any pocket (or the first use when never counted) is [days] old. */
        fun countDue(lastCount: LocalDate?, firstUse: LocalDate?, today: LocalDate, days: Int): Boolean {
            val since = lastCount ?: firstUse ?: return false
            return !since.plusDays(days.toLong()).isAfter(today)
        }
    }
}
