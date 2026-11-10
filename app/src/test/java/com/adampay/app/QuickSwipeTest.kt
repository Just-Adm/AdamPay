package com.adampay.app

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adampay.app.core.locale.ProvideAppLanguage
import com.adampay.app.data.db.QuickItemEntity
import com.adampay.app.data.wallet.Seeds
import com.adampay.app.data.wallet.WalletSnapshot
import com.adampay.app.domain.model.AppLanguage
import com.adampay.app.ui.settings.QuickItemsScreen
import com.adampay.app.ui.theme.AdamTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Quick entries settings: swipe right deletes, swipe left opens the editor, in Arabic (RTL) and English. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h802dp-xhdpi", application = Application::class)
class QuickSwipeTest {

    @get:Rule val compose = createComposeRule()

    private val categories = Seeds.categories.mapIndexed { i, c -> c.copy(id = 100L + i) }
    private val quick = Seeds.quickItems(categories).mapIndexed { i, q -> q.copy(id = 200L + i) }
    private val snapshot = WalletSnapshot(Seeds.pockets.mapIndexed { i, p -> p.copy(id = i + 1L) }, emptyMap(), categories, quick)

    private fun run(language: AppLanguage, label: String, swipe: String): Pair<List<Long>, List<QuickItemEntity>> {
        val deleted = mutableListOf<Long>()
        val saved = mutableListOf<QuickItemEntity>()
        compose.setContent {
            ProvideAppLanguage(language) {
                AdamTheme { QuickItemsScreen(snapshot, onSave = { saved += it }, onDelete = { deleted += it }, onReorder = {}, onBack = {}) }
            }
        }
        compose.onNodeWithText(label, substring = true).performTouchInput { if (swipe == "right") swipeRight() else swipeLeft() }
        compose.waitForIdle()
        return deleted to saved
    }

    @Test fun `english - swipe right deletes`() {
        val (deleted, _) = run(AppLanguage.ENGLISH, "Service taxi", "right")
        assertEquals(listOf(200L), deleted)
    }

    @Test fun `english - swipe left edits, nothing deleted`() {
        val (deleted, _) = run(AppLanguage.ENGLISH, "Service taxi", "left")
        assertTrue(deleted.isEmpty())
        compose.onNodeWithText("Edit quick entry").assertExists()
    }

    @Test fun `arabic - swipe right still deletes`() {
        val (deleted, _) = run(AppLanguage.ARABIC, "سرفيس", "right")
        assertEquals(listOf(200L), deleted)
    }

    @Test fun `arabic - swipe left still edits`() {
        val (deleted, _) = run(AppLanguage.ARABIC, "سرفيس", "left")
        assertTrue(deleted.isEmpty())
        compose.onNodeWithText("تعديل الإدخال السريع").assertExists()
    }
}
