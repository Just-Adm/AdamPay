package com.adampay.app.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adampay.app.R
import com.adampay.app.core.backup.BackupWork
import com.adampay.app.core.security.Biometrics
import com.adampay.app.domain.account.AccountState
import com.adampay.app.domain.security.LockTimeout
import com.adampay.app.domain.security.SecurityConfig
import com.adampay.app.ui.components.AdamCard
import com.adampay.app.ui.components.IconBadge
import com.adampay.app.ui.components.PrimaryButton
import com.adampay.app.ui.components.Segmented
import com.adampay.app.ui.components.SettingsRow
import com.adampay.app.ui.components.SubScreen
import com.adampay.app.ui.components.SwitchRow
import com.adampay.app.ui.components.dayText
import com.adampay.app.ui.components.timeText
import com.adampay.app.ui.components.rememberHaptics
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes
import java.time.Instant
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = Adam.type.smallMedium, color = Adam.colors.textMuted, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
private fun RowsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), content = content)
}

/** What Settings → App lock can change. */
data class SecurityActions(
    val turnOn: () -> Unit = {},
    val turnOff: () -> Unit = {},
    val changePin: () -> Unit = {},
    val setBiometric: (Boolean) -> Unit = {},
    val setTimeout: (LockTimeout) -> Unit = {},
    val setSecureScreen: (Boolean) -> Unit = {},
    val setHideAmounts: (Boolean) -> Unit = {},
)

/** Settings → App lock. */
@Composable
fun SecurityScreen(
    config: SecurityConfig,
    hideAmounts: Boolean,
    actions: SecurityActions,
    onBack: () -> Unit,
    biometricAvailable: Boolean = Biometrics.available(LocalContext.current),
    message: String? = null,
    onMessageShown: () -> Unit = {},
) {
    val c = Adam.colors
    val context = LocalContext.current
    val confirmTitle = stringResource(R.string.biometric_confirm)
    val cancel = stringResource(R.string.action_cancel)
    val snackbar = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(message) {
        // Shown from the screen's own scope, so clearing the message doesn't cancel the snackbar.
        if (message != null) { scope.launch { snackbar.showSnackbar(message) }; onMessageShown() }
    }
    Box(Modifier.fillMaxSize()) {
    SubScreen(stringResource(R.string.security_title), onBack) {
        Spacer(Modifier.height(6.dp))
        RowsCard {
            SwitchRow(
                R.drawable.ic_lock, stringResource(R.string.security_lock), config.locks,
                { on -> if (on) actions.turnOn() else actions.turnOff() },
                subtitle = stringResource(R.string.security_lock_sub),
            )
            if (config.locks) {
                SettingsRow(
                    R.drawable.ic_fingerprint, stringResource(R.string.security_biometric),
                    subtitle = stringResource(if (biometricAvailable) R.string.security_biometric_sub else R.string.security_biometric_none),
                    trailing = {
                        com.adampay.app.ui.components.AdamSwitch(config.biometric && biometricAvailable) { on ->
                            if (!on) actions.setBiometric(false)
                            // Turning it on asks for the finger once, so it's known to work.
                            else if (biometricAvailable) Biometrics.prompt(context, confirmTitle, cancel) { ok -> if (ok) actions.setBiometric(true) }
                        }
                    },
                )
                SettingsRow(R.drawable.ic_pin, stringResource(R.string.security_change_pin), onClick = actions.changePin)
            }
        }
        if (config.locks) {
            SectionLabel(stringResource(R.string.security_timeout))
            Segmented(
                LockTimeout.entries, config.timeout,
                label = {
                    stringResource(
                        when (it) {
                            LockTimeout.IMMEDIATELY -> R.string.timeout_immediately
                            LockTimeout.ONE_MINUTE -> R.string.timeout_1
                            LockTimeout.FIVE_MINUTES -> R.string.timeout_5
                        },
                    )
                },
                onSelect = actions.setTimeout,
            )
        }
        SectionLabel(stringResource(R.string.security_privacy))
        RowsCard {
            SwitchRow(R.drawable.ic_hide_source, stringResource(R.string.security_secure), config.secureScreen, actions.setSecureScreen, subtitle = stringResource(R.string.security_secure_sub))
            SwitchRow(R.drawable.ic_visibility_off, stringResource(R.string.appearance_hide), hideAmounts, actions.setHideAmounts, subtitle = stringResource(R.string.appearance_hide_sub))
        }
        Text(
            stringResource(R.string.security_note), style = Adam.type.small, color = c.textMuted,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 14.dp),
        )
    }
    SnackbarHost(
        snackbar,
        Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
    ) { data -> Snackbar(data, shape = AdamShapes.tile, containerColor = c.surfaceHigh, contentColor = c.text, actionColor = c.accentInk) }
    }
}

/* ------------------------------------------------------------------ Backup */

/** What Settings → Backup can do. */
data class BackupActions(
    val signIn: () -> Unit = {},
    val signOut: () -> Unit = {},
    val backupNow: () -> Unit = {},
    val pickBackup: () -> Unit = {},
    val restore: (Instant) -> Unit = {},
    val setAuto: (Boolean) -> Unit = {},
)

@Composable
fun backupWhen(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    stringResource(R.string.date_time, dayText(at.atZone(zone).toLocalDate(), LocalDate.now(zone)), timeText(at.toEpochMilli(), zone))

private fun sizeText(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(java.util.Locale.ROOT, bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(java.util.Locale.ROOT, bytes / 1024.0 / 1024)
    else -> "%.2f GB".format(java.util.Locale.ROOT, bytes / 1024.0 / 1024 / 1024)
}

@Composable
fun backupErrorText(code: String): String = stringResource(
    when (code) {
        "SETUP" -> R.string.backup_err_setup
        "SIGN_IN" -> R.string.backup_err_sign_in
        "OFFLINE" -> R.string.backup_err_offline
        else -> R.string.backup_err_failed
    },
)

/** Settings → Backup. [pick] is the list of backups to choose from while the restore dialog is open. */
@Composable
fun BackupScreen(
    account: AccountState,
    signingIn: Boolean,
    running: BackupWork?,
    actions: BackupActions,
    onBack: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    pick: List<Instant>? = null,
    found: List<Instant>? = null,
    onDismissPick: () -> Unit = {},
) {
    val c = Adam.colors
    val haptics = rememberHaptics()
    var askSignOut by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        SubScreen(stringResource(R.string.backup_title), onBack) {
            Spacer(Modifier.height(6.dp))
            val google = account.google
            if (google == null) {
                AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
                    IconBadge(R.drawable.ic_cloud_upload, size = 52.dp, iconSize = 26.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.backup_title), style = Adam.type.title, color = c.text)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.backup_intro), style = Adam.type.body, color = c.textSecondary)
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(
                        stringResource(if (signingIn) R.string.backup_signing_in else R.string.backup_sign_in),
                        actions.signIn, enabled = !signingIn, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    stringResource(R.string.backup_includes), style = Adam.type.small, color = c.textMuted,
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 14.dp),
                )
                return@SubScreen
            }

            // Account
            AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                        Text(google.name.take(1).ifBlank { "G" }.uppercase(), style = Adam.type.title, color = c.accentInk)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(google.name, style = Adam.type.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(google.email, style = Adam.type.small, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { askSignOut = true }) { Text(stringResource(R.string.backup_sign_out), color = c.textSecondary, style = Adam.type.smallMedium) }
                }
            }

            // Status
            Spacer(Modifier.height(12.dp))
            AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                val s = account.status
                StatusLine(stringResource(R.string.backup_last), s.lastAt?.let { backupWhen(it) } ?: stringResource(R.string.backup_never))
                if (s.backups > 0) {
                    Spacer(Modifier.height(10.dp))
                    StatusLine(
                        stringResource(R.string.backup_used),
                        com.adampay.app.core.locale.bidiJoin(listOf(com.adampay.app.core.locale.Bidi.ltr(sizeText(s.usedBytes)), pluralStringResource(R.plurals.backup_count, s.backups, s.backups))),
                    )
                }
                if (s.lastError != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(AdamShapes.tile).background(c.expense.copy(alpha = 0.12f)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_error), null, tint = c.expense, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.backup_last_failed), style = Adam.type.smallMedium, color = c.text)
                            Text(backupErrorText(s.lastError), style = Adam.type.small, color = c.textSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                val backingUp = running == BackupWork.BACKING_UP
                PrimaryButton(
                    stringResource(if (backingUp) R.string.backup_running else R.string.backup_now),
                    { haptics.click(); actions.backupNow() }, enabled = running == null, icon = R.drawable.ic_cloud_upload,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionLabel(stringResource(R.string.backup_options))
            RowsCard {
                SwitchRow(R.drawable.ic_schedule, stringResource(R.string.backup_auto), account.autoBackup, actions.setAuto, subtitle = stringResource(R.string.backup_auto_sub))
                SettingsRow(
                    R.drawable.ic_settings_backup_restore,
                    stringResource(if (running == BackupWork.RESTORING) R.string.backup_restoring else R.string.backup_restore),
                    subtitle = stringResource(R.string.backup_restore_sub),
                    trailing = if (running == BackupWork.RESTORING) ({ CircularProgressIndicator(Modifier.size(20.dp), color = c.accent, strokeWidth = 2.dp) }) else null,
                    onClick = if (running == null) actions.pickBackup else null,
                )
            }
            Text(
                stringResource(R.string.backup_includes), style = Adam.type.small, color = c.textMuted,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 14.dp),
            )
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
        ) { data -> Snackbar(data, shape = AdamShapes.tile, containerColor = c.surfaceHigh, contentColor = c.text, actionColor = c.accentInk) }
    }

    if (askSignOut) {
        AlertDialog(
            onDismissRequest = { askSignOut = false },
            containerColor = c.surfaceHigh,
            title = { Text(stringResource(R.string.backup_sign_out_title), style = Adam.type.title, color = c.text) },
            text = { Text(stringResource(R.string.backup_sign_out_body), style = Adam.type.body, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { askSignOut = false; actions.signOut() }) { Text(stringResource(R.string.backup_sign_out), color = c.expense) } },
            dismissButton = { TextButton(onClick = { askSignOut = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }
    if (pick != null) RestoreDialog(pick, actions.restore, onDismissPick)
    if (found != null && found.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissPick,
            containerColor = c.surfaceHigh,
            title = { Text(stringResource(R.string.backup_found_title), style = Adam.type.title, color = c.text) },
            text = {
                Column {
                    Text(stringResource(R.string.backup_found_body, backupWhen(found.first())), style = Adam.type.body, color = c.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.backup_restore_warning), style = Adam.type.small, color = c.textMuted)
                }
            },
            confirmButton = { TextButton(onClick = { onDismissPick(); actions.restore(found.first()) }) { Text(stringResource(R.string.backup_restore_confirm), color = c.accentInk) } },
            dismissButton = { TextButton(onClick = onDismissPick) { Text(stringResource(R.string.backup_not_now), color = c.textSecondary) } },
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Text(label, style = Adam.type.micro, color = Adam.colors.textMuted)
    Spacer(Modifier.height(2.dp))
    Text(value, style = Adam.type.bodyMedium, color = Adam.colors.text)
}

/** Pick which backup to restore (newest first), with the warning that it replaces everything. */
@Composable
private fun RestoreDialog(backups: List<Instant>, onRestore: (Instant) -> Unit, onDismiss: () -> Unit) {
    val c = Adam.colors
    var chosen by remember(backups) { mutableStateOf(backups.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text(stringResource(R.string.backup_pick_title), style = Adam.type.title, color = c.text) },
        text = {
            Column {
                // Long lists scroll inside the dialog instead of pushing the buttons away.
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    backups.take(30).forEachIndexed { i, at ->
                        val on = at == chosen
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(AdamShapes.tile)
                                .background(if (on) c.accentSoft else c.surfaceAlt.copy(alpha = 0.5f))
                                .border(1.dp, if (on) c.accent else c.border, AdamShapes.tile)
                                .tap(press = false) { chosen = at }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(backupWhen(at), style = Adam.type.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
                            if (i == 0) Text(stringResource(R.string.backup_latest), style = Adam.type.micro, color = c.accentInk)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.backup_restore_warning), style = Adam.type.small, color = c.expense)
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onRestore(chosen) }) { Text(stringResource(R.string.backup_restore_confirm), color = c.accentInk) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
    )
}
