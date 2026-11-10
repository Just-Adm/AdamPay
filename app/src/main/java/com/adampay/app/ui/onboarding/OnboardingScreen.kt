package com.adampay.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adampay.app.R
import com.adampay.app.core.locale.LocalAppLanguage
import com.adampay.app.data.wallet.WalletSnapshot
import com.adampay.app.data.wallet.name
import com.adampay.app.domain.model.AppLanguage
import com.adampay.app.domain.money.AmountInput
import com.adampay.app.ui.components.AdamCard
import com.adampay.app.ui.components.AdamDivider
import com.adampay.app.ui.components.AdamTextField
import com.adampay.app.ui.components.Amount
import com.adampay.app.ui.components.AppIcons
import com.adampay.app.ui.components.MoneyKeypad
import com.adampay.app.ui.components.PrimaryButton
import com.adampay.app.ui.components.RoundIconButton
import com.adampay.app.ui.components.rememberHaptics
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.settings.languageName
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes

private const val STEPS = 3

/**
 * Setup in three formal steps: language, the name the app uses, opening balance per pocket.
 * Runs on first launch and again from Settings ([rerun]): answers are filled in and the close
 * button leaves without changes.
 */
@Composable
fun OnboardingScreen(
    snapshot: WalletSnapshot,
    onLanguage: (AppLanguage) -> Unit,
    onFinish: (name: String, opening: Map<Long, Long>) -> Unit,
    initialStep: Int = 0,
    initialName: String = "",
    initialAmounts: Map<Long, Long> = emptyMap(),
    rerun: Boolean = false,
    onCancel: () -> Unit = {},
) {
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var amounts by remember(initialAmounts) { mutableStateOf(initialAmounts) }
    BackHandler(enabled = step > 0 || rerun) { if (step > 0) step-- else onCancel() }

    Column(
        Modifier.fillMaxSize().background(Adam.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
            .padding(horizontal = 20.dp),
    ) {
        TopBar(step, rerun, onBack = { step-- }, onClose = onCancel)
        AnimatedContent(
            targetState = step,
            transitionSpec = { (slideInHorizontally { it / 8 } + fadeIn(tween(240))) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.weight(1f),
            label = "setup",
        ) { s ->
            when (s) {
                0 -> LanguageStep(onLanguage) { step = 1 }
                1 -> NameStep(name, { name = it }) { step = 2 }
                else -> OpeningStep(snapshot, amounts, { amounts = it }) { onFinish(name.trim(), amounts.filterValues { it > 0 }) }
            }
        }
    }
}

@Composable
private fun TopBar(step: Int, rerun: Boolean, onBack: () -> Unit, onClose: () -> Unit) {
    val c = Adam.colors
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            step > 0 -> RoundIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack, background = Color.Transparent)
            rerun -> RoundIconButton(R.drawable.ic_close, stringResource(R.string.action_close), onClose, background = Color.Transparent)
            else -> Spacer(Modifier.size(42.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.ob_step, step + 1, STEPS), style = Adam.type.smallMedium, color = c.textSecondary)
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(STEPS) { i ->
            val color by animateColorAsState(if (i <= step) c.accent else c.border, tween(250), label = "progress")
            Box(Modifier.weight(1f).height(4.dp).clip(AdamShapes.pill).background(color))
        }
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Text(title, style = Adam.type.h1, color = Adam.colors.text)
    Spacer(Modifier.height(8.dp))
    Text(body, style = Adam.type.body, color = Adam.colors.textSecondary)
    Spacer(Modifier.height(26.dp))
}

@Composable
private fun StepColumn(button: String, enabled: Boolean, onNext: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = content)
        PrimaryButton(button, onNext, Modifier.fillMaxWidth().padding(vertical = 14.dp), enabled = enabled)
    }
}

@Composable
private fun LanguageStep(onLanguage: (AppLanguage) -> Unit, onNext: () -> Unit) {
    val c = Adam.colors
    val haptics = rememberHaptics()
    val current = LocalAppLanguage.current
    StepColumn(stringResource(R.string.action_continue), true, onNext) {
        StepTitle(stringResource(R.string.ob_welcome_title), stringResource(R.string.ob_welcome_body))
        Text(stringResource(R.string.ob_language), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.padding(bottom = 10.dp))
        AdamCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
            AppLanguage.entries.forEachIndexed { i, l ->
                if (i > 0) AdamDivider(Modifier.padding(horizontal = 12.dp))
                val on = l == current
                Row(
                    Modifier.fillMaxWidth().clip(AdamShapes.tile).tap(press = false) { haptics.tick(); onLanguage(l) }.padding(horizontal = 12.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(languageName(l), style = Adam.type.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
                    RadioDot(on)
                }
            }
        }
    }
}

@Composable
private fun RadioDot(on: Boolean) {
    val c = Adam.colors
    Box(
        Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (on) c.accent else c.borderStrong, CircleShape).padding(5.dp)
            .clip(CircleShape).background(if (on) c.accent else Color.Transparent),
    )
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, onNext: () -> Unit) {
    StepColumn(stringResource(R.string.action_continue), name.isNotBlank(), onNext) {
        StepTitle(stringResource(R.string.ob_name_title), stringResource(R.string.ob_name_body))
        AdamTextField(name, { onName(it.take(30)) }, stringResource(R.string.ob_name_label), placeholder = stringResource(R.string.ob_name_hint))
        Spacer(Modifier.windowInsetsPadding(WindowInsets.ime))
    }
}

@Composable
private fun OpeningStep(snapshot: WalletSnapshot, amounts: Map<Long, Long>, onAmounts: (Map<Long, Long>) -> Unit, onFinish: () -> Unit) {
    val c = Adam.colors
    val language = LocalAppLanguage.current
    val pockets = snapshot.activePockets
    var selected by rememberSaveable { mutableStateOf(pockets.firstOrNull()?.id) }
    var typing by remember(selected) { mutableStateOf(AmountInput.of(amounts[selected] ?: 0)) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepTitle(stringResource(R.string.ob_cash_title), stringResource(R.string.ob_cash_body))
            AdamCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
                pockets.forEachIndexed { i, p ->
                    if (i > 0) AdamDivider(Modifier.padding(horizontal = 12.dp))
                    val on = p.id == selected
                    val bg by animateColorAsState(if (on) c.accentSoft else Color.Transparent, tween(160), label = "obRow")
                    Row(
                        Modifier.fillMaxWidth().clip(AdamShapes.tile).background(bg).tap(press = false) { selected = p.id }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(p.color), Color(p.color2 ?: p.color)))),
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(AppIcons.of(p.icon)), null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Text(p.name.forTerms(language), style = Adam.type.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
                        Amount(amounts[p.id] ?: 0, color = if ((amounts[p.id] ?: 0) > 0) c.text else c.textMuted, peekable = false)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ob_cash_total), style = Adam.type.smallMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
                Amount(amounts.values.sum(), style = Adam.type.amountLarge.copy(fontSize = 22.sp), labelColor = c.accentInk, peekable = false)
            }
        }
        MoneyKeypad(typing, { next ->
            typing = next
            selected?.let { id -> onAmounts(amounts + (id to next.fils)) }
        }, keyHeight = 48.dp)
        PrimaryButton(stringResource(R.string.ob_finish), onFinish, Modifier.fillMaxWidth().padding(vertical = 14.dp))
    }
}
