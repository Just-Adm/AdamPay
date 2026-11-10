package com.adampay.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.LocalHapticsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Haptics for key moments (save, delete, count done), honouring the user's vibration setting. */
@Stable
class Haptics(private val view: View, private val enabled: Boolean) {
    private fun play(constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }

    /** Keypad keys, chips, drag steps. */
    fun tick() = play(HapticFeedbackConstants.KEYBOARD_TAP)
    fun click() = play(HapticFeedbackConstants.CONTEXT_CLICK)
    fun longPress() = play(HapticFeedbackConstants.LONG_PRESS)

    /** A saved entry, a finished count. */
    fun confirm() = play(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)

    /** A delete, an invalid key. */
    fun reject() = play(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { Haptics(view, enabled) }
}

/** Adam Pay's press: a soft spring sink (0.96) that bounces back. */
fun Modifier.springPress(source: MutableInteractionSource, pressedScale: Float = 0.96f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Click + long-click with spring press, accent ripple and haptics. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tap(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    press: Boolean = true,
    pressedScale: Float = 0.96f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    this
        .then(if (press) Modifier.springPress(source, pressedScale) else Modifier)
        .combinedClickable(
            interactionSource = source,
            indication = ripple(color = Adam.colors.accent),
            enabled = enabled,
            role = role,
            onLongClick = onLongClick?.let { lc -> { haptics.longPress(); lc() } },
            onClick = onClick,
        )
}

val Decelerate = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

/** Fade + rise entry, staggered by [index]. Skipped in screenshot tests. */
fun Modifier.enter(index: Int = 0, rise: Int = 16): Modifier = composed {
    if (LocalInspectionMode.current) return@composed this
    val alpha = remember { Animatable(0f) }
    val offset = remember { Animatable(1f) }
    val risePx = with(LocalDensity.current) { rise.dp.toPx() }
    LaunchedEffect(Unit) {
        delay(45L * index)
        launch { alpha.animateTo(1f, tween(360, easing = Decelerate)) }
        launch { offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) }
    }
    graphicsLayer {
        this.alpha = alpha.value
        translationY = offset.value * risePx
    }
}
