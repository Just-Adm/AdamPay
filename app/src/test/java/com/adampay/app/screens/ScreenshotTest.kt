package com.adampay.app.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adampay.app.core.locale.ProvideAppLanguage
import com.adampay.app.data.db.CategoryEntity
import com.adampay.app.data.db.PocketEntity
import com.adampay.app.data.db.QuickItemEntity
import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.db.TxnType
import com.adampay.app.data.wallet.Seeds
import com.adampay.app.data.wallet.WalletSnapshot
import com.adampay.app.domain.layout.BoxId
import com.adampay.app.domain.layout.PageId
import com.adampay.app.domain.layout.PageLayout
import com.adampay.app.domain.model.AppLanguage
import com.adampay.app.domain.model.AppSettings
import com.adampay.app.domain.model.ThemeMode
import com.adampay.app.domain.wallet.HistoryFilter
import com.adampay.app.domain.wallet.Ledger
import com.adampay.app.ui.AppState
import com.adampay.app.ui.WalletUi
import com.adampay.app.ui.add.AddRequest
import com.adampay.app.ui.add.AddSheetContent
import com.adampay.app.ui.main.MainNav
import com.adampay.app.ui.main.MainScreen
import com.adampay.app.ui.main.Tab
import com.adampay.app.ui.onboarding.OnboardingScreen
import com.adampay.app.ui.settings.AppearanceScreen
import com.adampay.app.ui.settings.CategoriesScreen
import com.adampay.app.ui.settings.CategoryEditor
import com.adampay.app.ui.settings.LanguageScreen
import com.adampay.app.ui.settings.MoneyDisplayScreen
import com.adampay.app.ui.settings.QuickItemEditor
import com.adampay.app.ui.settings.QuickItemsScreen
import com.adampay.app.ui.settings.SettingsActions
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes
import com.adampay.app.ui.theme.AdamTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Renders every main screen to app/screenshots/ (Roborazzi + Robolectric native graphics) at the
 * X7e's size (720×1604 px ≈ 360×802 dp), dark and light, Arabic and English.
 * Run: ./gradlew :app:recordRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h802dp-xhdpi", application = Application::class)
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 10, 17)
    private fun at(daysAgo: Long, h: Int, m: Int) = LocalDateTime.of(today.minusDays(daysAgo), java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
    private val now = at(0, 18, 30)

    private val pockets = Seeds.pockets.mapIndexed { i, p -> p.copy(id = i + 1L) }
    private val categories = Seeds.categories.mapIndexed { i, c -> c.copy(id = 100L + i) }
    private fun cat(key: String) = categories.first { it.seedKey == key }.id
    private val quick = Seeds.quickItems(categories).mapIndexed { i, q -> q.copy(id = 200L + i) }

    private val txns = listOf(
        TxnEntity(id = 1, type = TxnType.OPENING, amountFils = 18_000, toPocketId = 1, timestamp = at(6, 9, 0)),
        TxnEntity(id = 2, type = TxnType.OPENING, amountFils = 45_000, toPocketId = 2, timestamp = at(6, 9, 0)),
        TxnEntity(id = 3, type = TxnType.OPENING, amountFils = 12_250, toPocketId = 3, timestamp = at(6, 9, 0)),
        TxnEntity(id = 10, type = TxnType.EXPENSE, amountFils = 350, categoryId = cat("transport"), fromPocketId = 1, note = "سرفيس", timestamp = at(0, 8, 10)),
        TxnEntity(id = 11, type = TxnType.EXPENSE, amountFils = 1_500, categoryId = cat("food"), fromPocketId = 1, note = "شاورما", timestamp = at(0, 13, 20)),
        TxnEntity(id = 12, type = TxnType.INCOME, amountFils = 5_000, categoryId = cat("family"), toPocketId = 1, note = "من أبوي", timestamp = at(0, 16, 5)),
        TxnEntity(id = 13, type = TxnType.EXPENSE, amountFils = 1_000, categoryId = cat("topup"), fromPocketId = 1, timestamp = at(0, 17, 45)),
        TxnEntity(id = 20, type = TxnType.EXPENSE, amountFils = 500, categoryId = cat("food"), fromPocketId = 1, note = "فلافل", timestamp = at(1, 12, 15)),
        TxnEntity(id = 21, type = TxnType.EXPENSE, amountFils = 3_000, categoryId = cat("outings"), fromPocketId = 1, note = "Cinema", timestamp = at(1, 19, 40)),
        TxnEntity(id = 22, type = TxnType.TRANSFER, amountFils = 10_000, fromPocketId = 2, toPocketId = 3, timestamp = at(1, 21, 0)),
        TxnEntity(id = 30, type = TxnType.EXPENSE, amountFils = 7_500, categoryId = cat("clothes"), fromPocketId = 2, note = "Hoodie", timestamp = at(3, 18, 0)),
        TxnEntity(id = 31, type = TxnType.EXPENSE, amountFils = 2_000, categoryId = cat("games"), fromPocketId = 1, timestamp = at(3, 20, 30)),
    ).sortedWith(compareByDescending<TxnEntity> { it.timestamp }.thenByDescending { it.id })

    private val snapshot = WalletSnapshot(pockets, Ledger.balances(txns), categories, quick)

    private fun walletUi(rows: List<TxnEntity> = txns, snap: WalletSnapshot = snapshot): WalletUi {
        val start = Ledger.startOfDay(today, zone)
        return WalletUi(snap.copy(balances = Ledger.balances(rows)), today, rows, rows.filter { it.timestamp in start until start + 86_400_000 })
    }

    private fun settings(language: AppLanguage, theme: ThemeMode, hide: Boolean = false) =
        AppSettings(onboardingDone = true, language = language, themeMode = theme, hideAmounts = hide, userName = if (language == AppLanguage.ENGLISH) "Adam" else "آدم")

    private fun shot(name: String, language: AppLanguage = AppLanguage.MIXED, theme: ThemeMode = ThemeMode.DARK, hide: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ProvideAppLanguage(language) {
                    AdamTheme(themeMode = theme, hideAmounts = hide) {
                        Box(Modifier.fillMaxSize().background(Adam.colors.bg)) { content() }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Composable
    private fun Main(
        language: AppLanguage = AppLanguage.MIXED,
        theme: ThemeMode = ThemeMode.DARK,
        tab: Tab = Tab.HOME,
        ui: WalletUi = walletUi(),
        hide: Boolean = false,
        edit: Boolean = false,
        layout: PageLayout = PageLayout.default(PageId.HOME),
    ) = MainScreen(
        state = AppState(settings(language, theme, hide), mapOf(PageId.HOME to layout)),
        walletUi = ui, vm = null, nav = MainNav(), onToggleHide = {}, onLayoutChange = {}, onLayoutReset = {},
        initialTab = tab, startInEditMode = edit, homeHour = 18,
    )

    // Home
    @Test fun home_dark_mixed() = shot("home_dark_mixed") { Main() }
    @Test fun home_light_mixed() = shot("home_light_mixed", theme = ThemeMode.LIGHT) { Main(theme = ThemeMode.LIGHT) }
    @Test fun home_dark_english() = shot("home_dark_english", AppLanguage.ENGLISH) { Main(AppLanguage.ENGLISH) }
    @Test fun home_light_english() = shot("home_light_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) { Main(AppLanguage.ENGLISH, ThemeMode.LIGHT) }
    @Test fun home_dark_arabic() = shot("home_dark_arabic", AppLanguage.ARABIC) { Main(AppLanguage.ARABIC) }
    @Test fun home_hidden() = shot("home_hidden", hide = true) { Main(hide = true) }
    @Test fun home_fresh() = shot("home_fresh") { Main(ui = walletUi(rows = emptyList())) }
    @Test fun home_edit() = shot("home_edit") {
        Main(edit = true, layout = PageLayout.default(PageId.HOME).move(BoxId.HOME_QUICK, BoxId.HOME_POCKETS).toggle(BoxId.HOME_TODAY))
    }

    // Add sheet (drawn as it sits inside the bottom sheet)
    @Composable
    private fun Sheet(request: AddRequest) {
        Box(Modifier.fillMaxSize().background(Adam.colors.scrim)) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(AdamShapes.sheet).background(Adam.colors.surface).padding(top = 26.dp),
            ) {
                AddSheetContent(request, snapshot, null, now, {}, {})
            }
        }
    }
    @Test fun add_dark_mixed() = shot("add_dark_mixed") { Sheet(AddRequest()) }
    @Test fun add_light_english() = shot("add_light_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) { Sheet(AddRequest()) }
    @Test fun add_edit_dark() = shot("add_edit_dark") { Sheet(AddRequest(editing = txns.first { it.id == 11L })) }
    @Test fun add_income_dark_english() = shot("add_income_dark_english", AppLanguage.ENGLISH) { Sheet(AddRequest(editing = txns.first { it.id == 12L })) }

    // History
    @Test fun history_dark_mixed() = shot("history_dark_mixed") { Main(tab = Tab.HISTORY) }
    @Test fun history_light_english() = shot("history_light_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) { Main(AppLanguage.ENGLISH, ThemeMode.LIGHT, tab = Tab.HISTORY) }
    @Test fun history_filtered() = shot("history_filtered") {
        com.adampay.app.ui.history.HistoryScreen(walletUi(), HistoryFilter(kind = HistoryFilter.Kind.EXPENSES, pocketId = 1), {}, {}, androidx.compose.foundation.layout.PaddingValues(0.dp))
    }
    @Test fun history_empty_search() = shot("history_empty_search", AppLanguage.ENGLISH) {
        com.adampay.app.ui.history.HistoryScreen(walletUi(), HistoryFilter(query = "pizza"), {}, {}, androidx.compose.foundation.layout.PaddingValues(0.dp))
    }

    // Settings
    @Test fun settings_dark_mixed() = shot("settings_dark_mixed") { Main(tab = Tab.SETTINGS) }
    @Test fun settings_light_english() = shot("settings_light_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) { Main(AppLanguage.ENGLISH, ThemeMode.LIGHT, tab = Tab.SETTINGS) }
    @Test fun appearance_dark() = shot("appearance_dark") { AppearanceScreen(settings(AppLanguage.MIXED, ThemeMode.DARK), SettingsActions(), {}, pockets.first()) }
    @Test fun appearance_light_english() = shot("appearance_light_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) {
        AppearanceScreen(settings(AppLanguage.ENGLISH, ThemeMode.LIGHT), SettingsActions(), {}, pockets.first())
    }
    @Test fun language_dark() = shot("language_dark") { LanguageScreen(AppLanguage.MIXED, {}, {}) }
    @Test fun money_display_dark() = shot("money_display_dark") { MoneyDisplayScreen(settings(AppLanguage.MIXED, ThemeMode.DARK), SettingsActions(), {}) }
    @Test fun categories_dark() = shot("categories_dark") { CategoriesScreen(snapshot, {}, {}, {}, {}) }
    @Test fun categories_light_english() = shot("categories_light_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) { CategoriesScreen(snapshot, {}, {}, {}, {}) }
    @Test fun category_editor_dark() = shot("category_editor_dark") {
        Box(Modifier.fillMaxSize().background(Adam.colors.surface).padding(top = 24.dp)) { CategoryEditor(categories.first(), true, {}, {}) }
    }
    @Test fun quick_items_dark() = shot("quick_items_dark") { QuickItemsScreen(snapshot, {}, {}, {}, {}) }
    @Test fun quick_editor_light_english() = shot("quick_editor_light_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) {
        Box(Modifier.fillMaxSize().background(Adam.colors.surface).padding(top = 24.dp)) { QuickItemEditor(quick.first(), snapshot, {}, {}) }
    }

    // First run
    @Test fun onboarding_language_dark() = shot("onboarding_language_dark") { OnboardingScreen(snapshot, {}, { _, _ -> }) }
    @Test fun onboarding_language_english_light() = shot("onboarding_language_english_light", AppLanguage.ENGLISH, ThemeMode.LIGHT) { OnboardingScreen(snapshot, {}, { _, _ -> }) }
    @Test fun onboarding_name_dark() = shot("onboarding_name_dark") { OnboardingScreen(snapshot, {}, { _, _ -> }, initialStep = 1, initialName = "آدم") }
    @Test fun onboarding_cash_dark() = shot("onboarding_cash_dark") {
        OnboardingScreen(snapshot, {}, { _, _ -> }, initialStep = 2, initialAmounts = mapOf(1L to 18_000L, 2L to 45_000L))
    }
    @Test fun onboarding_rerun_english() = shot("onboarding_rerun_english", AppLanguage.ENGLISH) {
        OnboardingScreen(snapshot, {}, { _, _ -> }, initialName = "Adam", rerun = true)
    }

    // Phase 2: cash count
    private val countSheet = com.adampay.app.domain.cash.CountSheet().set(10_000, 1).set(1_000, 2).set(500, 1).set(100, 1).plus(50)
    @Test fun count_enter_dark() = shot("count_enter_dark") {
        com.adampay.app.ui.cash.CountScreen(walletUi(), com.adampay.app.domain.cash.Denominations.Default, 1, { _, _, _, _, _ -> }, {}, initialSheet = countSheet)
    }
    @Test fun count_enter_light_english() = shot("count_enter_light_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) {
        com.adampay.app.ui.cash.CountScreen(walletUi(), com.adampay.app.domain.cash.Denominations.Default, 1, { _, _, _, _, _ -> }, {}, initialSheet = countSheet)
    }
    @Test fun count_missing_dark() = shot("count_missing_dark") {
        val ui = walletUi()
        val expected = ui.snapshot.balance(1)
        val r = com.adampay.app.domain.cash.Resolution(com.adampay.app.domain.cash.CountDifference.of(expected, countSheet.total))
            .add(com.adampay.app.domain.cash.Explanation.Expense(1_500, cat("food"), "شاورما"))
        com.adampay.app.ui.cash.CountScreen(ui.copy(lostThisMonth = 2_000), com.adampay.app.domain.cash.Denominations.Default, 1, { _, _, _, _, _ -> }, {},
            initialSheet = countSheet, initialResult = true, initialResolution = r)
    }
    @Test fun count_match_english() = shot("count_match_english", AppLanguage.ENGLISH) {
        val ui = walletUi()
        val sheet = com.adampay.app.domain.cash.CountSheet().set(10_000, 1).set(1_000, 4).set(500, 1).set(100, 1).plus(50)
        com.adampay.app.ui.cash.CountScreen(ui, com.adampay.app.domain.cash.Denominations.Default, 1, { _, _, _, _, _ -> }, {},
            initialSheet = sheet, initialResult = true, initialResolution = com.adampay.app.domain.cash.Resolution(com.adampay.app.domain.cash.CountDifference.of(ui.snapshot.balance(1), sheet.total)))
    }
    @Test fun count_extra_light() = shot("count_extra_light", theme = ThemeMode.LIGHT) {
        val ui = walletUi()
        val sheet = com.adampay.app.domain.cash.CountSheet().set(20_000, 1)
        com.adampay.app.ui.cash.CountScreen(ui, com.adampay.app.domain.cash.Denominations.Default, 1, { _, _, _, _, _ -> }, {},
            initialSheet = sheet, initialResult = true, initialResolution = com.adampay.app.domain.cash.Resolution(com.adampay.app.domain.cash.CountDifference.of(ui.snapshot.balance(1), sheet.total)))
    }

    // Phase 2: pockets and transfers
    @Composable
    private fun SheetBox(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Adam.colors.scrim)) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(AdamShapes.sheet).background(Adam.colors.surface).padding(top = 26.dp)) { content() }
        }
    }
    @Test fun pocket_sheet_dark() = shot("pocket_sheet_dark") {
        val ui = walletUi().copy(lastCounts = mapOf(1L to com.adampay.app.data.db.CashCountEntity(pocketId = 1, timestamp = at(1, 20, 0), expectedFils = 14_000, countedFils = 13_500)))
        SheetBox { com.adampay.app.ui.pockets.PocketSheetContent(pockets.first(), ui, com.adampay.app.ui.pockets.PocketActions(), zone) }
    }
    @Test fun pockets_dark() = shot("pockets_dark") {
        val withArchived = snapshot.copy(pockets = pockets + com.adampay.app.data.db.PocketEntity(id = 9, nameAr = "رحلة", nameEn = "Trip", icon = "beach_access", color = 0xFFE3B25A, color2 = 0xFF8F5E1A, archived = true))
        com.adampay.app.ui.pockets.PocketsScreen(walletUi(snap = withArchived), {}, { _, _ -> }, {}, {}, {})
    }
    @Test fun pocket_editor_dark() = shot("pocket_editor_dark") {
        Box(Modifier.fillMaxSize().background(Adam.colors.surface).padding(top = 24.dp)) { com.adampay.app.ui.pockets.PocketEditor(pockets[1], snapshot, {}, {}) }
    }
    @Test fun pocket_editor_custom_english() = shot("pocket_editor_custom_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) {
        Box(Modifier.fillMaxSize().background(Adam.colors.surface).padding(top = 24.dp)) {
            com.adampay.app.ui.pockets.PocketEditor(pockets[2].copy(color = 0xFF7A5CF0, color2 = com.adampay.app.ui.pockets.darker(0xFF7A5CF0)), snapshot, {}, {})
        }
    }
    @Test fun add_transfer_dark() = shot("add_transfer_dark") {
        Sheet(AddRequest(editing = txns.first { it.id == 22L }))
    }
    @Test fun add_transfer_new_english() = shot("add_transfer_new_english", AppLanguage.ENGLISH) {
        Sheet(AddRequest(type = TxnType.TRANSFER, pocketId = 1))
    }
    @Test fun denominations_dark() = shot("denominations_dark") {
        com.adampay.app.ui.settings.DenominationsScreen(com.adampay.app.domain.cash.Denominations.Default, {}, {})
    }

    // Phase 3: budget, goals, streak, reminders
    private val goalViews by lazy {
        val g1 = com.adampay.app.data.db.GoalEntity(id = 1, name = "سماعات", icon = "headphones", color = 0xFF5AA9F2, targetFils = 35_000, savedFils = 12_500, createdAt = at(20, 12, 0), deadlineEpochDay = today.plusDays(42).toEpochDay())
        val g2 = com.adampay.app.data.db.GoalEntity(id = 2, name = "Football boots", icon = "sports_soccer", color = 0xFF4FC7A0, targetFils = 30_000, createdAt = at(30, 12, 0), pocketId = 3)
        listOf(
            com.adampay.app.ui.GoalView(g1, com.adampay.app.domain.plan.GoalProgress.of(12_500, 35_000, 600.0, today, today.plusDays(42))),
            com.adampay.app.ui.GoalView(g2, com.adampay.app.domain.plan.GoalProgress.of(22_250, 30_000, 350.0, today, null)),
        )
    }
    private val gameGoal by lazy {
        val g = com.adampay.app.data.db.GoalEntity(id = 3, name = "لعبة جديدة", icon = "sports_esports", color = 0xFFFF4D9D, targetFils = 70_191, savedFils = 31_500, createdAt = at(40, 12, 0))
        com.adampay.app.ui.GoalView(g, com.adampay.app.domain.plan.GoalProgress.of(31_500, 70_191, 787.5, today, null))
    }
    private fun planUi(over: Boolean = false) = walletUi().copy(
        budget = com.adampay.app.domain.plan.BudgetStatus.of(if (over) 2_000 else 10_000, txns, today, java.time.DayOfWeek.SATURDAY, zone),
        goals = listOf(gameGoal) + goalViews,
        featured = gameGoal,
        streak = com.adampay.app.ui.StreakView(current = 6, best = 9, today = true),
    )
    @Test fun home_plan_dark() = shot("home_plan_dark") {
        Main(ui = planUi(), layout = PageLayout.default(PageId.HOME).toggle(BoxId.HOME_POCKETS).toggle(BoxId.HOME_QUICK))
    }
    @Test fun home_plan_light_english() = shot("home_plan_light_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) {
        Main(AppLanguage.ENGLISH, ThemeMode.LIGHT, ui = planUi(over = true), layout = PageLayout.default(PageId.HOME).toggle(BoxId.HOME_POCKETS).toggle(BoxId.HOME_QUICK))
    }
    @Test fun home_no_spend_dark() = shot("home_no_spend_dark") {
        val ui = walletUi(rows = txns.filterNot { Ledger.date(it.timestamp, zone) == today }).copy(streak = com.adampay.app.ui.StreakView(current = 3, best = 5, today = false))
        Main(ui = ui, layout = PageLayout.default(PageId.HOME).toggle(BoxId.HOME_POCKETS))
    }
    @Test fun budget_editor_dark() = shot("budget_editor_dark") { SheetBox { com.adampay.app.ui.plan.BudgetEditor(10_000) {} } }
    @Test fun goals_dark() = shot("goals_dark") { com.adampay.app.ui.plan.GoalsScreen(planUi(), {}, {}, {}, { _, _ -> }, {}) }
    @Test fun goals_empty_english() = shot("goals_empty_english", AppLanguage.ENGLISH) { com.adampay.app.ui.plan.GoalsScreen(walletUi(), {}, {}, {}, { _, _ -> }, {}) }
    @Test fun goal_editor_dark() = shot("goal_editor_dark") {
        Box(Modifier.fillMaxSize().background(Adam.colors.surface).padding(top = 24.dp)) { com.adampay.app.ui.plan.GoalEditor(goalViews[0].goal, planUi(), {}, {}, {}) }
    }
    @Test fun reminders_dark() = shot("reminders_dark") {
        com.adampay.app.ui.settings.RemindersScreen(settings(AppLanguage.MIXED, ThemeMode.DARK).copy(countReminder = true), com.adampay.app.ui.settings.ReminderActions(), {}, notificationsAllowed = false)
    }
    @Test fun reminders_light_english() = shot("reminders_light_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) {
        com.adampay.app.ui.settings.RemindersScreen(settings(AppLanguage.ENGLISH, ThemeMode.LIGHT), com.adampay.app.ui.settings.ReminderActions(), {}, notificationsAllowed = true)
    }

    /* Phase 5: app lock and backup */
    private val signedIn = com.adampay.app.domain.account.AccountState(
        google = com.adampay.app.domain.account.GoogleAccount("1", "Adam Oudeh", "adam.oudeh0@gmail.com"),
        status = com.adampay.app.domain.account.BackupStatus(lastAt = Instant.ofEpochMilli(at(1, 23, 31)), lastUploadedBytes = 2_400, usedBytes = 48_640, backups = 12),
    )
    private val lockOn = com.adampay.app.domain.security.SecurityConfig(lockEnabled = true, pinHash = "x", biometric = true, timeout = com.adampay.app.domain.security.LockTimeout.ONE_MINUTE)

    @Test fun lock_dark() = shot("lock_dark") {
        com.adampay.app.ui.security.LockScreen("آدم", biometric = true, waitUntil = 0, onCheck = { com.adampay.app.domain.security.PinCheck.Ok }, onBiometricOk = {}, biometricAvailable = true)
    }
    @Test fun lock_light_english() = shot("lock_light_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) {
        com.adampay.app.ui.security.LockScreen("Adam", biometric = false, waitUntil = 0, onCheck = { com.adampay.app.domain.security.PinCheck.Ok }, onBiometricOk = {}, biometricAvailable = false)
    }
    @Test fun lock_wait_dark() = shot("lock_wait_dark") {
        com.adampay.app.ui.security.LockScreen("آدم", biometric = true, waitUntil = System.currentTimeMillis() + 90_000, onCheck = { com.adampay.app.domain.security.PinCheck.Ok }, onBiometricOk = {}, biometricAvailable = true)
    }
    @Test fun pin_create_dark() = shot("pin_create_dark") {
        com.adampay.app.ui.security.PinFlowScreen(com.adampay.app.ui.security.PinFlow.SET, 0, { com.adampay.app.domain.security.PinCheck.Ok }, {}, {}, {}, {})
    }
    @Test fun security_dark() = shot("security_dark") {
        com.adampay.app.ui.security.SecurityScreen(lockOn, hideAmounts = false, actions = com.adampay.app.ui.security.SecurityActions(), onBack = {}, biometricAvailable = true)
    }
    @Test fun security_off_light_english() = shot("security_off_light_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) {
        com.adampay.app.ui.security.SecurityScreen(com.adampay.app.domain.security.SecurityConfig(), hideAmounts = true, actions = com.adampay.app.ui.security.SecurityActions(), onBack = {}, biometricAvailable = false)
    }
    @Test fun backup_signed_out_dark() = shot("backup_signed_out_dark") {
        com.adampay.app.ui.security.BackupScreen(com.adampay.app.domain.account.AccountState(), false, null, com.adampay.app.ui.security.BackupActions(), {})
    }
    @Test fun backup_dark() = shot("backup_dark") {
        com.adampay.app.ui.security.BackupScreen(signedIn, false, null, com.adampay.app.ui.security.BackupActions(), {})
    }
    @Test fun backup_error_light_english() = shot("backup_error_light_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) {
        com.adampay.app.ui.security.BackupScreen(
            signedIn.copy(status = signedIn.status.copy(lastError = "OFFLINE")), false, com.adampay.app.core.backup.BackupWork.BACKING_UP,
            com.adampay.app.ui.security.BackupActions(), {},
        )
    }
    @Test fun backup_restore_pick_dark() = shot("backup_restore_pick_dark") {
        com.adampay.app.ui.security.BackupScreen(
            signedIn, false, null, com.adampay.app.ui.security.BackupActions(), {},
            pick = listOf(at(0, 9, 12), at(1, 23, 31), at(2, 23, 30), at(5, 23, 32)).map(Instant::ofEpochMilli),
        )
    }

    /* Phase 6: featured goal card */
    @Test fun home_featured_dark() = shot("home_featured_dark") { Main(ui = planUi()) }
    @Test fun home_featured_light_english() = shot("home_featured_light_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) {
        Main(AppLanguage.ENGLISH, ThemeMode.LIGHT, ui = planUi().copy(featured = gameGoal.copy(goal = gameGoal.goal.copy(name = "New game"))))
    }
    @Test fun home_featured_empty_dark() = shot("home_featured_empty_dark") { Main() }
    @Test fun featured_reached_hidden_dark() = shot("featured_reached_hidden_dark", hide = true) {
        Box(Modifier.padding(18.dp)) {
            com.adampay.app.ui.home.FeaturedGoalCard(
                gameGoal.copy(progress = com.adampay.app.domain.plan.GoalProgress.of(70_191, 70_191, 0.0, today, null)), {}, {},
            )
        }
    }

    /* Phase 6: Quick Log and Quick access */
    @Test fun quick_log_dark() = shot("quick_log_dark") {
        Box(Modifier.fillMaxSize().background(Adam.colors.surface).padding(top = 24.dp)) {
            com.adampay.app.ui.quicklog.QuickLogSheet(snapshot, com.adampay.app.data.db.TxnType.EXPENSE, null, now, { _, _ -> }, {}, {}, inSheet = false)
        }
    }
    @Test fun quick_log_light_english_locked() = shot("quick_log_light_english_locked", AppLanguage.ENGLISH, ThemeMode.LIGHT, hide = true) {
        Box(Modifier.fillMaxSize().background(Adam.colors.surface).padding(top = 24.dp)) {
            com.adampay.app.ui.quicklog.QuickLogSheet(snapshot, com.adampay.app.data.db.TxnType.EXPENSE, null, now, { _, _ -> }, {}, {}, inSheet = false)
        }
    }
    @Test fun quick_access_dark() = shot("quick_access_dark") {
        com.adampay.app.ui.settings.QuickAccessScreen(com.adampay.app.core.launch.ButtonLink.Off, lockOn = true, onLink = {}, onUnlink = {}, onBack = {})
    }
    @Test fun quick_access_linked_english() = shot("quick_access_linked_english", AppLanguage.ENGLISH, ThemeMode.LIGHT) {
        com.adampay.app.ui.settings.QuickAccessScreen(com.adampay.app.core.launch.ButtonLink.On("x"), lockOn = false, onLink = {}, onUnlink = {}, onBack = {})
    }
    @Test fun goal_editor_featured_dark() = shot("goal_editor_featured_dark") {
        Box(Modifier.fillMaxSize().background(Adam.colors.surface).padding(top = 24.dp)) {
            com.adampay.app.ui.plan.GoalEditor(gameGoal.goal, planUi(), {}, {}, {}, featured = true, onSaveFeatured = { _, _ -> })
        }
    }
}
