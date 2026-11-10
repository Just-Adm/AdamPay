package com.adampay.app

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adampay.app.core.launch.ButtonLink
import com.adampay.app.core.launch.LaunchRouter
import com.adampay.app.ui.quicklog.QuickLogActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The launcher entry: app shortcuts go to Quick Log / the cash count, a learned AI Button press to Quick Log. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class LaunchRoutingTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun launch(intent: Intent): Intent? {
        Robolectric.buildActivity(MainActivity::class.java, intent).create().get()
        return shadowOf(app).nextStartedActivity
    }

    @Test fun `shortcuts open quick log or the cash count`() {
        val expense = launch(Intent(MainActivity.ACTION_EXPENSE).setClass(app, MainActivity::class.java))!!
        assertEquals(QuickLogActivity::class.java.name, expense.component?.className)
        assertEquals("EXPENSE", expense.getStringExtra(QuickLogActivity.EXTRA_TYPE))
        val income = launch(Intent(MainActivity.ACTION_INCOME).setClass(app, MainActivity::class.java))!!
        assertEquals("INCOME", income.getStringExtra(QuickLogActivity.EXTRA_TYPE))
        val count = launch(Intent(MainActivity.ACTION_COUNT).setClass(app, MainActivity::class.java))!!
        assertEquals(HomeActivity::class.java.name, count.component?.className)
        assertEquals(HomeActivity.OPEN_COUNT, count.getStringExtra(HomeActivity.EXTRA_OPEN))
    }

    @Test fun `while learning, the next launch links the button and opens quick log, then plain launches open the app`() {
        LaunchRouter.unlink(app)
        val plain = launch(Intent(Intent.ACTION_MAIN).setClass(app, MainActivity::class.java))!!
        assertEquals(HomeActivity::class.java.name, plain.component?.className)
        LaunchRouter.startLearning(app)
        assertTrue(LaunchRouter.refresh(app) is ButtonLink.Learning)
        val pressed = launch(Intent(Intent.ACTION_MAIN).setClass(app, MainActivity::class.java))!!
        assertEquals(QuickLogActivity::class.java.name, pressed.component?.className)
        assertTrue(LaunchRouter.refresh(app) is ButtonLink.On)
        // Same signature again (no referrer, no icon bounds in this test) → Quick Log.
        val again = launch(Intent(Intent.ACTION_MAIN).setClass(app, MainActivity::class.java))!!
        assertEquals(QuickLogActivity::class.java.name, again.component?.className)
        // An icon tap (bounds present) still opens the app.
        val icon = launch(Intent(Intent.ACTION_MAIN).setClass(app, MainActivity::class.java).apply { sourceBounds = android.graphics.Rect(0, 0, 10, 10) })!!
        assertEquals(HomeActivity::class.java.name, icon.component?.className)
        LaunchRouter.unlink(app)
    }
}
