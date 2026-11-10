package com.adampay.app.ui.quicklog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.adampay.app.R
import com.adampay.app.core.locale.Localizer
import com.adampay.app.core.locale.LocalAppLanguage
import com.adampay.app.core.locale.ProvideAppLanguage
import com.adampay.app.core.security.AppLock
import com.adampay.app.data.db.QuickItemEntity
import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.db.TxnSource
import com.adampay.app.data.db.TxnType
import com.adampay.app.data.settings.SettingsRepository
import com.adampay.app.data.wallet.WalletRepository
import com.adampay.app.data.wallet.name
import com.adampay.app.data.wallet.WalletSnapshot
import com.adampay.app.domain.money.Money
import com.adampay.app.ui.add.AddRequest
import com.adampay.app.ui.add.AddSheetContent
import com.adampay.app.ui.components.AppIcons
import com.adampay.app.ui.components.HIDDEN
import com.adampay.app.ui.components.rememberHaptics
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.components.text
import com.adampay.app.ui.components.tintPair
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes
import com.adampay.app.ui.theme.AdamTheme
import com.adampay.app.ui.theme.LocalHideAmounts
import com.adampay.app.ui.theme.LocalMoneyFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick Log: the add sheet over whatever is on screen, opened from a widget, an app shortcut or the
 * AI Button (double press). Quick entries on top log with one tap. While the app lock is on it never
 * shows balances (amounts are hidden), it only adds.
 */
@AndroidEntryPoint
class QuickLogActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var wallet: WalletRepository
    @Inject lateinit var lock: AppLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = if (intent.getStringExtra(EXTRA_TYPE) == TxnType.INCOME.name) TxnType.INCOME else TxnType.EXPENSE
        val source = if (intent.getStringExtra(EXTRA_FROM) == FROM_WIDGET) TxnSource.WIDGET else TxnSource.QUICK
        setContent {
            val s by settings.settings.collectAsState(initial = null)
            val sec by lock.config.collectAsState(initial = null)
            val snapshot by wallet.snapshot.collectAsState(initial = WalletSnapshot.Empty)
            val st = s ?: return@setContent
            val security = sec ?: return@setContent
            if (snapshot.pockets.isEmpty()) return@setContent
            androidx.compose.runtime.SideEffect {
                if (security.secureScreen) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
            ProvideAppLanguage(st.language) {
                AdamTheme(
                    themeMode = st.themeMode, accent = Color(st.accentArgb), textScale = st.textSize.scale,
                    hapticsEnabled = st.hapticsEnabled, moneyFormat = st.moneyFormat,
                    hideAmounts = st.hideAmounts || security.locks,
                ) {
                    QuickLogSheet(
                        snapshot = snapshot,
                        type = type,
                        defaultPocketId = st.defaultPocketId,
                        now = System.currentTimeMillis(),
                        onQuick = { item, name ->
                            lifecycleScope.launch {
                                val pocket = snapshot.defaultPocket(settings.current().defaultPocketId) ?: return@launch
                                val row = wallet.logQuick(item, pocket.id, name, source)
                                saved(row)
                            }
                        },
                        onSave = { txn ->
                            lifecycleScope.launch {
                                val id = wallet.save(txn.copy(createdBy = source))
                                saved(txn.copy(id = id))
                            }
                        },
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    private suspend fun saved(row: TxnEntity) {
        val s = settings.settings.first()
        val hidden = s.hideAmounts || lock.current().locks
        val l = Localizer(this, s.language)
        Toast.makeText(this, l.string(R.string.snack_saved, s.moneyFormat.text(row.amountFils, hidden = hidden)), Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_FROM = "from"
        const val FROM_WIDGET = "widget"

        fun intent(context: Context, type: TxnType = TxnType.EXPENSE, from: String? = null): Intent =
            Intent(context, QuickLogActivity::class.java)
                .putExtra(EXTRA_TYPE, type.name)
                .apply { if (from != null) putExtra(EXTRA_FROM, from) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

/** The sheet itself (also drawn by the screenshot tests). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLogSheet(
    snapshot: WalletSnapshot,
    type: TxnType,
    defaultPocketId: Long?,
    now: Long,
    onQuick: (QuickItemEntity, String) -> Unit,
    onSave: (TxnEntity) -> Unit,
    onDismiss: () -> Unit,
    inSheet: Boolean = true,
) {
    val c = Adam.colors
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val body: @Composable () -> Unit = {
        Column {
            if (snapshot.quickItems.isNotEmpty()) {
                Text(
                    stringResource(R.string.home_quick), style = Adam.type.smallMedium, color = c.textSecondary,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 8.dp),
                )
                QuickRow(snapshot, onQuick)
                Spacer(Modifier.height(6.dp))
            }
            AddSheetContent(
                request = AddRequest(type = type),
                snapshot = snapshot,
                defaultPocketId = defaultPocketId,
                now = now,
                onSave = { txn -> scope.launch { if (inSheet) state.hide() }.invokeOnCompletion { onSave(txn) } },
                onDelete = {},
            )
        }
    }
    if (!inSheet) { body(); return }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.surface, shape = AdamShapes.sheet) { body() }
}

@Composable
private fun QuickRow(snapshot: WalletSnapshot, onQuick: (QuickItemEntity, String) -> Unit) {
    val language = LocalAppLanguage.current
    val format = LocalMoneyFormat.current
    val hidden = LocalHideAmounts.current
    val haptics = rememberHaptics()
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(snapshot.quickItems, key = { it.id }) { q ->
            val cat = snapshot.category(q.categoryId)
            val (tint, bg) = tintPair(cat?.color ?: 0xFF9AA0A6)
            val name = q.name.forUi(language)
            Row(
                Modifier.clip(AdamShapes.pill).background(Adam.colors.surfaceAlt)
                    .tap { haptics.confirm(); onQuick(q, name) }
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                    Icon(painterResource(AppIcons.of(cat?.icon)), null, tint = tint, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(name, style = Adam.type.smallMedium, color = Adam.colors.text, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (hidden) HIDDEN else Money.formatNumber(q.amountFils, format.decimals),
                    style = Adam.type.amount.copy(fontSize = 13.sp), color = Adam.colors.textSecondary,
                )
            }
        }
    }
}
