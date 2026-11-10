package com.adampay.app.ui.widgets

import android.content.Context
import android.util.LayoutDirection
import com.adampay.app.R
import com.adampay.app.core.locale.AppLocales
import com.adampay.app.core.locale.Localizer
import com.adampay.app.core.security.AppLock
import com.adampay.app.data.settings.SettingsRepository
import com.adampay.app.data.wallet.WalletRepository
import com.adampay.app.data.wallet.name
import com.adampay.app.domain.wallet.Ledger
import com.adampay.app.ui.Featured
import com.adampay.app.ui.components.text
import com.adampay.app.ui.goalViews
import com.adampay.app.ui.home.GoalLevels
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun wallet(): WalletRepository
    fun settings(): SettingsRepository
    fun lock(): AppLock
    fun clock(): Clock
}

data class QuickChip(val id: Long, val name: String, val amount: String, val color: Long, val icon: String?)

data class GoalChip(
    val name: String,
    val icon: String,
    val fraction: Float,
    val percent: String,
    val saved: String,
    val ofTarget: String,
    val level: String,
    val line: String,
)

/** Everything the three widgets show, already formatted in the app's language. */
data class WidgetData(
    /** The app's language reads right to left but the launcher lays out left to right (or the other way). */
    val flip: Boolean,
    /** The app's own reading direction (for joined lines). */
    val rtl: Boolean = false,
    val accent: Long,
    val totalTitle: String,
    val total: String,
    val negative: Boolean,
    val todayLine: String,
    val quickTitle: String,
    val quick: List<QuickChip>,
    val quickEmpty: String,
    val add: String,
    val goal: GoalChip?,
    val goalEmpty: String,
)

object WidgetLoader {

    suspend fun load(context: Context): WidgetData {
        val e = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val s = e.settings().current()
        val sec = e.lock().current()
        val wallet = e.wallet()
        val snap = wallet.snapshot.first()
        val txns = wallet.txns.first()
        val clock = e.clock()
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val start = Ledger.startOfDay(today, zone)
        val todayRows = txns.filter { it.timestamp in start until start + 86_400_000 }
        // A locked app never shows money on the home screen.
        val hidden = s.hideAmounts || sec.locks
        val f = s.moneyFormat
        val l = Localizer(context, s.language)
        val appRtl = AppLocales.isRtl(s.language)
        val systemRtl = context.resources.configuration.layoutDirection == LayoutDirection.RTL
        val goal = Featured.pick(goalViews(wallet.goals.first(), snap, txns, today, zone), s.featuredGoalId)?.let { v ->
            val p = v.progress
            GoalChip(
                name = v.goal.name,
                icon = v.goal.icon,
                fraction = p.fraction,
                percent = "${(p.fraction * 100).toInt()}%",
                saved = f.text(p.saved, hidden = hidden),
                ofTarget = l.string(R.string.featured_of, f.text(p.target, hidden = hidden)),
                level = l.string(R.string.featured_level, GoalLevels.level(p.fraction), GoalLevels.COUNT),
                line = if (p.reached) l.string(R.string.goal_reached) else l.string(R.string.featured_left, f.text(p.remaining, hidden = hidden)),
            )
        }
        return WidgetData(
            flip = appRtl != systemRtl,
            rtl = appRtl,
            accent = s.accentArgb,
            totalTitle = l.string(R.string.home_total),
            total = f.text(snap.total, hidden = hidden),
            negative = snap.total < 0 && !hidden,
            todayLine = l.string(R.string.w_spent_today, f.text(Ledger.spent(todayRows), hidden = hidden)),
            quickTitle = l.string(R.string.home_quick),
            quick = snap.quickItems.map { q ->
                val cat = snap.category(q.categoryId)
                QuickChip(q.id, q.name.forUi(s.language), if (hidden) com.adampay.app.ui.components.HIDDEN else f.number(q.amountFils), cat?.color ?: 0xFF9AA0A6, cat?.icon)
            },
            quickEmpty = l.string(R.string.w_quick_empty),
            add = l.string(R.string.w_add),
            goal = goal,
            goalEmpty = l.string(R.string.featured_empty),
        )
    }
}
