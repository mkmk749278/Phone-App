package com.dualshield.phone.core.incall

import kotlin.math.abs

/** What a finished drag on the incoming-call handle should do. */
enum class SwipeOutcome { ANSWER, REJECT, NONE }

/**
 * Decides whether a drag on the incoming-call handle counts.
 *
 * Pure and separate from the composable so the rules that make the gesture safe can be
 * argued with in a test rather than only on a device. What is at stake is small and
 * specific: a call answered or rejected that the user did not mean to answer or reject.
 */
object SwipeCommit {

    /** Fraction of the available travel that counts as deliberate. */
    const val COMMIT_FRACTION = 0.62f

    /**
     * The distance a drag must cover to commit.
     *
     * A fraction of the travel on most screens, but never less than [minDistancePx]: on a
     * narrow screen a percentage of a short track is a couple of millimetres, which is
     * within what a pocket can produce. Clamped to the travel itself so the threshold is
     * always reachable — a threshold the user cannot physically reach is a phone that
     * cannot answer calls.
     */
    fun thresholdPx(travelPx: Float, minDistancePx: Float): Float {
        if (travelPx <= 0f) return 0f
        return maxOf(travelPx * COMMIT_FRACTION, minDistancePx).coerceAtMost(travelPx)
    }

    /**
     * What a drag of [travelledPx] should do.
     *
     * [alreadyCommitted] is the latch that makes this fire at most once per call: a second
     * finger, a bounce at the end of the drag, or a recomposition arriving after the action
     * has been sent all resolve to [SwipeOutcome.NONE].
     *
     * A drag that ends short is not a weaker version of a drag that completed — it is
     * nothing at all, and returns [SwipeOutcome.NONE] exactly like no drag.
     */
    fun outcome(
        travelledPx: Float,
        thresholdPx: Float,
        alreadyCommitted: Boolean,
    ): SwipeOutcome = when {
        alreadyCommitted -> SwipeOutcome.NONE
        thresholdPx <= 0f -> SwipeOutcome.NONE
        abs(travelledPx) < thresholdPx -> SwipeOutcome.NONE
        travelledPx > 0f -> SwipeOutcome.ANSWER
        else -> SwipeOutcome.REJECT
    }
}
