package com.example.appdev

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests that launch the real app on a device or emulator and walk the six sections.
 * Run with `gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun packageNameMatchesApplicationId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.heatguard.pro", context.packageName)
    }

    @Test
    fun allSixSectionsAreReachableFromTheBottomBar() {
        // Tab label -> text that only that screen shows.
        val sections = listOf(
            "Live" to "Live Analytics",
            "Coach" to "Cooling Coach",
            "History" to "Incident history",
            "Insights" to "Patterns from incidents stored on this device",
            "Settings" to "Stored on this device only",
            "Dashboard" to "Battery thermal monitor",
        )
        sections.forEach { (tab, marker) ->
            compose.onNodeWithText(tab).performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithTextCount(marker) > 0
            }
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextCount(text: String): Int =
        onAllNodesWithText(text).fetchSemanticsNodes().size
}
