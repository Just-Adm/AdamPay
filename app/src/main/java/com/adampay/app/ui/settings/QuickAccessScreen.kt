package com.adampay.app.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adampay.app.R
import com.adampay.app.core.launch.ButtonLink
import com.adampay.app.ui.components.AdamCard
import com.adampay.app.ui.components.GhostButton
import com.adampay.app.ui.components.IconBadge
import com.adampay.app.ui.components.PrimaryButton
import com.adampay.app.ui.components.SubScreen
import com.adampay.app.ui.theme.Adam
import com.adampay.app.ui.theme.AdamShapes
import kotlinx.coroutines.delay

/** Settings → Quick access: link the AI Button's double press, and how to add widgets and shortcuts. */
@Composable
fun QuickAccessScreen(link: ButtonLink, lockOn: Boolean, onLink: () -> Unit, onUnlink: () -> Unit, onBack: () -> Unit) {
    val c = Adam.colors
    SubScreen(stringResource(R.string.access_title), onBack) {
        Spacer(Modifier.height(6.dp))
        AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(R.drawable.ic_bolt, size = 44.dp, iconSize = 22.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_title), style = Adam.type.title, color = c.text)
                    Text(stringResource(R.string.ai_body), style = Adam.type.small, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(14.dp))
            Step(1, stringResource(R.string.ai_step1))
            Spacer(Modifier.height(8.dp))
            Step(2, stringResource(R.string.ai_step2))
            Spacer(Modifier.height(14.dp))
            Status(link)
            Spacer(Modifier.height(14.dp))
            when (link) {
                is ButtonLink.On -> GhostButton(stringResource(R.string.ai_unlink), onUnlink, Modifier.fillMaxWidth(), color = c.expense)
                is ButtonLink.Learning -> GhostButton(stringResource(R.string.action_cancel), onUnlink, Modifier.fillMaxWidth())
                else -> PrimaryButton(stringResource(R.string.ai_link), onLink, Modifier.fillMaxWidth(), icon = R.drawable.ic_bolt)
            }
        }
        Spacer(Modifier.height(12.dp))
        InfoCard(R.drawable.ic_dashboard_customize, stringResource(R.string.widgets_title), stringResource(R.string.widgets_body))
        Spacer(Modifier.height(12.dp))
        InfoCard(R.drawable.ic_touch_app, stringResource(R.string.shortcuts_title), stringResource(R.string.shortcuts_body))
        if (lockOn) {
            Text(
                stringResource(R.string.access_lock_note), style = Adam.type.small, color = c.textMuted,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 14.dp),
            )
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    val c = Adam.colors
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Text("$n", style = Adam.type.micro, color = c.accentInk)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = Adam.type.small, color = c.textSecondary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Status(link: ButtonLink) {
    val c = Adam.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(link) { while (link is ButtonLink.Learning) { now = System.currentTimeMillis(); delay(500) } }
    val (text, color, icon) = when (link) {
        is ButtonLink.On -> Triple(stringResource(R.string.ai_on), c.income, R.drawable.ic_check_circle)
        is ButtonLink.Learning -> {
            val s = ((link.untilMillis - now).coerceAtLeast(0) + 999) / 1000
            Triple(stringResource(R.string.ai_learning, "%d:%02d".format(java.util.Locale.ROOT, s / 60, s % 60)), c.accentInk, R.drawable.ic_timer)
        }
        ButtonLink.Indistinguishable -> Triple(stringResource(R.string.ai_cannot), c.warn, R.drawable.ic_error)
        ButtonLink.Off -> Triple(stringResource(R.string.ai_off), c.textMuted, R.drawable.ic_info)
    }
    Row(
        Modifier.fillMaxWidth().clip(AdamShapes.tile).background(c.surfaceAlt.copy(alpha = 0.6f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = Adam.type.small, color = color, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoCard(icon: Int, title: String, body: String) {
    val c = Adam.colors
    AdamCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(icon, size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Adam.type.bodyMedium, color = c.text)
                Spacer(Modifier.height(2.dp))
                Text(body, style = Adam.type.small, color = c.textSecondary)
            }
        }
    }
}
