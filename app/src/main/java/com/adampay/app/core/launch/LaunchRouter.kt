package com.adampay.app.core.launch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How the app was opened, reduced to what tells a hardware-button launch apart from a tap on the icon:
 * the app that sent the launch ([referrer]) and whether the launcher passed the icon's position ([fromIcon]).
 */
data class LaunchSource(val referrer: String?, val fromIcon: Boolean) {
    fun signature(): String = (referrer ?: "-") + if (fromIcon) "#icon" else ""
}

/** State of the "AI Button (double press) opens Quick Log" link, for the settings card. */
sealed interface ButtonLink {
    data object Off : ButtonLink
    /** Waiting for the user to press the button (until [untilMillis]). */
    data class Learning(val untilMillis: Long) : ButtonLink
    data class On(val signature: String) : ButtonLink
    /** The press looked exactly like an icon tap, so it cannot be told apart. */
    data object Indistinguishable : ButtonLink
}

/** What the launcher entry should open. */
enum class Route { APP, QUICK_LOG, QUICK_LOG_JUST_LINKED }

/**
 * Remembers the launch signature of the Honor AI Button (Hyper Athlete's approach). Hyper Athlete keeps the
 * single press; in the phone's AI Button settings the user picks "Adam Pay" for the double press, presses
 * "Link" in Adam Pay and then double-presses once; from then on that signature opens Quick Log and every other
 * launch (icon, search, notifications) opens the app. Stored on this phone only (not part of backups).
 */
object LaunchRouter {

    private const val PREFS = "launch_router"
    private const val K_SIG = "ai_button_signature"
    private const val K_LEARN = "learn_until"
    private const val K_FAIL = "indistinguishable"
    const val LEARN_WINDOW_MS = 120_000L

    private val _link = MutableStateFlow<ButtonLink>(ButtonLink.Off)
    val link: StateFlow<ButtonLink> = _link.asStateFlow()

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun refresh(c: Context, now: Long = System.currentTimeMillis()): ButtonLink {
        val p = prefs(c)
        val state = when {
            p.getLong(K_LEARN, 0) > now -> ButtonLink.Learning(p.getLong(K_LEARN, 0))
            p.getString(K_SIG, null) != null -> ButtonLink.On(p.getString(K_SIG, null)!!)
            p.getBoolean(K_FAIL, false) -> ButtonLink.Indistinguishable
            else -> ButtonLink.Off
        }
        _link.value = state
        return state
    }

    fun startLearning(c: Context, now: Long = System.currentTimeMillis()) {
        prefs(c).edit { putLong(K_LEARN, now + LEARN_WINDOW_MS); remove(K_FAIL) }
        refresh(c, now)
    }

    fun unlink(c: Context) {
        prefs(c).edit { remove(K_SIG); remove(K_LEARN); remove(K_FAIL) }
        refresh(c)
    }

    /** Decides where a launch of the launcher entry goes, learning the button's signature when asked to. */
    fun route(c: Context, source: LaunchSource, now: Long = System.currentTimeMillis()): Route {
        val p = prefs(c)
        val home = homePackage(c)
        val learning = p.getLong(K_LEARN, 0) > now
        val decision = decide(
            source = source, saved = p.getString(K_SIG, null), learning = learning,
            ownPackage = c.packageName, homePackage = home,
        )
        when (decision) {
            Decision.LEARNED -> p.edit { putString(K_SIG, source.signature()); remove(K_LEARN); remove(K_FAIL) }
            Decision.CANNOT_LEARN -> p.edit { remove(K_LEARN); putBoolean(K_FAIL, true) }
            else -> Unit
        }
        refresh(c, now)
        return when (decision) {
            Decision.LEARNED -> Route.QUICK_LOG_JUST_LINKED
            Decision.MATCH -> Route.QUICK_LOG
            else -> Route.APP
        }
    }

    enum class Decision { APP, MATCH, LEARNED, CANNOT_LEARN }

    /** Pure rule, unit-tested. */
    fun decide(source: LaunchSource, saved: String?, learning: Boolean, ownPackage: String, homePackage: String?): Decision {
        // Our own notifications and screens never count as the button.
        if (source.referrer == ownPackage) return Decision.APP
        if (learning) {
            // A normal icon tap from the home screen looks like this; it can't be the button.
            val looksLikeIcon = source.fromIcon && (source.referrer == null || source.referrer == homePackage)
            return if (looksLikeIcon) Decision.CANNOT_LEARN else Decision.LEARNED
        }
        if (saved != null && saved == source.signature()) return Decision.MATCH
        return Decision.APP
    }

    fun homePackage(c: Context): String? = runCatching {
        c.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }.getOrNull()
}
