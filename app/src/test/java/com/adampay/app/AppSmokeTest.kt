package com.adampay.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Boots the real app (Hilt graph, Room, DataStore, navigation, theme, language) on a fresh install and
 * walks the core flow: first run → starting cash → log an expense in the sheet → it shows on Home →
 * open Settings and come back. One flow, because DataStore is a per-process singleton.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h802dp-xhdpi")
class AppSmokeTest {

    @get:Rule val compose = createAndroidComposeRule<HomeActivity>()

    private fun waitFor(text: String, substring: Boolean = false) {
        try {
            compose.waitUntil(15_000) { compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            println(compose.onAllNodes(androidx.compose.ui.test.isRoot()).printToString(maxDepth = 60))
            throw e
        }
    }

    private fun waitForDescription(text: String) =
        compose.waitUntil(15_000) { compose.onAllNodes(hasContentDescription(text)).fetchSemanticsNodes().isNotEmpty() }

    /** Keypad keys only (clickable), not digits of a balance drawn behind the sheet. */
    private fun tapKey(k: String) = compose.onAllNodes(hasText(k) and hasClickAction()).onFirst().performClick()

    @Test fun `fresh install - first run, log an expense, see it on home`() {
        // Setup step 1: language, in the default mixed mode (Arabic UI). Switching is instant, same activity.
        waitFor("مرحباً بك")
        val activity = compose.activity
        compose.onNodeWithText("English").performClick()
        waitFor("Let's set up your wallet in three short steps.")
        assertSame(activity, compose.activity)
        compose.onNodeWithText("Continue").performClick()

        // Step 2: the name the app uses.
        waitFor("What should we call you?")
        compose.onNode(hasSetTextAction()).performTextInput("Adam")
        compose.onNodeWithText("Continue").performClick()

        // Step 3: opening balance, "In my pocket" selected; type 20.
        waitFor("Opening balance")
        waitFor("In my pocket")
        tapKey("2"); tapKey("0")
        compose.onNodeWithText("Finish setup").performClick()

        // Home greets by name and shows the opening balance.
        waitFor("Adam")
        waitFor("Total balance")
        waitFor("20.000", substring = true)
        waitFor("No transactions today.")

        // Log 1.5 JD on Food.
        compose.onAllNodes(hasContentDescription("New transaction")).onFirst().performClick()
        waitFor("Expense")
        tapKey("1"); tapKey("."); tapKey("5")
        compose.onAllNodesWithText("Food").onFirst().performClick()
        compose.onNodeWithText("Save", substring = true).performClick()

        // Back on Home: the balance, the pocket card and today's card follow.
        waitFor("18.500", substring = true)
        waitFor("1 transaction today")

        // Settings tab opens and its rows don't navigate by themselves.
        compose.onAllNodes(hasContentDescription("Settings")).onFirst().performClick()
        waitFor("Amount format")
        compose.onNodeWithText("Amount format").performClick()
        waitFor("Decimal places")
        compose.activity.onBackPressedDispatcher.onBackPressed()
        waitFor("Default pocket")
        waitForDescription("Home")

        // Run setup again: answers are filled in; the new opening balance replaces the old one.
        compose.onNodeWithText("Run setup again").performClick()
        compose.onNodeWithText("Continue").performClick()
        waitFor("Welcome")
        compose.onNodeWithText("Continue").performClick()
        waitFor("Adam")
        compose.onNodeWithText("Continue").performClick()
        waitFor("Opening balance")
        tapKey("5") // 20 → 205
        compose.onNodeWithText("Finish setup").performClick()
        waitFor("203.500", substring = true) // 205 − 1.5, not 20 + 205 − 1.5

        // Count the pocket: 200 counted, 3.500 missing, marked as lost → the balance becomes 200.
        compose.onAllNodes(hasText("In my pocket", substring = true) and hasClickAction()).onFirst().performClick()
        waitFor("Count cash")
        compose.onAllNodesWithText("Count cash").onFirst().performClick()
        waitFor("Enter amount")
        compose.onNodeWithText("Enter amount").performClick()
        tapKey("2"); tapKey("0"); tapKey("0")
        compose.onNodeWithText("Compare").performClick()
        waitFor("Amount missing")
        compose.onNodeWithText("Lost / don't remember").performClick()
        compose.onNodeWithText("Save count").performClick()
        waitFor("Total balance")
        waitFor("200.000", substring = true)

        // App lock: choose a PIN (twice), leave the app, come back → the lock screen; a wrong PIN is refused.
        compose.onAllNodes(hasContentDescription("Settings")).onFirst().performClick()
        waitFor("App lock")
        compose.onNodeWithText("App lock").performClick()
        compose.onNodeWithText("Lock with a PIN").performClick()
        waitFor("Choose a 4-digit PIN")
        "1234".forEach { tapKey(it.toString()) }
        waitFor("Enter the PIN again")
        "1234".forEach { tapKey(it.toString()) }
        waitFor("Change PIN")
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        waitFor("Welcome back, Adam")
        "0000".forEach { tapKey(it.toString()) }
        waitFor("Incorrect PIN", substring = true)
        waitFor("4 attempts left", substring = true)
        "1234".forEach { tapKey(it.toString()) }
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Welcome back, Adam")).fetchSemanticsNodes().isEmpty() }
        waitFor("Change PIN")
    }
}
