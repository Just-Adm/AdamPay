package com.adampay.app.domain

import com.adampay.app.core.launch.LaunchRouter
import com.adampay.app.core.launch.LaunchSource
import com.adampay.app.data.db.GoalEntity
import com.adampay.app.domain.layout.BoxId
import com.adampay.app.domain.layout.PageId
import com.adampay.app.domain.layout.PageLayout
import com.adampay.app.domain.plan.GoalProgress
import com.adampay.app.ui.Featured
import com.adampay.app.ui.GoalView
import com.adampay.app.ui.home.GoalLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuickAccessTest {

    /* ---------------- AI Button (double press) ---------------- */

    private fun d(src: LaunchSource, saved: String? = null, learning: Boolean = false) =
        LaunchRouter.decide(src, saved, learning, ownPackage = "com.adampay.app", homePackage = "com.hihonor.android.launcher")

    @Test fun `learns the button and then recognises it, icon taps stay normal`() {
        val button = LaunchSource("com.hihonor.aibutton", fromIcon = false)
        val icon = LaunchSource("com.hihonor.android.launcher", fromIcon = true)
        assertEquals(LaunchRouter.Decision.LEARNED, d(button, learning = true))
        assertEquals(LaunchRouter.Decision.MATCH, d(button, saved = button.signature()))
        assertEquals(LaunchRouter.Decision.APP, d(icon, saved = button.signature()))
        assertEquals(LaunchRouter.Decision.CANNOT_LEARN, d(icon, learning = true))
        // Our own notifications never count as the button.
        assertEquals(LaunchRouter.Decision.APP, d(LaunchSource("com.adampay.app", false), learning = true))
        assertEquals(LaunchRouter.Decision.APP, d(LaunchSource("com.adampay.app", false), saved = "com.adampay.app"))
        val viaLauncher = LaunchSource("com.hihonor.android.launcher", fromIcon = false)
        assertEquals(LaunchRouter.Decision.LEARNED, d(viaLauncher, learning = true))
        assertEquals(LaunchRouter.Decision.APP, d(icon, saved = viaLauncher.signature()))
    }

    /* ---------------- featured goal ---------------- */

    private val today = LocalDate.of(2026, 10, 17)
    private fun goal(id: Long, saved: Long, target: Long) =
        GoalView(GoalEntity(id = id, name = "g$id", icon = "savings", color = 0, targetFils = target, savedFils = saved, createdAt = 0), GoalProgress.of(saved, target, 0.0, today, null))

    @Test fun `four levels, a reached goal is level 4 of 4`() {
        assertEquals(1, GoalLevels.level(0f))
        assertEquals(1, GoalLevels.level(0.249f))
        assertEquals(2, GoalLevels.level(0.25f))
        assertEquals(2, GoalLevels.level(31_500f / 70_191f)) // 44 %
        assertEquals(4, GoalLevels.level(0.99f))
        assertEquals(4, GoalLevels.level(1f))
    }

    @Test fun `featured goal - automatic, chosen, or none`() {
        val reached = goal(1, 5_000, 5_000)
        val game = goal(2, 31_500, 70_191)
        val other = goal(3, 0, 10_000)
        val all = listOf(reached, game, other)
        assertEquals(2L, Featured.pick(all, null)?.goal?.id) // first one not reached yet
        assertEquals(3L, Featured.pick(all, 3)?.goal?.id)
        assertNull(Featured.pick(all, 0))
        assertEquals(2L, Featured.pick(all, 99)?.goal?.id) // deleted choice → automatic
        assertEquals(1L, Featured.pick(listOf(reached), null)?.goal?.id)
        assertNull(Featured.pick(emptyList(), null))
    }

    @Test fun `a saved home layout gets the featured card first, under the balance`() {
        val old = "HOME_POCKETS,HOME_TODAY,HOME_BUDGET,HOME_QUICK,HOME_GOALS,HOME_RECENT|HOME_QUICK"
        val l = PageLayout.decode(PageId.HOME, old)
        assertEquals(BoxId.HOME_FEATURED, l.order.first())
        assertTrue(BoxId.HOME_FEATURED in l.visible)
        assertTrue(BoxId.HOME_QUICK in l.hidden)
        assertEquals(BoxId.HOME_FEATURED, PageLayout.default(PageId.HOME).order.first())
    }
}
