package com.adampay.app

import android.graphics.Color
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adampay.app.ui.AdamRoot
import com.adampay.app.ui.AppViewModel
import com.adampay.app.ui.theme.isDarkTheme
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The app's UI. Opened by the launcher router ([MainActivity]) and by notifications. */
@AndroidEntryPoint
class HomeActivity : ComponentActivity() {

    companion object {
        /** Extra naming a screen to open on launch, e.g. [OPEN_COUNT] from the count reminder. */
        const val EXTRA_OPEN = "open"
        const val OPEN_COUNT = "count"
    }

    private val appViewModel: AppViewModel by viewModels()

    @javax.inject.Inject lateinit var appLock: com.adampay.app.core.security.AppLock

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until settings are read, so the first frame is already themed and localised.
        splash.setKeepOnScreenCondition { appViewModel.state.value == null }
        window.decorView.post { runCatching { preferHighestRefreshRate() } }
        if (savedInstanceState == null) appViewModel.open(intent?.getStringExtra(EXTRA_OPEN))
        askNotificationsOnce()

        setContent {
            val state by appViewModel.state.collectAsStateWithLifecycle()
            val s = state ?: return@setContent
            // Settings → App lock → "Hide in recent apps": blank thumbnail, no screenshots.
            DisposableEffect(s.security.secureScreen) {
                if (s.security.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose { }
            }
            val dark = isDarkTheme(s.settings.themeMode)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            AdamRoot(s, appViewModel)
        }
    }

    private val notificationPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    /** Once, after setup, ask for notifications so the evening reminder can appear (Android 13+). */
    private fun askNotificationsOnce() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences("app_flags", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        lifecycleScope.launch {
            appViewModel.state.first { it?.settings?.onboardingDone == true }
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                prefs.edit().putBoolean("asked_notifications", true).apply()
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { appLock.onForeground() }
    }

    override fun onStop() {
        super.onStop()
        // A theme or language change recreates the screen; that isn't leaving the app.
        if (!isChangingConfigurations) appLock.onBackground()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        appViewModel.open(intent.getStringExtra(EXTRA_OPEN))
    }

    /** Ask for the panel's top refresh rate (120 Hz on the X7e) at the current resolution. */
    private fun preferHighestRefreshRate() {
        val display: Display = window.decorView.display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (best.modeId != current.modeId || window.attributes.preferredDisplayModeId != best.modeId) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        }
    }
}
