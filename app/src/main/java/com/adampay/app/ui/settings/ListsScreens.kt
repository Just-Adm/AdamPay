package com.adampay.app.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adampay.app.R
import com.adampay.app.core.locale.LocalAppLanguage
import com.adampay.app.data.db.CategoryEntity
import com.adampay.app.data.db.CategoryType
import com.adampay.app.data.db.QuickItemEntity
import com.adampay.app.data.db.TxnType
import com.adampay.app.data.wallet.CardPalette
import com.adampay.app.data.wallet.WalletSnapshot
import com.adampay.app.data.wallet.name
import com.adampay.app.domain.model.AppLanguage
import com.adampay.app.domain.money.AmountInput
import com.adampay.app.ui.components.AdamCard
import com.adampay.app.ui.components.AdamTextField
import com.adampay.app.ui.components.Amount
import com.adampay.app.ui.components.AppIcons
import com.adampay.app.ui.components.Chip
import com.adampay.app.ui.components.EmptyState
import com.adampay.app.ui.components.GhostButton
import com.adampay.app.ui.components.IconBadge
import com.adampay.app.ui.components.MoneyKeypad
import com.adampay.app.ui.components.PrimaryButton
import com.adampay.app.ui.components.RoundIconButton
import com.adampay.app.ui.components.Segmented
import com.adampay.app.ui.components.rememberHaptics
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.components.tintPair
import com.adampay.app.ui.components.typeLabel
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** A page whose list can be reordered by long-press drag, with a bottom "New" button. */
@Composable
internal fun <T : Any> ReorderPage(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    onBack: () -> Unit,
    onReorder: (List<T>) -> Unit,
    onNew: () -> Unit,
    newLabel: String,
    header: @Composable () -> Unit = {},
    empty: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    snackbar: androidx.compose.material3.SnackbarHostState? = null,
    row: @Composable (T) -> Unit,
) {
    val c = Adam.colors
    val haptics = rememberHaptics()
    var working by remember(items) { mutableStateOf(items) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val f = working.indexOfFirst { key(it) == from.key }
        val t = working.indexOfFirst { key(it) == to.key }
        if (f >= 0 && t >= 0) {
            working = working.toMutableList().apply { add(t, removeAt(f)) }
            haptics.tick()
        }
    }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack, background = Color.Transparent)
                Spacer(Modifier.width(6.dp))
                Text(title, style = Adam.type.h2, color = c.text)
            }
            LazyColumn(
                Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 110.dp),
            ) {
                item(key = "header") { header() }
                if (working.isEmpty()) item(key = "empty") { empty() }
                items(working, key = key) { item ->
                    ReorderableItem(reorder, key = key(item)) { dragging ->
                        val elevation by animateDpAsState(if (dragging) 12.dp else 0.dp, tween(150), label = "lift")
                        Box(
                            Modifier
                                .padding(bottom = 8.dp)
                                .shadow(elevation, AdamShapes.tile)
                                .clip(AdamShapes.tile)
                                .background(c.surface)
                                .longPressDraggableHandle(onDragStarted = { haptics.longPress() }, onDragStopped = { onReorder(working) }),
                        ) { row(item) }
                    }
                }
                item(key = "footer") { footer() }
            }
        }
        PrimaryButton(
            newLabel, onNew, icon = R.drawable.ic_add,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(18.dp).fillMaxWidth(),
        )
        if (snackbar != null) {
            androidx.compose.material3.SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(start = 12.dp, end = 12.dp, bottom = 84.dp),
            ) { data ->
                androidx.compose.material3.Snackbar(data, shape = AdamShapes.tile, containerColor = c.surfaceHigh, contentColor = c.text, actionColor = c.accentInk)
            }
        }
    }
}

@Composable
internal fun ListRow(icon: Int, color: Long, title: String, subtitle: String?, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val (tint, bg) = tintPair(color)
    Row(
        Modifier.fillMaxWidth().tap(press = false, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint = tint, background = bg, size = 40.dp, iconSize = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Adam.type.bodyMedium, color = Adam.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Adam.type.small, color = Adam.colors.textMuted, maxLines = 1)
        }
        trailing()
        Spacer(Modifier.width(8.dp))
        Icon(painterResource(R.drawable.ic_drag_indicator), null, tint = Adam.colors.textMuted, modifier = Modifier.size(20.dp))
    }
}

// ───────────────────────────── Categories ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    snapshot: WalletSnapshot,
    onSave: (CategoryEntity) -> Unit,
    onRemove: (CategoryEntity) -> Unit,
    onReorder: (List<CategoryEntity>) -> Unit,
    onBack: () -> Unit,
    initialType: CategoryType = CategoryType.EXPENSE,
    initialEditing: CategoryEntity? = null,
) {
    val language = LocalAppLanguage.current
    var type by rememberSaveable { mutableStateOf(initialType) }
    var editing by remember { mutableStateOf(initialEditing) }
    val list = snapshot.categories(type)
    ReorderPage(
        title = stringResource(R.string.settings_categories),
        items = list,
        key = { it.id },
        onBack = onBack,
        onReorder = onReorder,
        onNew = {
            editing = CategoryEntity(nameAr = "", nameEn = "", icon = "more_horiz", color = CardPalette.Solids.first(), type = type)
        },
        newLabel = stringResource(R.string.cat_new),
        header = {
            Column {
                Segmented(
                    listOf(CategoryType.EXPENSE, CategoryType.INCOME), type,
                    label = { stringResource(if (it == CategoryType.EXPENSE) R.string.cat_expense else R.string.cat_income) },
                    onSelect = { type = it },
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(stringResource(R.string.cat_reorder_hint), style = Adam.type.small, color = Adam.colors.textMuted, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 12.dp))
            }
        },
    ) { cat ->
        ListRow(AppIcons.of(cat.icon), cat.color, cat.name.forTerms(language), cat.name.forUi(language).takeIf { it != cat.name.forTerms(language) }, onClick = { editing = cat })
    }

    editing?.let { cat ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { editing = null }, sheetState = sheetState,
            containerColor = Adam.colors.surface, shape = AdamShapes.sheet,
        ) {
            CategoryEditor(
                cat,
                canRemove = cat.id != 0L && list.size > 1,
                onSave = { onSave(it); editing = null },
                onRemove = { onRemove(cat); editing = null },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditor(cat: CategoryEntity, canRemove: Boolean, onSave: (CategoryEntity) -> Unit, onRemove: () -> Unit) {
    val c = Adam.colors
    var ar by rememberSaveable { mutableStateOf(cat.nameAr) }
    var en by rememberSaveable { mutableStateOf(cat.nameEn) }
    var icon by rememberSaveable { mutableStateOf(cat.icon) }
    var color by rememberSaveable { mutableStateOf(cat.color) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (tint, bg) = tintPair(color)
            IconBadge(AppIcons.of(icon), tint = tint, background = bg, size = 48.dp, iconSize = 24.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(if (cat.id == 0L) R.string.cat_new else R.string.cat_edit), style = Adam.type.h2, color = c.text)
        }
        Spacer(Modifier.height(18.dp))
        AdamTextField(ar, { ar = it.take(30) }, stringResource(R.string.cat_name_ar), rtl = true)
        Spacer(Modifier.height(12.dp))
        AdamTextField(en, { en = it.take(30) }, stringResource(R.string.cat_name_en), rtl = false)

        Text(stringResource(R.string.cat_icon), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcons.categoryIcons.forEach { (key, res) ->
                val on = key == icon
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (on) Color(color) else c.surfaceAlt).tap(pressedScale = 0.9f) { icon = key },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(res), null, tint = if (on) c.bg else c.textSecondary, modifier = Modifier.size(21.dp)) }
            }
        }

        Text(stringResource(R.string.cat_color), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 10.dp))
        ColorRow(CardPalette.Solids, color) { color = it }

        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            stringResource(R.string.action_save),
            onClick = {
                val a = ar.trim().ifEmpty { en.trim() }
                val e = en.trim().ifEmpty { ar.trim() }
                onSave(cat.copy(nameAr = a, nameEn = e, icon = icon, color = color))
            },
            enabled = ar.isNotBlank() || en.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (canRemove) {
            Spacer(Modifier.height(10.dp))
            GhostButton(stringResource(R.string.cat_remove), onRemove, Modifier.fillMaxWidth(), color = c.expense, icon = R.drawable.ic_delete)
            Text(stringResource(R.string.cat_remove_note), style = Adam.type.small, color = c.textMuted, modifier = Modifier.padding(8.dp))
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun ColorRow(colors: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    val c = Adam.colors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(colors) { argb ->
            val on = argb == selected
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .border(2.dp, if (on) c.text else Color.Transparent, CircleShape)
                    .padding(4.dp).clip(CircleShape).background(Color(argb))
                    .tap(pressedScale = 0.9f) { onPick(argb) },
            )
        }
    }
}

// ───────────────────────────── Quick items ─────────────────────────────

/**
 * A row that deletes when swiped to the right and opens the editor when swiped to the left (screen directions,
 * whatever the language). The edit swipe springs back; the delete swipe removes the row.
 */
@Composable
fun SwipeEditDelete(onDelete: () -> Unit, onEdit: () -> Unit, content: @Composable () -> Unit) {
    val c = Adam.colors
    val haptics = rememberHaptics()
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    // In RTL, "start to end" moves right-to-left on screen, so the physical directions swap.
    val rightSwipe = if (rtl) androidx.compose.material3.SwipeToDismissBoxValue.EndToStart else androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
    val leftSwipe = if (rtl) androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd else androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState()
    // Act once when a swipe settles (confirmValueChange can be called several times per gesture).
    androidx.compose.runtime.LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            rightSwipe -> { haptics.reject(); onDelete() }
            leftSwipe -> { haptics.click(); onEdit(); state.snapTo(androidx.compose.material3.SwipeToDismissBoxValue.Settled) }
            else -> Unit
        }
    }
    androidx.compose.material3.SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val target = state.dismissDirection
            val deleting = target == rightSwipe
            val editing = target == leftSwipe
            Row(
                Modifier.fillMaxSize().background(if (deleting) c.expenseSoft else if (editing) c.accentSoft else Color.Transparent).padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Physical left edge shows the delete icon (revealed by a right swipe); right edge shows edit.
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (deleting) Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete), tint = c.expense)
                        Spacer(Modifier.weight(1f))
                        if (editing) Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.action_edit), tint = c.accentInk)
                    }
                }
            }
        },
    ) { Box(Modifier.background(c.surface)) { content() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickItemsScreen(
    snapshot: WalletSnapshot,
    onSave: (QuickItemEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onReorder: (List<QuickItemEntity>) -> Unit,
    onBack: () -> Unit,
    initialEditing: QuickItemEntity? = null,
) {
    val language = LocalAppLanguage.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    var editing by remember { mutableStateOf(initialEditing) }
    ReorderPage(
        title = stringResource(R.string.settings_quick),
        items = snapshot.quickItems,
        key = { it.id },
        onBack = onBack,
        onReorder = onReorder,
        onNew = { editing = QuickItemEntity(nameAr = "", nameEn = "", amountFils = 0, categoryId = null) },
        newLabel = stringResource(R.string.quick_new),
        header = {
            Text(stringResource(R.string.quick_hint), style = Adam.type.small, color = Adam.colors.textMuted, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 14.dp))
        },
        empty = { EmptyState(R.drawable.ic_bolt, stringResource(R.string.quick_empty_title), stringResource(R.string.quick_empty_body)) },
        snackbar = snackbar,
    ) { q ->
        val cat = snapshot.category(q.categoryId)
        // Swipe right = delete (with Undo), swipe left = edit — physical directions, in Arabic too.
        SwipeEditDelete(
            onDelete = {
                onDelete(q.id)
                scope.launch {
                    val r = snackbar.showSnackbar(
                        context.getString(R.string.quick_deleted), actionLabel = context.getString(R.string.action_undo),
                        duration = androidx.compose.material3.SnackbarDuration.Long,
                    )
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) onSave(q)
                }
            },
            onEdit = { editing = q },
        ) {
            ListRow(
                AppIcons.of(cat?.icon), cat?.color ?: 0xFF9AA0A6, q.name.forUi(language), cat?.name?.forTerms(language),
                onClick = { editing = q },
            ) {
                Amount(q.amountFils, color = if (q.type == TxnType.INCOME) Adam.colors.income else Adam.colors.text, peekable = false)
            }
        }
    }

    editing?.let { q ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { editing = null }, sheetState = sheetState,
            containerColor = Adam.colors.surface, shape = AdamShapes.sheet,
        ) {
            QuickItemEditor(
                q, snapshot,
                onSave = { onSave(it); editing = null },
                onDelete = { onDelete(q.id); editing = null },
            )
        }
    }
}

@Composable
fun QuickItemEditor(item: QuickItemEntity, snapshot: WalletSnapshot, onSave: (QuickItemEntity) -> Unit, onDelete: () -> Unit) {
    val c = Adam.colors
    val language = LocalAppLanguage.current
    val arabicUi = language != AppLanguage.ENGLISH
    var name by rememberSaveable { mutableStateOf(item.name.forUi(language)) }
    var type by rememberSaveable { mutableStateOf(if (item.type == TxnType.INCOME) TxnType.INCOME else TxnType.EXPENSE) }
    var input by remember { mutableStateOf(AmountInput.of(item.amountFils)) }
    var categoryId by rememberSaveable { mutableStateOf(item.categoryId) }
    var pocketId by rememberSaveable { mutableStateOf(item.pocketId) }
    var showKeypad by rememberSaveable { mutableStateOf(item.id == 0L) }
    val cats = snapshot.categories(if (type == TxnType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE)
    LaunchedEffect(type) { if (cats.none { it.id == categoryId }) categoryId = cats.firstOrNull()?.id }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (item.id == 0L) R.string.quick_new else R.string.quick_edit), style = Adam.type.h2, color = c.text, modifier = Modifier.weight(1f))
            if (item.id != 0L) RoundIconButton(R.drawable.ic_delete, stringResource(R.string.action_delete), onDelete, tint = c.expense, background = c.expenseSoft)
        }
        Spacer(Modifier.height(16.dp))
        Segmented(
            listOf(TxnType.EXPENSE, TxnType.INCOME), type, label = { typeLabel(it) }, onSelect = { type = it },
            selectedColor = { if (it == TxnType.EXPENSE) c.expense else c.income },
        )
        Spacer(Modifier.height(14.dp))
        AdamTextField(name, { name = it.take(30) }, stringResource(R.string.quick_name))

        Text(stringResource(R.string.quick_amount), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))
        AdamCard(Modifier.fillMaxWidth(), color = c.surfaceAlt, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), onClick = { showKeypad = !showKeypad }) {
            Amount(input.fils, style = Adam.type.amountLarge, peekable = false)
        }
        if (showKeypad) {
            Spacer(Modifier.height(10.dp))
            MoneyKeypad(input, { input = it }, keyHeight = 46.dp)
        }

        Text(stringResource(R.string.quick_category), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cats, key = { it.id }) { cat ->
                Chip(cat.name.forTerms(language), selected = cat.id == categoryId, icon = AppIcons.of(cat.icon), iconTint = Color(cat.color)) { categoryId = cat.id }
            }
        }

        Text(stringResource(R.string.quick_pocket), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Chip(stringResource(R.string.quick_pocket_default), selected = pocketId == null) { pocketId = null } }
            items(snapshot.activePockets, key = { it.id }) { p ->
                Chip(p.name.forTerms(language), selected = pocketId == p.id, icon = AppIcons.of(p.icon), iconTint = Color(p.color)) { pocketId = p.id }
            }
        }

        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            stringResource(R.string.action_save),
            onClick = {
                val n = name.trim()
                // Keep the other language's name in step unless it was set separately.
                val sameBefore = item.nameAr == item.nameEn || item.nameAr.isBlank() || item.nameEn.isBlank()
                val ar = if (arabicUi) n else if (sameBefore) n else item.nameAr
                val en = if (!arabicUi) n else if (sameBefore) n else item.nameEn
                onSave(item.copy(nameAr = ar, nameEn = en, amountFils = input.fils, categoryId = categoryId, pocketId = pocketId, type = type))
            },
            enabled = name.isNotBlank() && input.fils > 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
    }
}
