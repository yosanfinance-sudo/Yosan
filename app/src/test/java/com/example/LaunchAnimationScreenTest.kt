package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.ui.screens.LaunchAnimationScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchAnimationScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testLaunchAnimationScreen_clickSkipsAnimation() {
        var finished = false
        composeTestRule.setContent {
            LaunchAnimationScreen(
                onAnimationFinished = {
                    finished = true
                }
            )
        }

        // Clicking anywhere on the launch screen triggers immediate transition
        composeTestRule.onRoot().performClick()
        assertTrue("Clicking screen should invoke onAnimationFinished", finished)
    }
}
