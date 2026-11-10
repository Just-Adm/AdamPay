package com.adampay.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adampay.app.R
import com.adampay.app.core.locale.AppLocales
import com.adampay.app.core.locale.LocalAppLanguage
import com.adampay.app.domain.model.AppSettings
import com.adampay.app.ui.components.AdamCard
import com.adampay.app.ui.components.PrimaryButton
import com.adampay.app.ui.components.RoundIconButton
import com.adampay.app.ui.components.SettingsRow
import com.adampay.app.ui.components.SubScreen
import com.adampay.app.ui.components.SwitchRow
import com.adampay.app.ui.theme.Adam
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class ReminderActions(
    val setNightly: (Boolean) -> Unit = {},
    val setNightlyTime: (LocalTime) -> Unit = {},
    val setCount: (Boolean) -> Unit = {},
    val setCountDays: (Int) -> Unit = {},
)

/** Settings → Reminders: the evening question, the cash-count reminder, notification permission. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(s: AppSettings, actions: ReminderActions, onBack: () -> Unit, notificationsAllowed: Boolean? = null) {
    val c = Adam.colors
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    var allowed by remember {
        mutableStateOf(notificationsAllowed ?: (Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED))
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> allowed = granted }
    var pickTime by remember { mutableStateOf(false) }
    val locale = AppLocales.ui(LocalAppLanguage.current)
    val timeText = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(s.nightlyTime)

    SubScreen(stringResource(R.string.reminders_title), onBack) {
        if (!allowed) {
            AdamCard(Modifier.fillMaxWidth().padding(top = 6.dp), color = c.warnSoft, padding = PaddingValues(16.dp)) {
                Text(stringResource(R.string.reminders_permission_title), style = Adam.type.title, color = c.text)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.reminders_permission_body), style = Adam.type.small, color = c.textSecondary)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.reminders_allow), {
                    if (Build.VERSION.SDK_INT >= 33 && !inspection) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else if (!inspection) context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }, Modifier.fillMaxWidth())
            }
        }

        Text(stringResource(R.string.reminders_nightly_section), style = Adam.type.smallMedium, color = c.textMuted, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp))
        AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            SwitchRow(R.drawable.ic_notifications, stringResource(R.string.reminders_nightly), s.nightlyReminder, actions.setNightly, subtitle = stringResource(R.string.reminders_nightly_sub))
            if (s.nightlyReminder) SettingsRow(R.drawable.ic_schedule, stringResource(R.string.reminders_time), subtitle = timeText) { pickTime = true }
        }

        Text(stringResource(R.string.reminders_count_section), style = Adam.type.smallMedium, color = c.textMuted, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp))
        AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            SwitchRow(R.drawable.ic_payments, stringResource(R.string.reminders_count), s.countReminder, actions.setCount, subtitle = stringResource(R.string.reminders_count_sub))
            if (s.countReminder) {
                SettingsRow(
                    R.drawable.ic_calendar_month, stringResource(R.string.reminders_count_every),
                    subtitle = androidx.compose.ui.res.pluralStringResource(R.plurals.days_count, s.countEveryDays, s.countEveryDays),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RoundIconButton(R.drawable.ic_remove, null, { actions.setCountDays((s.countEveryDays - 1).coerceAtLeast(1)) }, size = 34.dp)
                            Spacer(Modifier.width(6.dp))
                            RoundIconButton(R.drawable.ic_add, null, { actions.setCountDays((s.countEveryDays + 1).coerceAtMost(60)) }, size = 34.dp)
                        }
                    },
                )
            }
        }

        Text(stringResource(R.string.reminders_magicos), style = Adam.type.small, color = c.textMuted, modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 18.dp))
    }

    if (pickTime) {
        val state = rememberTimePickerState(s.nightlyTime.hour, s.nightlyTime.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = c.surfaceHigh,
            title = { Text(stringResource(R.string.reminders_time), style = Adam.type.title, color = c.text) },
            text = {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = c.surfaceAlt, selectorColor = c.accent, timeSelectorSelectedContainerColor = c.accentSoft,
                        timeSelectorUnselectedContainerColor = c.surfaceAlt, periodSelectorSelectedContainerColor = c.accentSoft,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = { actions.setNightlyTime(LocalTime.of(state.hour, state.minute)); pickTime = false }) { Text(stringResource(R.string.action_ok), color = c.accentInk) } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }
}
