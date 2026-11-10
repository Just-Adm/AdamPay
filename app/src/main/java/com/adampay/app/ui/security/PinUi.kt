package com.adampay.app.ui.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adampay.app.R
import com.adampay.app.core.security.Biometrics
import com.adampay.app.domain.security.Pin
import com.adampay.app.domain.security.PinCheck
import com.adampay.app.ui.components.IconBadge
import com.adampay.app.ui.components.SubScreen
import com.adampay.app.ui.components.rememberHaptics
import com.adampay.app.ui.components.tap
import com.adampay.app.ui.theme.Adam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Four dots that fill as digits are typed; they shake on a wrong PIN. */
@Composable
fun PinDots(filled: Int, error: Boolean, shake: Animatable<Float, *>, modifier: Modifier = Modifier) {
    val c = Adam.colors
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier.offset(x = shake.value.dp).semantics { contentDescription = "$filled / ${Pin.LENGTH}" },
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            repeat(Pin.LENGTH) { i ->
                val on = i < filled
                val scale by animateFloatAsState(if (on) 1f else 0.8f, spring(dampingRatio = 0.5f), label = "dot")
                Box(
                    Modifier.size(16.dp).scale(scale).clip(CircleShape)
                        .background(if (error) c.expense else if (on) c.accent else Color.Transparent)
                        .border(1.6.dp, if (error) c.expense else if (on) c.accent else c.borderStrong, CircleShape),
                )
            }
        }
    }
}

/** 1–9, [fingerprint], 0, backspace — always laid out like a phone keypad. */
@Composable
fun PinPad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometric: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    val c = Adam.colors
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.widthIn(max = 320.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { d -> DigitKey(d, enabled) { haptics.tick(); onDigit(d) } }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                if (onBiometric != null) {
                    IconKey(R.drawable.ic_fingerprint, stringResource(R.string.biometric_button), c.accentInk, enabled) { haptics.click(); onBiometric() }
                } else Spacer(Modifier.size(KEY))
                DigitKey(0, enabled) { haptics.tick(); onDigit(0) }
                IconKey(R.drawable.ic_backspace, stringResource(R.string.key_backspace), c.textSecondary, enabled) { haptics.tick(); onBackspace() }
            }
        }
    }
}

private val KEY = 72.dp

@Composable
private fun DigitKey(d: Int, enabled: Boolean, onClick: () -> Unit) {
    val c = Adam.colors
    Box(
        Modifier.size(KEY).clip(CircleShape)
            .background(c.surfaceAlt.copy(alpha = if (c.isDark) 0.75f else 0.95f))
            .tap(enabled = enabled, pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(d.toString(), style = Adam.type.amountLarge.copy(fontSize = 28.sp), color = if (enabled) c.text else c.textMuted)
    }
}

@Composable
private fun IconKey(icon: Int, description: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(KEY).clip(CircleShape).tap(enabled = enabled, pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = tint, modifier = Modifier.size(28.dp))
    }
}

/** "0:30" until [until] (wall clock). */
@Composable
private fun rememberCountdown(until: Long): String? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        while (System.currentTimeMillis() < until) { now = System.currentTimeMillis(); delay(250) }
        now = System.currentTimeMillis()
    }
    if (now >= until) return null
    val s = ((until - now) + 999) / 1000
    return "%d:%02d".format(java.util.Locale.ROOT, s / 60, s % 60)
}

/**
 * Typing a PIN: title, subtitle or error line, dots, keypad. Calls [onComplete] with the four digits;
 * it answers null (accepted) or an error to show, after which the dots clear.
 */
@Composable
fun PinEntry(
    title: String,
    subtitle: String?,
    onComplete: suspend (String) -> String?,
    modifier: Modifier = Modifier,
    waitUntil: Long = 0,
    initialError: String? = null,
    onBiometric: (() -> Unit)? = null,
    footer: @Composable () -> Unit = {},
) {
    val c = Adam.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(initialError) }
    var busy by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val countdown = if (waitUntil > 0) rememberCountdown(waitUntil) else null
    val waiting = countdown != null

    fun submit(p: String) {
        busy = true
        scope.launch {
            val err = onComplete(p)
            if (err != null) {
                haptics.reject()
                error = err
                pin = ""
                busy = false
                // The red, empty dots shake; typing can start again straight away.
                for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, tween(45))
            }
            busy = false
        }
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(R.drawable.ic_lock, size = 64.dp, iconSize = 30.dp)
        Spacer(Modifier.height(18.dp))
        Text(title, style = Adam.type.h2, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        val line = when {
            waiting -> stringResource(R.string.pin_wait, countdown!!)
            !error.isNullOrEmpty() -> error
            else -> subtitle
        }
        Text(
            line.orEmpty(), style = Adam.type.small, textAlign = TextAlign.Center,
            color = if (waiting || error != null) c.expense else c.textSecondary,
            modifier = Modifier.padding(horizontal = 24.dp).height(40.dp),
        )
        Spacer(Modifier.height(10.dp))
        PinDots(pin.length, error != null && pin.isEmpty(), shake)
        Spacer(Modifier.height(36.dp))
        PinPad(
            onDigit = { d ->
                if (busy || waiting || pin.length >= Pin.LENGTH) return@PinPad
                if (pin.isEmpty()) error = null
                pin += d
                if (pin.length == Pin.LENGTH) submit(pin)
            },
            onBackspace = { if (!busy) pin = pin.dropLast(1) },
            onBiometric = onBiometric,
            enabled = !waiting,
        )
        Spacer(Modifier.height(12.dp))
        footer()
    }
}

/** Shown over the whole app while it is locked. */
@Composable
fun LockScreen(
    name: String,
    biometric: Boolean,
    waitUntil: Long,
    onCheck: suspend (String) -> PinCheck,
    onBiometricOk: () -> Unit,
    biometricAvailable: Boolean? = null,
) {
    val c = Adam.colors
    val context = LocalContext.current
    val title = stringResource(R.string.biometric_title)
    val cancel = stringResource(R.string.biometric_use_pin)
    val wrong = stringResource(R.string.pin_wrong)
    val resources = context.resources
    val canBio = biometric && (biometricAvailable ?: remember { Biometrics.available(context) })
    var forgot by remember { mutableStateOf(false) }
    val askFinger: () -> Unit = { Biometrics.prompt(context, title, cancel) { ok -> if (ok) onBiometricOk() } }
    // Offer the fingerprint straight away, like banking apps.
    LaunchedEffect(Unit) { if (canBio && biometricAvailable == null) askFinger() }

    Box(
        Modifier.fillMaxSize().background(c.bg).tap(press = false) {}
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center,
    ) {
        PinEntry(
            title = if (name.isBlank()) stringResource(R.string.lock_welcome_plain) else stringResource(R.string.lock_welcome, name),
            subtitle = stringResource(R.string.pin_enter),
            waitUntil = waitUntil,
            onComplete = { pin ->
                when (val r = onCheck(pin)) {
                    PinCheck.Ok -> null
                    is PinCheck.Wait -> ""
                    is PinCheck.Wrong -> if (r.waitUntil > 0) wrong
                    else wrong + " · " + resources.getQuantityString(R.plurals.pin_tries_left, r.triesLeft, r.triesLeft)
                }
            },
            onBiometric = if (canBio) askFinger else null,
            footer = {
                TextButton(onClick = { forgot = true }) { Text(stringResource(R.string.pin_forgot), style = Adam.type.smallMedium, color = c.accentInk) }
            },
        )
    }
    if (forgot) {
        AlertDialog(
            onDismissRequest = { forgot = false },
            containerColor = c.surfaceHigh,
            title = { Text(stringResource(R.string.pin_forgot), style = Adam.type.title, color = c.text) },
            text = { Text(stringResource(R.string.pin_forgot_body), style = Adam.type.body, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { forgot = false }) { Text(stringResource(R.string.action_ok), color = c.accentInk) } },
        )
    }
}

enum class PinFlow { SET, CHANGE, DISABLE }

/**
 * Settings flows around the PIN: set a new one (twice), change it (current first) or turn the lock
 * off (current PIN). [onCheck] verifies the current PIN; [onSet] stores a new one; [onDisable] turns off.
 */
@Composable
fun PinFlowScreen(
    flow: PinFlow,
    waitUntil: Long,
    onCheck: suspend (String) -> PinCheck,
    onSet: suspend (String) -> Unit,
    onDisable: suspend () -> Unit,
    onDone: (PinFlow) -> Unit,
    onBack: () -> Unit,
) {
    val resources = LocalContext.current.resources
    val wrong = stringResource(R.string.pin_wrong)
    val mismatch = stringResource(R.string.pin_mismatch)
    // Steps: 0 = current PIN, 1 = new PIN, 2 = repeat it.
    var step by remember { mutableStateOf(if (flow == PinFlow.SET) 1 else 0) }
    var first by remember { mutableStateOf("") }
    var mismatchShown by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    SubScreen(stringResource(if (flow == PinFlow.DISABLE) R.string.security_title else if (flow == PinFlow.CHANGE) R.string.security_change_pin else R.string.security_lock), onBack, scroll = false) {
        Spacer(Modifier.height(28.dp))
        // A new PinEntry per step, so the dots and error line start clean.
        when (step) {
            0 -> PinEntry(
                title = stringResource(R.string.pin_current),
                subtitle = stringResource(if (flow == PinFlow.DISABLE) R.string.pin_sub_disable else R.string.pin_sub_change),
                waitUntil = waitUntil,
                onComplete = { pin ->
                    when (val r = onCheck(pin)) {
                        PinCheck.Ok -> {
                            if (flow == PinFlow.DISABLE) { onDisable(); withContext(Dispatchers.Main.immediate) { onDone(flow) } } else step = 1
                            null
                        }
                        is PinCheck.Wait -> ""
                        is PinCheck.Wrong -> if (r.waitUntil > 0) wrong
                        else wrong + " · " + resources.getQuantityString(R.plurals.pin_tries_left, r.triesLeft, r.triesLeft)
                    }
                },
            )
            1 -> Box(Modifier.fillMaxWidth()) {
                PinEntry(
                    title = stringResource(R.string.pin_create),
                    subtitle = stringResource(R.string.pin_create_sub),
                    initialError = if (mismatchShown) mismatch else null,
                    onComplete = { pin -> first = pin; mismatchShown = false; step = 2; null },
                )
            }
            else -> Box(Modifier.fillMaxWidth()) {
                PinEntry(
                    title = stringResource(R.string.pin_confirm),
                    subtitle = stringResource(R.string.pin_confirm_sub),
                    onComplete = { pin ->
                        if (pin == first) { onSet(pin); withContext(Dispatchers.Main.immediate) { onDone(flow) }; null }
                        else { haptics.reject(); delay(250); first = ""; mismatchShown = true; step = 1; null }
                    },
                )
            }
        }
    }
}
