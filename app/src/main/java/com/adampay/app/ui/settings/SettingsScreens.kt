package com.adampay.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adampay.app.BuildConfig
import com.adampay.app.R
import com.adampay.app.core.locale.LocalAppLanguage
import com.adampay.app.core.locale.bidiJoin
import com.adampay.app.data.db.PocketEntity
import com.adampay.app.data.wallet.WalletSnapshot
import com.adampay.app.data.wallet.name
import com.adampay.app.domain.model.AppLanguage
import com.adampay.app.domain.model.AppSettings
import com.adampay.app.domain.model.TextSize
import com.adampay.app.domain.model.ThemeMode
import com.adampay.app.domain.money.CurrencyLabel
import com.adampay.app.ui.AppState
import com.adampay.app.ui.components.AdamCard
import com.adampay.app.ui.components.AdamTextField
import com.adampay.app.ui.components.Amount
import com.adampay.app.ui.components.AppIcons
import com.adampay.app.ui.components.Segmented
import com.adampay.app.ui.components.SettingsRow
import com.adampay.app.ui.components.Sign
import com.adampay.app.ui.components.SubScreen
import com.adampay.app.ui.components.SwitchRow
import com.adampay.app.ui.components.rememberHaptics
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.components.text
import com.adampay.app.ui.home.PocketCard
import com.adampay.app.ui.main.MainNav
import com.adampay.app.ui.main.Routes
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes
import com.adampay.app.ui.theme.Palette
import com.adampay.app.ui.theme.argb
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Settings changes, routed to the app view model (no-ops in screenshot tests). */
data class SettingsActions(
    val setLanguage: (AppLanguage) -> Unit = {},
    val setThemeMode: (ThemeMode) -> Unit = {},
    val setAccent: (Long) -> Unit = {},
    val setTextSize: (TextSize) -> Unit = {},
    val setHaptics: (Boolean) -> Unit = {},
    val setHideAmounts: (Boolean) -> Unit = {},
    val setDecimals: (Int) -> Unit = {},
    val setCurrencyLabel: (CurrencyLabel) -> Unit = {},
    val setWeekStart: (DayOfWeek) -> Unit = {},
    val setDefaultPocket: (Long?) -> Unit = {},
    val arrangeHome: () -> Unit = {},
    val resetAppearance: () -> Unit = {},
    val setUserName: (String) -> Unit = {},
    val openBudget: () -> Unit = {},
    val restartSetup: () -> Unit = {},
)

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = Adam.type.smallMedium, color = Adam.colors.textMuted, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp))
    AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), content = content)
}

@Composable
fun languageName(l: AppLanguage): String = stringResource(
    when (l) {
        AppLanguage.MIXED -> R.string.lang_mixed
        AppLanguage.ARABIC -> R.string.lang_arabic
        AppLanguage.ENGLISH -> R.string.lang_english
    },
)

/** The Settings tab. */
@Composable
fun SettingsHome(
    state: AppState,
    snapshot: WalletSnapshot,
    nav: MainNav,
    actions: SettingsActions,
    contentPadding: PaddingValues,
) {
    val s = state.settings
    val c = Adam.colors
    var askSetup by remember { mutableStateOf(false) }
    if (askSetup) {
        AlertDialog(
            onDismissRequest = { askSetup = false },
            containerColor = c.surfaceHigh,
            title = { Text(stringResource(R.string.settings_setup_again), style = Adam.type.title, color = c.text) },
            text = { Text(stringResource(R.string.settings_setup_again_confirm), style = Adam.type.body, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { askSetup = false; actions.restartSetup() }) { Text(stringResource(R.string.action_continue), color = c.accentInk) } },
            dismissButton = { TextButton(onClick = { askSetup = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp)
            .padding(contentPadding),
    ) {
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.settings_title), style = Adam.type.h1, color = c.text)

        Group(stringResource(R.string.settings_section_profile)) {
            NameRow(s.userName, actions.setUserName)
            SettingsRow(
                R.drawable.ic_restart_alt, stringResource(R.string.settings_setup_again),
                subtitle = stringResource(R.string.settings_setup_again_sub),
            ) { askSetup = true }
        }

        Group(stringResource(R.string.settings_section_security)) {
            SettingsRow(R.drawable.ic_lock, stringResource(R.string.security_title), subtitle = stringResource(R.string.settings_security_sub)) { nav.open(Routes.SECURITY) }
            SettingsRow(R.drawable.ic_cloud_upload, stringResource(R.string.backup_title), subtitle = stringResource(R.string.settings_backup_sub)) { nav.open(Routes.BACKUP) }
        }

        Group(stringResource(R.string.settings_section_look)) {
            SettingsRow(R.drawable.ic_palette, stringResource(R.string.settings_appearance), subtitle = stringResource(R.string.settings_appearance_sub)) { nav.open(Routes.APPEARANCE) }
            SettingsRow(R.drawable.ic_language, stringResource(R.string.settings_language), subtitle = languageName(s.language)) { nav.open(Routes.LANGUAGE) }
            SwitchRow(R.drawable.ic_bolt, stringResource(R.string.settings_haptics), s.hapticsEnabled, actions.setHaptics, subtitle = stringResource(R.string.settings_haptics_sub))
        }

        Group(stringResource(R.string.settings_section_money)) {
            SettingsRow(
                R.drawable.ic_paid, stringResource(R.string.settings_money_display),
                subtitle = bidiJoin(listOf(stringResource(if (s.decimals == 3) R.string.money_decimals_3 else R.string.money_decimals_2), s.currencyLabel.text)),
            ) { nav.open(Routes.MONEY) }
            DefaultPocketRow(snapshot, s.defaultPocketId, actions.setDefaultPocket)
            SettingsRow(R.drawable.ic_payments, stringResource(R.string.settings_count), subtitle = stringResource(R.string.settings_count_sub)) { nav.open(Routes.COUNT) }
            SettingsRow(R.drawable.ic_toll, stringResource(R.string.denoms_title), subtitle = stringResource(R.string.denoms_sub)) { nav.open(Routes.DENOMS) }
        }

        Group(stringResource(R.string.settings_section_plan)) {
            SettingsRow(R.drawable.ic_flag, stringResource(R.string.budget_title), subtitle = stringResource(R.string.settings_budget_sub)) { actions.openBudget() }
            SettingsRow(R.drawable.ic_savings, stringResource(R.string.goals_title), subtitle = stringResource(R.string.settings_goals_sub)) { nav.open(Routes.GOALS) }
            SettingsRow(R.drawable.ic_notifications, stringResource(R.string.reminders_title), subtitle = stringResource(R.string.settings_reminders_sub)) { nav.open(Routes.REMINDERS) }
        }

        Group(stringResource(R.string.settings_section_lists)) {
            SettingsRow(R.drawable.ic_wallet, stringResource(R.string.pockets_title), subtitle = stringResource(R.string.pockets_sub)) { nav.open(Routes.POCKETS) }
            SettingsRow(R.drawable.ic_category, stringResource(R.string.settings_categories), subtitle = stringResource(R.string.settings_categories_sub)) { nav.open(Routes.CATEGORIES) }
            SettingsRow(R.drawable.ic_bolt, stringResource(R.string.settings_quick), subtitle = stringResource(R.string.settings_quick_sub)) { nav.open(Routes.QUICK) }
        }

        Group(stringResource(R.string.settings_section_access)) {
            SettingsRow(R.drawable.ic_touch_app, stringResource(R.string.access_title), subtitle = stringResource(R.string.access_sub)) { nav.open(Routes.ACCESS) }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = Adam.type.micro, color = c.textMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun NameRow(name: String, onSave: (String) -> Unit) {
    val c = Adam.colors
    var editing by remember { mutableStateOf(false) }
    SettingsRow(R.drawable.ic_person, stringResource(R.string.settings_name), subtitle = name.ifBlank { stringResource(R.string.settings_name_empty) }) { editing = true }
    if (editing) {
        var text by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = c.surfaceHigh,
            title = { Text(stringResource(R.string.settings_name), style = Adam.type.title, color = c.text) },
            text = { AdamTextField(text, { text = it.take(30) }, stringResource(R.string.ob_name_label)) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = { onSave(text); editing = false }) { Text(stringResource(R.string.action_save), color = c.accentInk) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }
}

@Composable
private fun DefaultPocketRow(snapshot: WalletSnapshot, selectedId: Long?, onPick: (Long?) -> Unit) {
    val language = LocalAppLanguage.current
    var open by remember { mutableStateOf(false) }
    val current = snapshot.defaultPocket(selectedId)
    Box {
        SettingsRow(
            AppIcons.of(current?.icon ?: "wallet"), stringResource(R.string.settings_default_pocket),
            subtitle = current?.name?.forTerms(language),
        ) { open = true }
        DropdownMenu(open, { open = false }, containerColor = Adam.colors.surfaceHigh) {
            snapshot.activePockets.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name.forTerms(language)) },
                    leadingIcon = { Icon(painterResource(AppIcons.of(p.icon)), null, tint = Color(p.color), modifier = Modifier.size(20.dp)) },
                    onClick = { onPick(p.id); open = false },
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = Adam.type.smallMedium, color = Adam.colors.textSecondary, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(settings: AppSettings, actions: SettingsActions, onBack: () -> Unit, preview: PocketEntity? = null) {
    val c = Adam.colors
    val haptics = rememberHaptics()
    SubScreen(stringResource(R.string.settings_appearance), onBack) {
        // Live preview: a pocket card and an amount in the chosen look.
        AdamCard(Modifier.fillMaxWidth().padding(top = 6.dp), padding = PaddingValues(16.dp)) {
            Text(stringResource(R.string.appearance_preview), style = Adam.type.micro, color = c.textMuted)
            Spacer(Modifier.height(12.dp))
            PocketCard(preview ?: PreviewPocket, 12_500)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Amount(2_350, sign = Sign.MINUS, color = c.expense, peekable = false)
                Amount(5_000, sign = Sign.PLUS, color = c.income, peekable = false)
            }
        }

        Label(stringResource(R.string.appearance_theme))
        Segmented(
            listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK), settings.themeMode,
            label = {
                stringResource(
                    when (it) {
                        ThemeMode.SYSTEM -> R.string.theme_system
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                    },
                )
            },
            onSelect = actions.setThemeMode,
        )

        Label(stringResource(R.string.appearance_accent))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Palette.Accents.forEach { color ->
                val on = color.argb() == (settings.accentArgb and 0xFFFFFFFFL)
                Box(
                    Modifier.size(46.dp).clip(CircleShape)
                        .border(2.dp, if (on) c.text else Color.Transparent, CircleShape)
                        .padding(5.dp).clip(CircleShape).background(color)
                        .tap(pressedScale = 0.9f) { haptics.tick(); actions.setAccent(color.argb()) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(painterResource(R.drawable.ic_check), null, tint = Color(0xFF17140D), modifier = Modifier.size(20.dp))
                }
            }
        }

        Label(stringResource(R.string.appearance_text))
        Segmented(
            TextSize.entries, settings.textSize,
            label = {
                stringResource(
                    when (it) {
                        TextSize.SMALL -> R.string.text_small
                        TextSize.DEFAULT -> R.string.text_default
                        TextSize.LARGE -> R.string.text_large
                    },
                )
            },
            onSelect = actions.setTextSize,
        )

        Spacer(Modifier.height(18.dp))
        AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            SwitchRow(R.drawable.ic_visibility_off, stringResource(R.string.appearance_hide), settings.hideAmounts, actions.setHideAmounts, subtitle = stringResource(R.string.appearance_hide_sub))
            SettingsRow(R.drawable.ic_dashboard_customize, stringResource(R.string.appearance_arrange), subtitle = stringResource(R.string.appearance_arrange_sub), onClick = actions.arrangeHome)
            SettingsRow(R.drawable.ic_restart_alt, stringResource(R.string.appearance_reset), onClick = { haptics.confirm(); actions.resetAppearance() }, trailing = {})
        }
    }
}

private val PreviewPocket = PocketEntity(
    id = -1, nameAr = "في جيبي", nameEn = "In my pocket", icon = "wallet", color = 0xFF2FB36D, color2 = 0xFF0C4A30,
)

@Composable
fun LanguageScreen(current: AppLanguage, onPick: (AppLanguage) -> Unit, onBack: () -> Unit) {
    val c = Adam.colors
    val haptics = rememberHaptics()
    SubScreen(stringResource(R.string.settings_language), onBack) {
        Spacer(Modifier.height(8.dp))
        AppLanguage.entries.forEach { l ->
            val on = l == current
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(AdamShapes.card)
                    .background(if (on) c.accentSoft else c.surface)
                    .border(1.5.dp, if (on) c.accent else c.border, AdamShapes.card)
                    .tap(pressedScale = 0.98f) { haptics.tick(); onPick(l) }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(languageName(l), style = Adam.type.title, color = c.text)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        stringResource(
                            when (l) {
                                AppLanguage.MIXED -> R.string.lang_mixed_sub
                                AppLanguage.ARABIC -> R.string.lang_arabic_sub
                                AppLanguage.ENGLISH -> R.string.lang_english_sub
                            },
                        ),
                        style = Adam.type.small, color = c.textSecondary,
                    )
                }
                if (on) Icon(painterResource(R.drawable.ic_check_circle_fill), null, tint = c.accentInk, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun MoneyDisplayScreen(settings: AppSettings, actions: SettingsActions, onBack: () -> Unit) {
    val c = Adam.colors
    val language = LocalAppLanguage.current
    val locale: Locale = com.adampay.app.core.locale.AppLocales.ui(language)
    SubScreen(stringResource(R.string.settings_money_display), onBack) {
        AdamCard(Modifier.fillMaxWidth().padding(top = 6.dp), padding = PaddingValues(18.dp)) {
            Text(stringResource(R.string.money_example), style = Adam.type.micro, color = c.textMuted)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Amount(12_500, style = Adam.type.amountLarge, labelStyle = Adam.type.title, labelColor = c.accentInk, peekable = false)
                Spacer(Modifier.width(18.dp))
                Amount(1_355, sign = Sign.MINUS, color = c.expense, peekable = false)
            }
        }

        Label(stringResource(R.string.money_decimals))
        Segmented(
            listOf(3, 2), settings.decimals,
            label = { stringResource(if (it == 3) R.string.money_decimals_3 else R.string.money_decimals_2) },
            onSelect = actions.setDecimals,
        )
        Text(stringResource(R.string.money_decimals_note), style = Adam.type.small, color = c.textMuted, modifier = Modifier.padding(top = 8.dp, start = 4.dp))

        Label(stringResource(R.string.money_label))
        Segmented(CurrencyLabel.entries, settings.currencyLabel, label = { it.text }, onSelect = actions.setCurrencyLabel)

        Label(stringResource(R.string.money_week_start))
        Segmented(
            listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY), settings.weekStart,
            label = { it.getDisplayName(TextStyle.FULL, locale) },
            onSelect = actions.setWeekStart,
        )
    }
}
