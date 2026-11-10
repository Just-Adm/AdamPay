package com.adampay.app.core.reminders

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.adampay.app.R
import com.adampay.app.core.locale.ProvideAppLanguage
import com.adampay.app.data.db.CategoryType
import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.settings.SettingsRepository
import com.adampay.app.data.wallet.WalletRepository
import com.adampay.app.data.wallet.WalletSnapshot
import com.adampay.app.ui.add.CategoryGrid
import com.adampay.app.ui.components.AdamTextField
import com.adampay.app.ui.components.Amount
import com.adampay.app.ui.components.PrimaryButton
import com.adampay.app.ui.components.Sign
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes
import com.adampay.app.ui.theme.AdamTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Opened from the "saved" notification after an inline reply: a small sheet over whatever is on screen
 * to pick the category (and a note) for the amount just logged.
 */
@AndroidEntryPoint
class QuickCategoryActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var wallet: WalletRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val txnId = intent.getLongExtra(EXTRA_TXN, 0L)
        setContent {
            val s by settings.settings.collectAsState(initial = null)
            val snapshot by wallet.snapshot.collectAsState(initial = WalletSnapshot.Empty)
            val txn by produceState<TxnEntity?>(null, txnId) { value = wallet.txn(txnId) }
            val st = s ?: return@setContent
            ProvideAppLanguage(st.language) {
                AdamTheme(themeMode = st.themeMode, accent = Color(st.accentArgb), textScale = st.textSize.scale, moneyFormat = st.moneyFormat) {
                    val row = txn
                    if (row != null && snapshot.categories.isNotEmpty()) {
                        Sheet(row, snapshot, onDone = { updated ->
                            lifecycleScope.launch { wallet.save(updated); finish() }
                        }, onDismiss = { finish() })
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Sheet(txn: TxnEntity, snapshot: WalletSnapshot, onDone: (TxnEntity) -> Unit, onDismiss: () -> Unit) {
        val c = Adam.colors
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        var categoryId by remember { mutableStateOf(txn.categoryId) }
        var note by remember { mutableStateOf(txn.note) }
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.surface, shape = AdamShapes.sheet) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
                Text(stringResource(R.string.quickcat_title), style = Adam.type.h2, color = c.text)
                Spacer(Modifier.height(6.dp))
                Amount(txn.amountFils, sign = Sign.MINUS, color = c.expense, style = Adam.type.amountLarge, peekable = false)
                Spacer(Modifier.height(14.dp))
                CategoryGrid(snapshot.categories(CategoryType.EXPENSE), categoryId) { id -> categoryId = id }
                Spacer(Modifier.height(12.dp))
                AdamTextField(note, { note = it.take(80) }, stringResource(R.string.add_note), placeholder = stringResource(R.string.add_note_hint))
                Spacer(Modifier.height(16.dp))
                PrimaryButton(stringResource(R.string.action_save), {
                    scope.launch { state.hide() }.invokeOnCompletion { onDone(txn.copy(categoryId = categoryId, note = note.trim())) }
                }, Modifier.fillMaxWidth())
            }
        }
    }

    companion object {
        const val EXTRA_TXN = "txn"
    }
}
