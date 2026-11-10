package com.adampay.app

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.adampay.app.core.launch.LaunchRouter
import com.adampay.app.core.launch.LaunchSource
import com.adampay.app.core.launch.Route
import com.adampay.app.data.db.TxnType
import com.adampay.app.ui.quicklog.QuickLogActivity

/**
 * The launcher entry. It shows nothing. App shortcuts (long press on the icon) carry an action and go
 * straight to Quick Log or the cash count. Otherwise it decides whether this launch is the Honor AI
 * Button's double press (learned once in Settings → Quick access) and opens Quick Log, or opens the app,
 * bringing an open one back exactly as it was. Runs in its own throw-away task so every launch reaches
 * [onCreate].
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val next: Intent? = when (intent?.action) {
            ACTION_EXPENSE -> QuickLogActivity.intent(this, TxnType.EXPENSE)
            ACTION_INCOME -> QuickLogActivity.intent(this, TxnType.INCOME)
            ACTION_COUNT -> home().putExtra(HomeActivity.EXTRA_OPEN, HomeActivity.OPEN_COUNT)
            else -> {
                val source = LaunchSource(referrer = referrer?.host, fromIcon = intent?.sourceBounds != null)
                when (runCatching { LaunchRouter.route(this, source) }.getOrDefault(Route.APP)) {
                    Route.QUICK_LOG, Route.QUICK_LOG_JUST_LINKED -> QuickLogActivity.intent(this, TxnType.EXPENSE)
                    Route.APP -> if (bringAppToFront()) null else home().apply { intent?.extras?.let { putExtras(it) } }
                }
            }
        }
        if (next != null) startActivity(next)
        finishQuietly()
    }

    private fun home() = Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun bringAppToFront(): Boolean = runCatching {
        val task = getSystemService(ActivityManager::class.java).appTasks.firstOrNull {
            it.taskInfo?.baseActivity?.className == HomeActivity::class.java.name
        } ?: return false
        task.moveToFront()
        true
    }.getOrDefault(false)

    private fun finishQuietly() {
        finish()
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        else @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    companion object {
        const val ACTION_EXPENSE = "com.adampay.app.action.NEW_EXPENSE"
        const val ACTION_INCOME = "com.adampay.app.action.NEW_INCOME"
        const val ACTION_COUNT = "com.adampay.app.action.COUNT_CASH"
    }
}
