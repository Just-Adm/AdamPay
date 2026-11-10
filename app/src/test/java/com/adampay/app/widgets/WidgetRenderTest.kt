package com.adampay.app.widgets

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adampay.app.ui.widgets.BalanceContent
import com.adampay.app.ui.widgets.GoalChip
import com.adampay.app.ui.widgets.GoalContent
import com.adampay.app.ui.widgets.QuickChip
import com.adampay.app.ui.widgets.QuickContent
import com.adampay.app.ui.widgets.WidgetData
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import com.adampay.app.domain.money.MoneyFormat
import com.adampay.app.ui.components.text
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the three home-screen widgets through Glance's RemoteViews (what the launcher gets) into
 * app/screenshots/widget_*.png, in the phone's dark mode and with an Arabic and an English app.
 * Run with :app:recordRoborazziDebug.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h802dp-night-xhdpi", application = Application::class)
class WidgetRenderTest {

    private val f = MoneyFormat()
    private fun m(fils: Long, hidden: Boolean = false) = f.text(fils, hidden = hidden)
    private fun data(arabic: Boolean, hidden: Boolean = false) = WidgetData(
        flip = arabic, rtl = arabic, accent = 0xFF2FB36D,
        totalTitle = if (arabic) "الرصيد الإجمالي" else "Total balance",
        total = m(64_400, hidden), negative = false,
        todayLine = (if (arabic) "مصروفات اليوم: " else "Spent today: ") + m(2_850, hidden),
        quickTitle = if (arabic) "الإدخال السريع" else "Quick entries",
        quick = listOf(
            QuickChip(1, if (arabic) "سرفيس" else "Service taxi", "0.350", 0xFF5AA9F2, "local_taxi"),
            QuickChip(2, if (arabic) "ساندويشة فلافل" else "Falafel sandwich", "0.500", 0xFFF2A65A, "fastfood"),
            QuickChip(3, if (arabic) "شحن رصيد" else "Top-up", "1.000", 0xFF4FC7A0, "mobile"),
        ),
        quickEmpty = "", add = "",
        goal = GoalChip(
            name = if (arabic) "لعبة جديدة" else "New game", icon = "sports_esports", fraction = 0.449f, percent = "44%",
            saved = m(31_500, hidden), ofTarget = (if (arabic) "من " else "of ") + m(70_191, hidden),
            level = if (arabic) "المستوى 2/4" else "Level 2/4", line = if (arabic) "المتبقي " + m(38_691, hidden) else m(38_691, hidden) + " to go",
        ),
        goalEmpty = "",
    )

    private fun render(name: String, size: DpSize, content: @Composable (Context) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val rv = runBlocking { GlanceRemoteViews().compose(activity, size) { content(activity) }.remoteViews }
        val d = activity.resources.displayMetrics.density
        val host = FrameLayout(activity).apply { setBackgroundColor(0xFF1E2A33.toInt()); setPadding(24, 24, 24, 24) }
        val view: View = rv.apply(activity, host)
        host.addView(view, ViewGroup.LayoutParams((size.width.value * d).toInt(), (size.height.value * d).toInt()))
        activity.setContentView(host, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        host.captureRoboImage("screenshots/$name.png")
    }

    @Test fun balance() = render("widget_balance_en", DpSize(180.dp, 110.dp)) { BalanceContent(data(false), it) }
    @Test fun balance_ar_hidden() = render("widget_balance_ar_hidden", DpSize(180.dp, 110.dp)) { BalanceContent(data(true, hidden = true), it) }
    @Test fun quick() = render("widget_quick_en", DpSize(320.dp, 130.dp)) { QuickContent(data(false), it) }
    @Test fun quick_ar() = render("widget_quick_ar", DpSize(250.dp, 130.dp)) { QuickContent(data(true), it) }
    @Test fun goal() = render("widget_goal_en", DpSize(250.dp, 150.dp)) { GoalContent(data(false), it) }
    @Test fun goal_ar() = render("widget_goal_ar", DpSize(250.dp, 150.dp)) { GoalContent(data(true), it) }
}
