package com.dualshield.phone.core

import com.dualshield.phone.core.incall.SwipeCommit
import com.dualshield.phone.core.incall.SwipeOutcome
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that make answering a call a deliberate act.
 *
 * Every case here is one of the handoff's acceptance tests for the incoming-call screen,
 * reduced to the part a unit test can actually decide. Whether a pocket can reach the
 * threshold is a device question; whether a short drag, a repeat touch or an already-sent
 * action can fire a second one is not, and those are the cases that turn into a call
 * answered or rejected that nobody meant.
 */
class SwipeCommitTest {

    private val travel = 300f
    private val threshold = SwipeCommit.thresholdPx(travel, minDistancePx = 100f)

    @Test
    fun `a full drag right answers and a full drag left rejects`() {
        assertEquals(SwipeOutcome.ANSWER, SwipeCommit.outcome(travel, threshold, false))
        assertEquals(SwipeOutcome.REJECT, SwipeCommit.outcome(-travel, threshold, false))
    }

    @Test
    fun `a partial drag does nothing, in either direction`() {
        // Acceptance test 4. "Nearly" must be indistinguishable from "not at all": there is
        // no weaker action for a drag that fell short.
        listOf(0f, 1f, threshold / 2, threshold - 0.01f).forEach { distance ->
            assertEquals(
                "a drag of $distance must not act",
                SwipeOutcome.NONE,
                SwipeCommit.outcome(distance, threshold, false),
            )
            assertEquals(
                "and neither must $distance the other way",
                SwipeOutcome.NONE,
                SwipeCommit.outcome(-distance, threshold, false),
            )
        }
    }

    @Test
    fun `reaching the threshold exactly is enough`() {
        // The boundary belongs to the user: a drag that visibly reached the target and did
        // nothing would read as the phone ignoring them.
        assertEquals(SwipeOutcome.ANSWER, SwipeCommit.outcome(threshold, threshold, false))
        assertEquals(SwipeOutcome.REJECT, SwipeCommit.outcome(-threshold, threshold, false))
    }

    @Test
    fun `nothing fires twice`() {
        // Acceptance test 5. Rapid repeated touches, a bounce at the end of a drag, or a
        // recomposition arriving after the action was sent all land here.
        listOf(travel, -travel, threshold, 0f).forEach { distance ->
            assertEquals(
                "a committed gesture must stay committed",
                SwipeOutcome.NONE,
                SwipeCommit.outcome(distance, threshold, alreadyCommitted = true),
            )
        }
    }

    @Test
    fun `the threshold is over half the travel`() {
        // Far enough that the handle is unmistakably nearer the target than its resting
        // place. A threshold at or below halfway would make the midpoint ambiguous.
        assertTrue(
            "threshold $threshold must exceed half of $travel",
            SwipeCommit.thresholdPx(travel, minDistancePx = 0f) > travel / 2,
        )
    }

    @Test
    fun `a narrow screen still needs a real distance`() {
        // A percentage of a short track is a couple of millimetres, which is within what a
        // pocket produces. The floor in real units is what stops that.
        val narrow = 80f
        assertEquals(
            "the floor applies",
            100f.coerceAtMost(narrow),
            SwipeCommit.thresholdPx(narrow, minDistancePx = 100f),
            0.001f,
        )
    }

    @Test
    fun `the threshold is always reachable`() {
        // A floor larger than the track would make the phone unable to answer calls at all,
        // which is a far worse failure than the one the floor exists to prevent.
        listOf(0f, 10f, 80f, 300f, 1000f).forEach { travelPx ->
            val t = SwipeCommit.thresholdPx(travelPx, minDistancePx = 500f)
            assertTrue("threshold $t must be reachable within $travelPx", t <= travelPx)
        }
    }

    @Test
    fun `a zero-width track never commits`() {
        // Before the first layout pass there is no travel. Treating "no room to move" as a
        // completed swipe would answer calls the instant the screen appeared.
        val t = SwipeCommit.thresholdPx(travelPx = 0f, minDistancePx = 100f)
        assertEquals(SwipeOutcome.NONE, SwipeCommit.outcome(0f, t, false))
        assertEquals(SwipeOutcome.NONE, SwipeCommit.outcome(500f, t, false))
    }

    @Test
    fun `the ringing screen offers no tap to answer or reject`() {
        // The whole requirement in one assertion. A tap handler wired to answer or reject is
        // exactly what this replaced, and it is a one-line change to put back by accident.
        val source = listOf(
            File("src/main/java/com/dualshield/phone/ui/incall/InCallScreen.kt"),
            File("app/src/main/java/com/dualshield/phone/ui/incall/InCallScreen.kt"),
        ).firstOrNull { it.exists() } ?: error("InCallScreen source not found")

        val ringing = source.readText()
            .substringAfter("if (call.isRinging) {")
            .substringBefore("VerticalSpacer(40.dp)")

        assertTrue(
            "the ringing branch must hand off to the swipe control",
            ringing.contains("CallAnswerSwipe"),
        )
        listOf("onClick = onAnswer", "onClick = onReject").forEach { forbidden ->
            assertFalse(
                "a ringing call must not be answered or rejected by a tap: found '$forbidden'",
                ringing.contains(forbidden),
            )
        }
    }
}
