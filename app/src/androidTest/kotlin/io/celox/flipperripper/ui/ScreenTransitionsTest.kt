package io.celox.flipperripper.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.width
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.ui.motion.ScreenMotion
import io.celox.flipperripper.ui.motion.ScreenTransitions
import io.celox.flipperripper.ui.theme.FlipperTheme
import org.junit.Rule
import org.junit.Test

/**
 * Drives a two-screen NavHost through [ScreenTransitions] with a manual clock and samples where the
 * incoming screen *is* on each frame. That turns "perfectly animated" into numbers: the screen must
 * start offset by the slide fraction, move every frame, settle at 0 on the spring — and, under reduced
 * motion, be in place on the very first frame.
 */
class ScreenTransitionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var nav: NavHostController

    private fun content(reduceMotion: Boolean) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipperTheme {
                nav = rememberNavController()
                val motion = MaterialTheme.motionScheme
                val transitions = remember { ScreenTransitions(motion, reduceMotion) }
                NavHost(
                    navController = nav,
                    startDestination = "a",
                    enterTransition = { transitions.tabEnter(forward = true) },
                    exitTransition = { transitions.tabExit(forward = true) },
                ) {
                    composable("a") { Box(Modifier.fillMaxSize().testTag("a")) }
                    composable("b") { Box(Modifier.fillMaxSize().testTag("b")) }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun sampleIncomingLeft(frames: Int): List<Float> {
        composeRule.runOnUiThread { nav.navigate("b") }
        // The destination enters the composition on the next frame; sample from the first frame in
        // which it exists (the transition's first frame).
        repeat(MAX_COMPOSE_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            if (composeRule.onAllNodesWithTag("b").fetchSemanticsNodes().isNotEmpty()) return@repeat
        }
        return (1..frames).map {
            val left = composeRule.onNodeWithTag("b").getUnclippedBoundsInRoot().left.value
            composeRule.mainClock.advanceTimeByFrame()
            left
        }
    }

    @Test
    fun incomingTabSlidesInFromTheSideAndSettlesOnTheSpring() {
        content(reduceMotion = false)
        val rootWidth = composeRule.onRoot().getUnclippedBoundsInRoot().width.value
        val samples = sampleIncomingLeft(frames = 60)
        Log.i(TAG, "root width ${rootWidth}dp; incoming left per ${FRAME_MS}ms frame: $samples")

        // Starts to the right (forward = arrives from the right), by roughly the slide fraction…
        assertThat(samples.first()).isGreaterThan(0f)
        assertThat(samples.first()).isLessThan(rootWidth * ScreenMotion.TAB_SLIDE_FRACTION + 1f)
        // …is genuinely in motion (not a snap) — several distinct positions on the way…
        assertThat(samples.distinct().size).isGreaterThan(5)
        // …and comes to rest at 0 within a second (60 frames).
        assertThat(samples.last()).isWithin(0.5f).of(0f)
        // Expressive spatial spring, ζ = 0.8: allowed (and expected) to overshoot slightly past 0.
        val minimum = samples.minOrNull() ?: 0f
        Log.i(TAG, "overshoot past target: ${minimum}dp")
        assertThat(minimum).isGreaterThan(-rootWidth * ScreenMotion.TAB_SLIDE_FRACTION)
    }

    @Test
    fun reducedMotionPlacesTheScreenOnTheFirstFrame() {
        content(reduceMotion = true)
        val samples = sampleIncomingLeft(frames = 3)
        Log.i(TAG, "reduced motion, incoming left per frame: $samples")
        samples.forEach { assertThat(it).isWithin(0.5f).of(0f) }
    }

    private companion object {
        const val TAG = "ScreenTransitionsTest"
        const val FRAME_MS = 16L
        const val MAX_COMPOSE_FRAMES = 5
    }
}
