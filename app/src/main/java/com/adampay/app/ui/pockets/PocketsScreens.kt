package com.adampay.app.ui.pockets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adampay.app.R
import com.adampay.app.core.locale.LocalAppLanguage
import com.adampay.app.data.db.CashCountEntity
import com.adampay.app.data.db.PocketEntity
import com.adampay.app.data.wallet.CardPalette
import com.adampay.app.data.wallet.WalletSnapshot
import com.adampay.app.data.wallet.name
import com.adampay.app.ui.WalletUi
import com.adampay.app.ui.components.AdamTextField
import com.adampay.app.ui.components.Amount
import com.adampay.app.ui.components.AppIcons
import com.adampay.app.ui.components.GhostButton
import com.adampay.app.ui.components.PrimaryButton
import com.adampay.app.ui.components.SettingsRow
import com.adampay.app.ui.components.dayText
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.home.PocketCard
import com.adampay.app.ui.settings.ReorderPage
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes
import java.time.Instant
import java.time.ZoneId

/** What a pocket card on Home offers when tapped. */
class PocketActions(
    val count: (PocketEntity) -> Unit = {},
    val transfer: (PocketEntity) -> Unit = {},
    val statement: (PocketEntity) -> Unit = {},
    val edit: (PocketEntity) -> Unit = {},
)

/** Sheet content for one pocket: the card, its last count, and actions. */
@Composable
fun PocketSheetContent(pocket: PocketEntity, ui: WalletUi, actions: PocketActions, zone: ZoneId = ZoneId.systemDefault()) {
    val c = Adam.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
        PocketCard(pocket, ui.snapshot.balance(pocket.id))
        Spacer(Modifier.height(10.dp))
        Text(lastCountText(ui.lastCounts[pocket.id], ui, zone), style = Adam.type.small, color = c.textMuted, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(8.dp))
        SettingsRow(R.drawable.ic_payments, stringResource(R.string.pocket_count), subtitle = stringResource(R.string.pocket_count_sub)) { actions.count(pocket) }
        SettingsRow(R.drawable.ic_swap_horiz, stringResource(R.string.pocket_transfer), subtitle = stringResource(R.string.pocket_transfer_sub)) { actions.transfer(pocket) }
        SettingsRow(R.drawable.ic_receipt_long, stringResource(R.string.pocket_statement)) { actions.statement(pocket) }
        SettingsRow(R.drawable.ic_edit, stringResource(R.string.pocket_edit)) { actions.edit(pocket) }
    }
}

@Composable
private fun lastCountText(count: CashCountEntity?, ui: WalletUi, zone: ZoneId): String {
    if (count == null) return stringResource(R.string.pocket_never_counted)
    val day = Instant.ofEpochMilli(count.timestamp).atZone(zone).toLocalDate()
    val result = when {
        count.countedFils == count.expectedFils -> stringResource(R.string.pocket_count_matched)
        count.countedFils < count.expectedFils -> stringResource(R.string.pocket_count_missing)
        else -> stringResource(R.string.pocket_count_extra)
    }
    return stringResource(R.string.pocket_last_count, dayText(day, ui.today), result)
}

/** Settings → Pockets: reorder, add, edit; archived pockets can be restored. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketsScreen(
    ui: WalletUi,
    onSave: (PocketEntity) -> Unit,
    onRemove: (PocketEntity, (com.adampay.app.data.wallet.PocketRemoval) -> Unit) -> Unit,
    onRestore: (PocketEntity) -> Unit,
    onReorder: (List<PocketEntity>) -> Unit,
    onBack: () -> Unit,
    initialEditing: PocketEntity? = null,
) {
    val language = LocalAppLanguage.current
    var editing by remember { mutableStateOf(initialEditing) }
    var refusal by remember { mutableStateOf<com.adampay.app.data.wallet.PocketRemoval?>(null) }
    val snapshot = ui.snapshot
    refusal?.let { r ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { refusal = null },
            containerColor = Adam.colors.surfaceHigh,
            title = { Text(stringResource(R.string.pocket_remove), style = Adam.type.title, color = Adam.colors.text) },
            text = {
                Text(
                    stringResource(if (r == com.adampay.app.data.wallet.PocketRemoval.HAS_MONEY) R.string.pocket_remove_has_money else R.string.pocket_remove_last),
                    style = Adam.type.body, color = Adam.colors.textSecondary,
                )
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { refusal = null }) { Text(stringResource(R.string.action_ok), color = Adam.colors.accentInk) } },
        )
    }
    val archived = snapshot.pockets.filter { it.archived }
    ReorderPage(
        title = stringResource(R.string.pockets_title),
        items = snapshot.activePockets,
        key = { it.id },
        onBack = onBack,
        onReorder = onReorder,
        onNew = {
            val g = CardPalette.Gradients[snapshot.pockets.size % CardPalette.Gradients.size]
            editing = PocketEntity(nameAr = "", nameEn = "", icon = "account_balance_wallet", color = g.start, color2 = g.end)
        },
        newLabel = stringResource(R.string.pockets_new),
        header = {
            Text(stringResource(R.string.pockets_hint), style = Adam.type.small, color = Adam.colors.textMuted, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 14.dp))
        },
        footer = {
            if (archived.isNotEmpty()) {
                Text(stringResource(R.string.pockets_archived), style = Adam.type.smallMedium, color = Adam.colors.textMuted, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
                archived.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(AdamShapes.tile).background(Adam.colors.surface).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GradientDot(p.color, p.color2, AppIcons.of(p.icon))
                        Spacer(Modifier.width(12.dp))
                        Text(p.name.forTerms(language), style = Adam.type.bodyMedium, color = Adam.colors.textSecondary, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.pockets_restore), style = Adam.type.smallMedium, color = Adam.colors.accentInk,
                            modifier = Modifier.clip(AdamShapes.pill).tap(press = false) { onRestore(p) }.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        },
    ) { p ->
        Row(Modifier.fillMaxWidth().tap(press = false) { editing = p }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GradientDot(p.color, p.color2, AppIcons.of(p.icon))
            Spacer(Modifier.width(12.dp))
            Text(p.name.forTerms(language), style = Adam.type.bodyMedium, color = Adam.colors.text, modifier = Modifier.weight(1f), maxLines = 1)
            Amount(snapshot.balance(p.id), peekable = false)
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_drag_indicator), null, tint = Adam.colors.textMuted, modifier = Modifier.size(20.dp))
        }
    }

    editing?.let { p ->
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { editing = null }, sheetState = state, containerColor = Adam.colors.surface, shape = AdamShapes.sheet) {
            PocketEditor(
                p, snapshot,
                onSave = { onSave(it); editing = null },
                onRemove = {
                    onRemove(p) { result ->
                        if (result == com.adampay.app.data.wallet.PocketRemoval.HAS_MONEY || result == com.adampay.app.data.wallet.PocketRemoval.LAST_ONE) refusal = result
                    }
                    editing = null
                },
            )
        }
    }
}

@Composable
private fun GradientDot(start: Long, end: Long?, icon: Int) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(start), Color(end ?: start)))),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(18.dp)) }
}

/** Name (Arabic / English), icon, card colour (palette or custom), live preview, remove. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PocketEditor(pocket: PocketEntity, snapshot: WalletSnapshot, onSave: (PocketEntity) -> Unit, onRemove: () -> Unit) {
    val c = Adam.colors
    var ar by rememberSaveable { mutableStateOf(pocket.nameAr) }
    var en by rememberSaveable { mutableStateOf(pocket.nameEn) }
    var icon by rememberSaveable { mutableStateOf(pocket.icon) }
    var start by rememberSaveable { mutableStateOf(pocket.color) }
    var end by rememberSaveable { mutableStateOf(pocket.color2) }
    var custom by rememberSaveable { mutableStateOf(CardPalette.Gradients.none { it.start == pocket.color && it.end == pocket.color2 }) }
    val preview = pocket.copy(nameAr = ar.ifBlank { en }, nameEn = en.ifBlank { ar }, icon = icon, color = start, color2 = end)
    val balance = snapshot.balance(pocket.id)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Text(stringResource(if (pocket.id == 0L) R.string.pockets_new else R.string.pocket_edit), style = Adam.type.h2, color = c.text)
        Spacer(Modifier.height(14.dp))
        PocketCard(preview, balance)
        Spacer(Modifier.height(16.dp))
        AdamTextField(ar, { ar = it.take(30) }, stringResource(R.string.cat_name_ar), rtl = true)
        Spacer(Modifier.height(12.dp))
        AdamTextField(en, { en = it.take(30) }, stringResource(R.string.cat_name_en), rtl = false)

        Text(stringResource(R.string.cat_icon), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcons.pocketIcons.forEach { (key, res) ->
                val on = key == icon
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (on) Color(start) else c.surfaceAlt).tap(pressedScale = 0.9f) { icon = key },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(res), null, tint = if (on) Color.White else c.textSecondary, modifier = Modifier.size(21.dp)) }
            }
        }

        Text(stringResource(R.string.pocket_color), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(CardPalette.Gradients) { g ->
                val on = !custom && g.start == start && g.end == end
                Box(
                    Modifier.size(40.dp).clip(CircleShape).border(2.dp, if (on) c.text else Color.Transparent, CircleShape).padding(4.dp)
                        .clip(CircleShape).background(Brush.linearGradient(listOf(Color(g.start), Color(g.end))))
                        .tap(pressedScale = 0.9f) { custom = false; start = g.start; end = g.end },
                )
            }
            item {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).border(2.dp, if (custom) c.text else Color.Transparent, CircleShape).padding(4.dp)
                        .clip(CircleShape).background(Brush.sweepGradient(Rainbow)).tap(pressedScale = 0.9f) { custom = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_palette), stringResource(R.string.pocket_color_custom), tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
        }
        if (custom) {
            Spacer(Modifier.height(14.dp))
            CustomColorPicker(start) { picked -> start = picked; end = darker(picked) }
        }

        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            stringResource(R.string.action_save),
            onClick = { onSave(preview) },
            enabled = ar.isNotBlank() || en.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (pocket.id != 0L) {
            Spacer(Modifier.height(10.dp))
            GhostButton(stringResource(R.string.pocket_remove), onRemove, Modifier.fillMaxWidth(), color = c.expense, icon = R.drawable.ic_archive)
            Text(stringResource(R.string.pocket_remove_note), style = Adam.type.small, color = c.textMuted, modifier = Modifier.padding(8.dp))
        }
        Spacer(Modifier.height(20.dp))
    }
}

private val Rainbow = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

/** The card's end colour for a custom pick: the same hue, darker. */
fun darker(argb: Long): Long {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb.toInt(), hsv)
    hsv[2] = (hsv[2] * 0.45f).coerceAtLeast(0.12f)
    return android.graphics.Color.HSVToColor(hsv).toLong() and 0xFFFFFFFFL
}

/** Hue bar + brightness bar. Drag or tap; the card preview above follows. */
@Composable
private fun CustomColorPicker(argb: Long, onPick: (Long) -> Unit) {
    val c = Adam.colors
    val hsv = remember(argb) { FloatArray(3).also { android.graphics.Color.colorToHSV(argb.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var value by remember { mutableFloatStateOf(hsv[2].coerceIn(0.35f, 1f)) }
    fun emit() = onPick(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.62f, value)).toLong() and 0xFFFFFFFFL)

    Text(stringResource(R.string.pocket_color_hue), style = Adam.type.micro, color = c.textMuted)
    Spacer(Modifier.height(6.dp))
    SliderBar(hue / 360f, Brush.horizontalGradient(Rainbow)) { f -> hue = f * 360f; emit() }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.pocket_color_brightness), style = Adam.type.micro, color = c.textMuted)
    Spacer(Modifier.height(6.dp))
    val base = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.62f, 1f)))
    SliderBar((value - 0.35f) / 0.65f, Brush.horizontalGradient(listOf(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.62f, 0.35f))), base))) { f ->
        value = 0.35f + f * 0.65f; emit()
    }
}

@Composable
private fun SliderBar(fraction: Float, brush: Brush, onChange: (Float) -> Unit) {
    val c = Adam.colors
    Canvas(
        Modifier.fillMaxWidth().height(30.dp)
            .pointerInput(Unit) { detectTapGestures { o -> onChange((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> onChange((change.position.x / size.width).coerceIn(0f, 1f)) } },
    ) {
        val h = 14.dp.toPx()
        val top = (size.height - h) / 2
        drawRoundRect(brush, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
        val x = fraction.coerceIn(0f, 1f) * size.width
        drawCircle(Color.White, radius = 12.dp.toPx(), center = Offset(x, size.height / 2))
        drawCircle(c.borderStrong, radius = 12.dp.toPx(), center = Offset(x, size.height / 2), style = Stroke(1.5.dp.toPx()))
    }
}
