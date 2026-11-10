package com.adampay.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.adampay.app.R
import com.adampay.app.core.auth.AuthMessage
import com.adampay.app.core.auth.GoogleStep
import com.adampay.app.core.locale.ProvideAppLanguage
import com.adampay.app.ui.security.BackupEvent
import com.adampay.app.ui.security.PinFlow
import kotlinx.coroutines.launch
import com.adampay.app.domain.layout.PageId
import com.adampay.app.ui.main.MainNav
import com.adampay.app.ui.main.MainScreen
import com.adampay.app.ui.main.Routes
import com.adampay.app.ui.onboarding.OnboardingScreen
import com.adampay.app.ui.settings.AppearanceScreen
import com.adampay.app.ui.settings.CategoriesScreen
import com.adampay.app.ui.settings.LanguageScreen
import com.adampay.app.ui.settings.MoneyDisplayScreen
import com.adampay.app.ui.settings.QuickItemsScreen
import com.adampay.app.ui.settings.SettingsActions
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamTheme

/** Theme, language and navigation for the whole app. */
@Composable
fun AdamRoot(state: AppState, app: AppViewModel) {
    val s = state.settings
    ProvideAppLanguage(s.language) {
        AdamTheme(
            themeMode = s.themeMode,
            accent = Color(s.accentArgb),
            textScale = s.textSize.scale,
            hapticsEnabled = s.hapticsEnabled,
            moneyFormat = s.moneyFormat,
            hideAmounts = s.hideAmounts,
        ) {
            Box(Modifier.fillMaxSize()) {
                AppBody(state, app)
                LockGate(state, app)
            }
        }
    }
}

@Composable
private fun AppBody(state: AppState, app: AppViewModel) {
    val s = state.settings
    val wallet: WalletViewModel = hiltViewModel()
    val ui by wallet.ui.collectAsStateWithLifecycle()
    if (!s.onboardingDone) {
        // Running setup again: wait for the current opening balances so the screen starts filled in.
        val opening by produceState<Map<Long, Long>?>(if (s.setupOnce) null else emptyMap(), s.setupOnce) {
            if (s.setupOnce) value = app.openingBalances()
        }
        val filled = opening ?: return
        if (!ui.loaded) return
        OnboardingScreen(
            snapshot = ui.snapshot,
            onLanguage = app::setLanguage,
            onFinish = app::finishOnboarding,
            initialName = s.userName,
            initialAmounts = filled,
            rerun = s.setupOnce,
            onCancel = app::cancelSetup,
        )
        return
    }
    val nav = rememberNavController()
    var arrangeRequest by rememberSaveable { mutableStateOf(false) }
    var securityMessage by rememberSaveable { mutableStateOf<Int?>(null) }
    val actions = SettingsActions(
        setLanguage = app::setLanguage,
        setThemeMode = app::setThemeMode,
        setAccent = app::setAccent,
        setTextSize = app::setTextSize,
        setHaptics = app::setHaptics,
        setHideAmounts = app::setHideAmounts,
        setDecimals = app::setDecimals,
        setCurrencyLabel = app::setCurrencyLabel,
        setWeekStart = app::setWeekStart,
        setDefaultPocket = app::setDefaultPocket,
        arrangeHome = { arrangeRequest = true; nav.popBackStack("main", inclusive = false) },
        resetAppearance = app::resetAppearance,
        setUserName = app::setUserName,
        restartSetup = app::restartSetup,
    )
    val back: () -> Unit = { nav.popBackStack() }
    val reminderActions = com.adampay.app.ui.settings.ReminderActions(
        setNightly = app::setNightlyReminder,
        setNightlyTime = app::setNightlyTime,
        setCount = app::setCountReminder,
        setCountDays = app::setCountEveryDays,
    )
    // A notification asked for a screen (the count reminder opens the cash count).
    val openRequest by app.openRequest.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(openRequest) {
        if (openRequest == com.adampay.app.HomeActivity.OPEN_COUNT) nav.navigate(Routes.COUNT) { launchSingleTop = true }
        if (openRequest != null) app.consumeOpen()
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dir = if (rtl) -1 else 1
    NavHost(
        nav, startDestination = "main",
        modifier = Modifier.fillMaxSize().background(Adam.colors.bg),
        enterTransition = { slideInHorizontally(tween(280)) { dir * it / 5 } + fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(tween(240)) { dir * it / 5 } + fadeOut(tween(180)) },
    ) {
        composable("main") {
            MainScreen(
                state = state,
                walletUi = ui,
                vm = wallet,
                nav = MainNav(open = { route -> nav.navigate(route) { launchSingleTop = true } }),
                onToggleHide = { app.setHideAmounts(!s.hideAmounts) },
                onLayoutChange = app::setLayout,
                onLayoutReset = { app.resetLayout(PageId.HOME) },
                settingsActions = actions,
                editModeRequest = arrangeRequest,
                onEditModeConsumed = { arrangeRequest = false },
            )
        }
        composable(
            "${Routes.COUNT}?pocket={pocket}",
            arguments = listOf(navArgument("pocket") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val pocket = entry.arguments?.getLong("pocket")?.takeIf { it > 0 }
            com.adampay.app.ui.cash.CountScreen(
                ui = ui, denominations = s.denominations, initialPocketId = pocket,
                onSave = { id, expected, counted, sheet, resolution ->
                    wallet.saveCount(id, expected, counted, sheet, resolution)
                    nav.popBackStack()
                },
                onBack = back,
            )
        }
        composable(
            "${Routes.POCKETS}?edit={edit}",
            arguments = listOf(navArgument("edit") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val editId = entry.arguments?.getLong("edit")?.takeIf { it > 0 }
            com.adampay.app.ui.pockets.PocketsScreen(
                ui = ui,
                onSave = wallet::savePocket,
                onRemove = { p, done -> wallet.removePocket(p, done) },
                onRestore = wallet::restorePocket,
                onReorder = wallet::reorderPockets,
                onBack = back,
                initialEditing = ui.snapshot.pocket(editId),
            )
        }
        composable(
            "${Routes.GOALS}?edit={edit}",
            arguments = listOf(navArgument("edit") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val editId = entry.arguments?.getLong("edit")?.takeIf { it > 0 }
            com.adampay.app.ui.plan.GoalsScreen(
                ui = ui,
                onSave = wallet::saveGoal,
                onDelete = wallet::deleteGoal,
                onReorder = wallet::reorderGoals,
                onAdjust = wallet::adjustGoal,
                onBack = back,
                initialEditing = ui.goals.firstOrNull { it.goal.id == editId }?.goal,
                featuredId = ui.featured?.goal?.id,
                onSaveFeatured = { g, on -> wallet.saveGoal(g, on) },
            )
        }
        composable(Routes.REMINDERS) {
            com.adampay.app.ui.settings.RemindersScreen(s, reminderActions, back)
        }
        composable(Routes.DENOMS) {
            com.adampay.app.ui.settings.DenominationsScreen(s.denominations, app::setDenominations, back)
        }
        composable(Routes.APPEARANCE) {
            AppearanceScreen(s, actions, back, preview = ui.snapshot.activePockets.firstOrNull())
        }
        composable(Routes.LANGUAGE) { LanguageScreen(s.language, app::setLanguage, back) }
        composable(Routes.MONEY) { MoneyDisplayScreen(s, actions, back) }
        composable(Routes.CATEGORIES) {
            CategoriesScreen(ui.snapshot, wallet::saveCategory, wallet::removeCategory, wallet::reorderCategories, back)
        }
        composable(Routes.QUICK) {
            QuickItemsScreen(ui.snapshot, wallet::saveQuickItem, wallet::deleteQuickItem, wallet::reorderQuickItems, back)
        }
        composable(Routes.SECURITY) {
            com.adampay.app.ui.security.SecurityScreen(
                message = securityMessage?.let { androidx.compose.ui.res.stringResource(it) },
                onMessageShown = { securityMessage = null },
                config = state.security,
                hideAmounts = s.hideAmounts,
                actions = com.adampay.app.ui.security.SecurityActions(
                    turnOn = { nav.navigate(Routes.pin(PinFlow.SET)) { launchSingleTop = true } },
                    turnOff = { nav.navigate(Routes.pin(PinFlow.DISABLE)) { launchSingleTop = true } },
                    changePin = { nav.navigate(Routes.pin(PinFlow.CHANGE)) { launchSingleTop = true } },
                    setBiometric = app::setBiometric,
                    setTimeout = app::setLockTimeout,
                    setSecureScreen = app::setSecureScreen,
                    setHideAmounts = app::setHideAmounts,
                ),
                onBack = back,
            )
        }
        composable("${Routes.PIN}/{flow}") { entry ->
            val flow = PinFlow.entries.firstOrNull { it.name == entry.arguments?.getString("flow") } ?: PinFlow.SET
            com.adampay.app.ui.security.PinFlowScreen(
                flow = flow,
                waitUntil = state.security.waitUntil,
                onCheck = app::checkPin,
                onSet = app::setPin,
                onDisable = app::disableLock,
                onDone = { f ->
                    // Shown as a snackbar on the App lock page we return to.
                    securityMessage = when (f) {
                        PinFlow.SET -> R.string.pin_set_done
                        PinFlow.CHANGE -> R.string.pin_changed
                        PinFlow.DISABLE -> R.string.pin_lock_off
                    }
                    nav.popBackStack()
                },
                onBack = back,
            )
        }
        composable(Routes.BACKUP) { BackupRoute(app, back) }
        composable(Routes.ACCESS) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val link by com.adampay.app.core.launch.LaunchRouter.link.collectAsStateWithLifecycle()
            // Re-read on return (the button may have been pressed meanwhile) and while the learning window runs.
            androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                com.adampay.app.core.launch.LaunchRouter.refresh(context)
                onPauseOrDispose { }
            }
            androidx.compose.runtime.LaunchedEffect(link) {
                while (link is com.adampay.app.core.launch.ButtonLink.Learning) {
                    kotlinx.coroutines.delay(1000); com.adampay.app.core.launch.LaunchRouter.refresh(context)
                }
            }
            com.adampay.app.ui.settings.QuickAccessScreen(
                link = link, lockOn = state.security.locks,
                onLink = { com.adampay.app.core.launch.LaunchRouter.startLearning(context) },
                onUnlink = { com.adampay.app.core.launch.LaunchRouter.unlink(context) },
                onBack = back,
            )
        }
    }
}

/** Settings → Backup with its Google sign-in screen, snackbars and restore dialogs. */
@Composable
private fun BackupRoute(app: AppViewModel, back: () -> Unit) {
    val vm: com.adampay.app.ui.security.BackupViewModel = hiltViewModel()
    val account by vm.account.collectAsStateWithLifecycle()
    val signingIn by vm.signingIn.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptics = com.adampay.app.ui.components.rememberHaptics()
    var pick by androidx.compose.runtime.remember { mutableStateOf<List<java.time.Instant>?>(null) }
    var found by androidx.compose.runtime.remember { mutableStateOf<List<java.time.Instant>?>(null) }
    val consent = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { r -> vm.completeSignIn(r.data) }

    androidx.compose.runtime.LaunchedEffect(vm) {
        vm.events.collect { e ->
            val text: String? = when (e) {
                is BackupEvent.Auth -> when (e.message) {
                    AuthMessage.LINKED -> context.getString(R.string.backup_linked, vm.account.value?.google?.email.orEmpty())
                    AuthMessage.CANCELLED -> context.getString(R.string.backup_cancelled)
                    AuthMessage.OFFLINE -> context.getString(R.string.backup_err_offline)
                    AuthMessage.SETUP -> context.getString(R.string.backup_err_setup) +
                        (e.code?.let { " (" + context.getString(R.string.backup_err_code, it.toString()) + ")" } ?: "")
                }
                is BackupEvent.BackedUp -> context.getString(if (e.newBackup) R.string.backup_done else R.string.backup_no_change)
                BackupEvent.Restored -> context.getString(R.string.backup_restored)
                BackupEvent.NoBackups -> context.getString(R.string.backup_none)
                is BackupEvent.Failed -> context.getString(
                    when (e.code) {
                        "SETUP" -> R.string.backup_err_setup
                        "SIGN_IN" -> R.string.backup_err_sign_in
                        "OFFLINE" -> R.string.backup_err_offline
                        else -> R.string.backup_err_failed
                    },
                )
                is BackupEvent.Found -> { found = e.backups; null }
                is BackupEvent.Pick -> { pick = e.backups; null }
            }
            if (e is BackupEvent.BackedUp || e == BackupEvent.Restored) haptics.confirm()
            if (e is BackupEvent.Failed) haptics.reject()
            if (text != null) snackbar.showSnackbar(text)
        }
    }

    com.adampay.app.ui.security.BackupScreen(
        account = account ?: return,
        signingIn = signingIn,
        running = running,
        actions = com.adampay.app.ui.security.BackupActions(
            signIn = {
                scope.launch {
                    val step = vm.startSignIn()
                    if (step is GoogleStep.NeedsConsent) {
                        app.expectReturn()
                        runCatching { consent.launch(androidx.activity.result.IntentSenderRequest.Builder(step.intent).build()) }
                            .onFailure { vm.completeSignIn(null) }
                    }
                }
            },
            signOut = vm::signOut,
            backupNow = vm::backupNow,
            pickBackup = vm::pickBackup,
            restore = vm::restore,
            setAuto = vm::setAuto,
        ),
        onBack = back,
        snackbar = snackbar,
        pick = pick,
        found = found,
        onDismissPick = { pick = null; found = null },
    )
}

/** The PIN screen over everything while the lock is on and not yet opened in this session. */
@Composable
private fun LockGate(state: AppState, app: AppViewModel) {
    val locked by app.locked.collectAsStateWithLifecycle()
    val sec = state.security
    if (!sec.locks || !locked) return
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    // A plain cover in the app's own window too: no frame of the wallet shows before the lock window opens.
    Box(Modifier.fillMaxSize().background(Adam.colors.bg))
    // Its own full-screen window, so it also covers any sheet or dialog that was open when the app left.
    // Back sends the app to the background; nothing behind the lock can be reached.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { activity?.moveTaskToBack(true) },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true, dismissOnClickOutside = false,
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        ),
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        val dark = Adam.colors.isDark
        androidx.compose.runtime.SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                w.setDimAmount(0f)
                w.setWindowAnimations(0)
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        com.adampay.app.ui.security.LockScreen(
            name = state.settings.userName,
            biometric = sec.biometric,
            waitUntil = sec.waitUntil,
            onCheck = app::checkPin,
            onBiometricOk = app::biometricUnlocked,
        )
    }
}
