package com.adampay.app.ui.widgets

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as UnitColorProvider
import com.adampay.app.HomeActivity
import com.adampay.app.R
import com.adampay.app.core.locale.Localizer
import com.adampay.app.data.db.TxnSource
import com.adampay.app.data.wallet.name
import com.adampay.app.ui.components.AppIcons
import com.adampay.app.ui.components.text
import com.adampay.app.ui.quicklog.QuickLogActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ------------------------------------------------------------------ colours */

/** The app's greens on a card that follows the phone's dark mode; the goal widget is always pink. */
private class WColors(accentArgb: Long) {
    val accent = Color(accentArgb.toInt())
    val text = ColorProvider(day = Color(0xFF0E1A14), night = Color(0xFFEFF4F1))
    val muted = ColorProvider(day = Color(0xFF5E6D65), night = Color(0xFF8E9C95))
    val chip = ColorProvider(day = Color(0xFFEDF2EF), night = Color(0xFF1B2420))
    val accentP = ColorProvider(day = accent, night = accent)
    val accentInk = ColorProvider(day = Color(0xFF1F7A4A), night = accent)
    val onAccent = ColorProvider(day = Color.White, night = Color(0xFF06140C))
    val expense = ColorProvider(day = Color(0xFFC8574C), night = Color(0xFFEE8A80))
    val white = ColorProvider(day = Color.White, night = Color.White)
    val whiteSoft = ColorProvider(day = Color.White.copy(alpha = 0.75f), night = Color.White.copy(alpha = 0.75f))
    val whiteTrack = ColorProvider(day = Color.White.copy(alpha = 0.22f), night = Color.White.copy(alpha = 0.22f))
}

/* ------------------------------------------------------------------ actions */

private fun openApp(context: Context): Action = actionStartActivity(
    Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
)

private fun quickLog(context: Context): Action =
    actionStartActivity(QuickLogActivity.intent(context, from = QuickLogActivity.FROM_WIDGET))

private val QuickIdKey = ActionParameters.Key<Long>("quick_id")

/** One tap on a quick entry in the widget logs it in the default pocket, then a short toast confirms. */
class LogQuickAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[QuickIdKey] ?: return
        val e = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val wallet = e.wallet()
        val s = e.settings().current()
        val snap = wallet.snapshot.first()
        val item = snap.quickItems.firstOrNull { it.id == id } ?: return
        val pocket = snap.defaultPocket(s.defaultPocketId) ?: return
        val row = wallet.logQuick(item, pocket.id, item.name.forUi(s.language), TxnSource.WIDGET)
        val hidden = s.hideAmounts || e.lock().current().locks
        val msg = Localizer(context, s.language).string(R.string.snack_saved, s.moneyFormat.text(row.amountFils, hidden = hidden))
        withContext(Dispatchers.Main) { Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show() }
        Widgets.refresh(context)
    }
}

/* ------------------------------------------------------------------ pieces */

@Composable
private fun Card(onClick: Action?, background: Int = R.drawable.widget_bg, content: @Composable () -> Unit) {
    var m = GlanceModifier.fillMaxSize().background(ImageProvider(background)).cornerRadius(24.dp).padding(14.dp)
    if (onClick != null) m = m.clickable(onClick)
    Box(m) { content() }
}

@Composable
private fun Icon(res: Int, tint: UnitColorProvider, size: Int = 20) {
    Image(ImageProvider(res), contentDescription = null, modifier = GlanceModifier.size(size.dp), colorFilter = ColorFilter.tint(tint))
}

/** A row whose children are reversed when the app's reading direction differs from the launcher's. */
@Composable
private fun DRow(flip: Boolean, modifier: GlanceModifier = GlanceModifier, items: List<@Composable RowScope.() -> Unit>) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) { (if (flip) items.reversed() else items).forEach { it(this) } }
}

@Composable
private fun AddButton(context: Context, c: WColors, size: Int = 34) {
    Box(
        GlanceModifier.size(size.dp).background(c.accentP).cornerRadius((size / 2).dp).clickable(quickLog(context)),
        contentAlignment = Alignment.Center,
    ) { Icon(R.drawable.ic_add, c.onAccent, size / 2 + 2) }
}

private fun align(flip: Boolean) = if (flip) Alignment.End else Alignment.Start
private fun textAlign(flip: Boolean) = if (flip) TextAlign.End else TextAlign.Start

/* ------------------------------------------------------------------ contents (also used by tests) */

@Composable
fun BalanceContent(d: WidgetData, context: Context) {
    val c = WColors(d.accent)
    val big = LocalSize.current.height >= 150.dp
    Card(openApp(context)) {
        Column(GlanceModifier.fillMaxSize(), horizontalAlignment = align(d.flip)) {
            DRow(
                d.flip, GlanceModifier.fillMaxWidth(),
                listOf(
                    { Icon(R.drawable.ic_account_balance_wallet, c.accentP, 18) },
                    { Spacer(GlanceModifier.width(6.dp)) },
                    { Text(d.totalTitle, style = TextStyle(color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = textAlign(d.flip)), maxLines = 1, modifier = GlanceModifier.defaultWeight()) },
                    { AddButton(context, c, 30) },
                ),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                d.total, maxLines = 1,
                style = TextStyle(color = if (d.negative) c.expense else c.text, fontSize = if (big) 30.sp else 24.sp, fontWeight = FontWeight.Bold, textAlign = textAlign(d.flip)),
                modifier = GlanceModifier.fillMaxWidth(),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(d.todayLine, maxLines = 1, style = TextStyle(color = c.muted, fontSize = 12.sp, textAlign = textAlign(d.flip)), modifier = GlanceModifier.fillMaxWidth())
        }
    }
}

@Composable
fun QuickContent(d: WidgetData, context: Context) {
    val c = WColors(d.accent)
    val size = LocalSize.current
    val perRow = if (size.width >= 300.dp) 3 else 2
    val rows = if (size.height >= 230.dp) 3 else if (size.height >= 170.dp) 2 else 1
    Card(null) {
        Column(GlanceModifier.fillMaxSize(), horizontalAlignment = align(d.flip)) {
            DRow(
                d.flip, GlanceModifier.fillMaxWidth().clickable(openApp(context)),
                listOf(
                    { Icon(R.drawable.ic_bolt, c.accentP, 18) },
                    { Spacer(GlanceModifier.width(6.dp)) },
                    { Text(d.quickTitle, style = TextStyle(color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = textAlign(d.flip)), maxLines = 1, modifier = GlanceModifier.defaultWeight()) },
                    { AddButton(context, c, 30) },
                ),
            )
            Spacer(GlanceModifier.height(8.dp))
            if (d.quick.isEmpty()) {
                Text(d.quickEmpty, style = TextStyle(color = c.muted, fontSize = 12.sp, textAlign = textAlign(d.flip)), maxLines = 2, modifier = GlanceModifier.fillMaxWidth())
            }
            d.quick.take(perRow * rows).chunked(perRow).forEachIndexed { i, row ->
                if (i > 0) Spacer(GlanceModifier.height(6.dp))
                val cells: List<@Composable RowScope.() -> Unit> = row.flatMapIndexed { j, q ->
                    listOfNotNull<@Composable RowScope.() -> Unit>(
                        if (j > 0) ({ Spacer(GlanceModifier.width(6.dp)) }) else null,
                        { QuickCell(q, c, GlanceModifier.defaultWeight()) },
                    )
                } + List(perRow - row.size) {
                    val empty: @Composable RowScope.() -> Unit = { Spacer(GlanceModifier.width(6.dp)); Spacer(GlanceModifier.defaultWeight()) }
                    empty
                }
                DRow(d.flip, GlanceModifier.fillMaxWidth(), cells)
            }
        }
    }
}

@Composable
private fun QuickCell(q: QuickChip, c: WColors, modifier: GlanceModifier) {
    val tint = ColorProvider(day = Color(q.color.toInt()), night = Color(q.color.toInt()))
    Column(
        modifier.background(c.chip).cornerRadius(14.dp).padding(horizontal = 10.dp, vertical = 7.dp)
            .clickable(actionRunCallback<LogQuickAction>(actionParametersOf(QuickIdKey to q.id))),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.of(q.icon), tint, 14)
            Spacer(GlanceModifier.width(5.dp))
            Text(q.name, style = TextStyle(color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
        Text(q.amount, style = TextStyle(color = c.muted, fontSize = 11.sp), maxLines = 1)
    }
}

@Composable
fun GoalContent(d: WidgetData, context: Context) {
    val c = WColors(d.accent)
    val g = d.goal
    val roomy = LocalSize.current.height >= 140.dp
    Card(openApp(context), background = R.drawable.widget_pink) {
        if (g == null) {
            Column(GlanceModifier.fillMaxSize(), horizontalAlignment = align(d.flip), verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.ic_savings, c.white, 26)
                Spacer(GlanceModifier.height(6.dp))
                Text(d.goalEmpty, style = TextStyle(color = c.white, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = textAlign(d.flip)), maxLines = 2)
            }
            return@Card
        }
        Column(GlanceModifier.fillMaxSize(), horizontalAlignment = align(d.flip)) {
            DRow(
                d.flip, GlanceModifier.fillMaxWidth(),
                listOf(
                    {
                        Box(GlanceModifier.size(30.dp).background(c.whiteTrack).cornerRadius(15.dp), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.of(g.icon), c.white, 17)
                        }
                    },
                    { Spacer(GlanceModifier.width(8.dp)) },
                    { Text(g.name, style = TextStyle(color = c.white, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = textAlign(d.flip)), maxLines = 1, modifier = GlanceModifier.defaultWeight()) },
                    { Text(g.percent, style = TextStyle(color = c.white, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1) },
                ),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(g.saved, style = TextStyle(color = c.white, fontSize = if (roomy) 22.sp else 18.sp, fontWeight = FontWeight.Bold, textAlign = textAlign(d.flip)), maxLines = 1, modifier = GlanceModifier.fillMaxWidth())
            Text(g.ofTarget, style = TextStyle(color = c.whiteSoft, fontSize = 11.sp, textAlign = textAlign(d.flip)), maxLines = 1, modifier = GlanceModifier.fillMaxWidth())
            Spacer(GlanceModifier.height(8.dp))
            LevelBar(g.fraction, d.flip, c)
            if (roomy) {
                Spacer(GlanceModifier.height(6.dp))
                Text(com.adampay.app.core.locale.Bidi.join(listOf(g.level, g.line), " · ", d.rtl), style = TextStyle(color = c.whiteSoft, fontSize = 11.sp, textAlign = textAlign(d.flip)), maxLines = 1, modifier = GlanceModifier.fillMaxWidth())
            }
        }
    }
}

/** Four segments; full ones white, the current one half-lit, the rest faint (RemoteViews can't clip shapes). */
@Composable
private fun LevelBar(fraction: Float, flip: Boolean, c: WColors) {
    val n = com.adampay.app.ui.home.GoalLevels.COUNT
    val half = ColorProvider(day = Color.White.copy(alpha = 0.55f), night = Color.White.copy(alpha = 0.55f))
    val cells: List<@Composable RowScope.() -> Unit> = (0 until n).flatMap { i ->
        val part = (fraction * n - i).coerceIn(0f, 1f)
        val color = when {
            part >= 1f -> c.white
            part > 0f -> half
            else -> c.whiteTrack
        }
        listOfNotNull<@Composable RowScope.() -> Unit>(
            if (i > 0) ({ Spacer(GlanceModifier.width(4.dp)) }) else null,
            { Box(GlanceModifier.defaultWeight().height(8.dp).background(color).cornerRadius(4.dp)) {} },
        )
    }
    DRow(flip, GlanceModifier.fillMaxWidth(), cells)
}

/* ------------------------------------------------------------------ widgets */

abstract class AdamWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 70.dp), DpSize(180.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(320.dp, 110.dp), DpSize(250.dp, 200.dp), DpSize(320.dp, 200.dp)),
    )

    @Composable abstract fun Content(d: WidgetData, context: Context)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetLoader.load(context)
        provideContent { Content(data, context) }
    }
}

class BalanceWidget : AdamWidget() { @Composable override fun Content(d: WidgetData, context: Context) = BalanceContent(d, context) }
class QuickWidget : AdamWidget() { @Composable override fun Content(d: WidgetData, context: Context) = QuickContent(d, context) }
class GoalWidget : AdamWidget() { @Composable override fun Content(d: WidgetData, context: Context) = GoalContent(d, context) }

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BalanceWidget() }
class QuickWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickWidget() }
class GoalWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = GoalWidget() }

/** Redraws every placed widget after the wallet, the settings or the lock change. Cheap when none are placed. */
object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val all: List<() -> AdamWidget> = listOf(::BalanceWidget, ::QuickWidget, ::GoalWidget)

    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val mgr = GlanceAppWidgetManager(app)
                all.forEach { make ->
                    val w = make()
                    if (mgr.getGlanceIds(w.javaClass).isNotEmpty()) w.updateAll(app)
                }
            }
        }
    }
}
